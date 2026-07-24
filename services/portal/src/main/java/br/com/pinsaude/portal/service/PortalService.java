package br.com.pinsaude.portal.service;

import br.com.pinsaude.portal.dto.DashboardResponse;
import br.com.pinsaude.portal.dto.EmpresaPortalResponse;
import br.com.pinsaude.portal.dto.ExtratoResponse;
import br.com.pinsaude.portal.dto.NotaPortalResponse;
import br.com.pinsaude.portal.dto.PerfilMedicoResponse;
import br.com.pinsaude.portal.dto.ProducaoPortalResponse;
import br.com.pinsaude.portal.dto.TomadorPortalResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class PortalService {

    private final JdbcTemplate jdbc;

    public PortalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID resolveMedicoId(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "JWT não contém claim 'email'. Configure o mapper no Keycloak.");
        }
        List<UUID> ids = jdbc.query(
                "SELECT id FROM onboarding.medicos WHERE email = ?",
                (rs, row) -> rs.getObject("id", UUID.class),
                email);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Médico não encontrado para o e-mail: " + email);
        }
        return ids.get(0);
    }

    public DashboardResponse getDashboard(UUID medicoId) {
        long saldoDisponivel = somarLiquidoPorStatus(medicoId, "EMITIDA");
        long valorAReceber   = somarLiquidoPorStatusIn(medicoId, "PENDENTE", "PROCESSANDO", "AGUARDANDO_VALIDACAO");
        long totalEmitidas   = contarNotasPorStatus(medicoId, "EMITIDA");

        // Total produzido: soma do valor_bruto de participacoes do médico (inclui old e new)
        long totalProduzido = jdbc.query("""
                SELECT COALESCE(SUM(pp.valor_bruto), 0)
                FROM faturamento.participacoes_producao pp
                WHERE pp.medico_id = ?
                """,
                (rs, row) -> rs.getLong(1),
                medicoId).stream().findFirst().orElse(0L);

        long totalProducoes = jdbc.query("""
                SELECT COUNT(*)
                FROM faturamento.participacoes_producao pp
                WHERE pp.medico_id = ?
                """,
                (rs, row) -> rs.getLong(1),
                medicoId).stream().findFirst().orElse(0L);

        List<NotaPortalResponse> ultimasNotas = jdbc.query(
                notasSql() + " ORDER BY nf.created_at DESC LIMIT 5",
                (rs, row) -> mapNota(rs),
                medicoId, medicoId);

        return new DashboardResponse(saldoDisponivel, valorAReceber, totalProduzido,
                totalEmitidas, totalProducoes, ultimasNotas, Collections.emptyList());
    }

    public List<NotaPortalResponse> getNotas(UUID medicoId, String competencia, String status) {
        StringBuilder sql = new StringBuilder(notasSql());

        List<Object> params = new java.util.ArrayList<>();
        params.add(medicoId);  // pp.medico_id no LEFT JOIN
        params.add(medicoId);  // WHERE nf.medico_id = ?

        if (competencia != null && !competencia.isBlank()) {
            sql.append(" AND nf.competencia = ?");
            params.add(competencia);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND nf.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY nf.created_at DESC");

        return jdbc.query(sql.toString(), (rs, row) -> mapNota(rs), params.toArray());
    }

    public List<ProducaoPortalResponse> getProducoes(UUID medicoId, String competencia) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.competencia, t.razao_social_nome AS tomador_nome,
                       s.descricao_padrao AS servico_descricao,
                       pp.valor_bruto AS valor_bruto_medico,
                       COALESCE(pp.taxa_pin_pct, 0.15) AS taxa_pin_pct,
                       p.status, p.created_at
                FROM faturamento.participacoes_producao pp
                JOIN faturamento.producoes p ON p.id = pp.producao_id
                JOIN faturamento.tomadores t ON t.id = p.tomador_id
                JOIN faturamento.servicos  s ON s.id = p.servico_id
                WHERE pp.medico_id = ?
                """);

        List<Object> params = new java.util.ArrayList<>();
        params.add(medicoId);

        if (competencia != null && !competencia.isBlank()) {
            sql.append(" AND p.competencia = ?");
            params.add(competencia);
        }
        sql.append(" ORDER BY p.created_at DESC");

        return jdbc.query(sql.toString(), (rs, row) -> {
            long bruto = rs.getLong("valor_bruto_medico");
            double taxaPinPct = rs.getDouble("taxa_pin_pct");
            long taxaPin = Math.round(bruto * taxaPinPct);
            return new ProducaoPortalResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("competencia"),
                    rs.getString("tomador_nome"),
                    rs.getString("servico_descricao"),
                    bruto,
                    bruto - taxaPin,
                    rs.getString("status"),
                    toOffsetDateTime(rs.getTimestamp("created_at"))
            );
        }, params.toArray());
    }

    public PerfilMedicoResponse getPerfil(UUID medicoId) {
        return jdbc.query("""
                SELECT id, nome, email, crm, crm_uf, especialidade, status
                FROM onboarding.medicos
                WHERE id = ?
                """,
                (rs, row) -> new PerfilMedicoResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("nome"),
                        rs.getString("email"),
                        rs.getString("crm"),
                        rs.getString("crm_uf"),
                        rs.getString("especialidade"),
                        rs.getString("status")),
                medicoId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Médico não encontrado"));
    }

    public List<EmpresaPortalResponse> getEmpresasDoMedico(UUID medicoId) {
        return jdbc.query("""
                SELECT e.id, e.razao_social, e.cnpj, e.municipio, e.inscricao_municipal
                FROM onboarding.vinculos_medico_empresa v
                JOIN onboarding.empresas e ON e.id = v.empresa_id
                WHERE v.medico_id = ?
                  AND e.ativo = true
                ORDER BY e.razao_social
                """,
                (rs, row) -> new EmpresaPortalResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("razao_social"),
                        rs.getString("cnpj"),
                        rs.getString("municipio"),
                        rs.getString("inscricao_municipal")),
                medicoId);
    }

    public List<TomadorPortalResponse> getTomadoresDoMedico(UUID medicoId) {
        return jdbc.query("""
                SELECT t.id, t.razao_social_nome, t.municipio
                FROM faturamento.medico_tomadores mt
                JOIN faturamento.tomadores t ON t.id = mt.tomador_id
                WHERE mt.medico_id = ?
                ORDER BY t.razao_social_nome
                """,
                (rs, row) -> new TomadorPortalResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("razao_social_nome"),
                        rs.getString("municipio")),
                medicoId);
    }

    public ExtratoResponse getExtrato(UUID medicoId, LocalDate dtInicio, LocalDate dtFim) {
        // Repasses efetivos serão consultados aqui quando EPIC-09 for implementado.
        // Apenas transferências liquidadas para a conta do médico devem aparecer.
        return new ExtratoResponse(0L, 0L, 0L, 0L, 0L, List.of());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    /**
     * SQL base para consulta de notas do médico.
     * Suporta notas antigas (nf.medico_id = ?) e novas multi-médico (via participacoes).
     * Valores per-doctor para notas multi-médico vêm da tabela participacoes_producao.
     */
    private String notasSql() {
        return """
                SELECT nf.id, nf.producao_id, nf.competencia, nf.tomador_nome,
                       CASE WHEN nf.medico_id IS NOT NULL THEN nf.valor_bruto
                            ELSE pp.valor_bruto END AS valor_bruto,
                       CASE WHEN nf.medico_id IS NOT NULL THEN nf.valor_liquido_medico
                            ELSE CAST(ROUND(pp.valor_bruto * COALESCE(pp.taxa_pin_pct, 0.15)) AS BIGINT)
                       END AS taxa_pin,
                       CASE WHEN nf.medico_id IS NOT NULL THEN nf.valor_liquido_medico
                            ELSE pp.valor_bruto - CAST(ROUND(pp.valor_bruto * COALESCE(pp.taxa_pin_pct, 0.15)) AS BIGINT)
                       END AS valor_liquido_medico,
                       nf.valor_iss, nf.valor_ir, nf.valor_csll, nf.valor_pis, nf.valor_cofins,
                       nf.status, nf.numero_nota,
                       (nf.xml_nota IS NOT NULL) AS tem_xml,
                       (nf.pdf_nota IS NOT NULL) AS tem_pdf,
                       nf.protocolo_emissao,
                       nf.emitida_at, nf.created_at
                FROM fiscal.notas_fiscais nf
                LEFT JOIN faturamento.participacoes_producao pp
                    ON pp.producao_id = nf.producao_id AND pp.medico_id = ?
                WHERE (nf.medico_id = ? OR pp.medico_id IS NOT NULL)
                """;
    }

    private long somarLiquidoPorStatus(UUID medicoId, String status) {
        return jdbc.query("""
                SELECT COALESCE(SUM(
                    CASE WHEN nf.medico_id IS NOT NULL THEN nf.valor_liquido_medico
                         ELSE pp.valor_bruto - CAST(ROUND(pp.valor_bruto * COALESCE(pp.taxa_pin_pct, 0.15)) AS BIGINT) END
                ), 0)
                FROM fiscal.notas_fiscais nf
                LEFT JOIN faturamento.participacoes_producao pp
                    ON pp.producao_id = nf.producao_id AND pp.medico_id = ?
                WHERE (nf.medico_id = ? OR pp.medico_id IS NOT NULL)
                  AND nf.status = ?
                """,
                (rs, row) -> rs.getLong(1),
                medicoId, medicoId, status).stream().findFirst().orElse(0L);
    }

    private long somarLiquidoPorStatusIn(UUID medicoId, String... statuses) {
        String placeholders = String.join(",", Collections.nCopies(statuses.length, "?"));
        Object[] params = new Object[statuses.length + 2];
        params[0] = medicoId;  // LEFT JOIN pp.medico_id
        params[1] = medicoId;  // WHERE nf.medico_id
        System.arraycopy(statuses, 0, params, 2, statuses.length);
        return jdbc.query("""
                SELECT COALESCE(SUM(
                    CASE WHEN nf.medico_id IS NOT NULL THEN nf.valor_liquido_medico
                         ELSE pp.valor_bruto - CAST(ROUND(pp.valor_bruto * COALESCE(pp.taxa_pin_pct, 0.15)) AS BIGINT) END
                ), 0)
                FROM fiscal.notas_fiscais nf
                LEFT JOIN faturamento.participacoes_producao pp
                    ON pp.producao_id = nf.producao_id AND pp.medico_id = ?
                WHERE (nf.medico_id = ? OR pp.medico_id IS NOT NULL)
                  AND nf.status IN (""" + placeholders + ")",
                (rs, row) -> rs.getLong(1),
                params).stream().findFirst().orElse(0L);
    }

    private long contarNotasPorStatus(UUID medicoId, String status) {
        return jdbc.query("""
                SELECT COUNT(*)
                FROM fiscal.notas_fiscais nf
                LEFT JOIN faturamento.participacoes_producao pp
                    ON pp.producao_id = nf.producao_id AND pp.medico_id = ?
                WHERE (nf.medico_id = ? OR pp.medico_id IS NOT NULL)
                  AND nf.status = ?
                """,
                (rs, row) -> rs.getLong(1),
                medicoId, medicoId, status).stream().findFirst().orElse(0L);
    }

    private NotaPortalResponse mapNota(ResultSet rs) throws SQLException {
        return new NotaPortalResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("producao_id", UUID.class),
                rs.getString("competencia"),
                rs.getString("tomador_nome"),
                rs.getLong("valor_bruto"),
                rs.getLong("valor_liquido_medico"),
                rs.getLong("taxa_pin"),
                rs.getLong("valor_iss"),
                rs.getLong("valor_ir"),
                rs.getLong("valor_csll"),
                rs.getLong("valor_pis"),
                rs.getLong("valor_cofins"),
                rs.getString("status"),
                rs.getString("numero_nota"),
                rs.getBoolean("tem_xml"),
                rs.getBoolean("tem_pdf"),
                rs.getString("protocolo_emissao"),
                toOffsetDateTime(rs.getTimestamp("emitida_at")),
                toOffsetDateTime(rs.getTimestamp("created_at"))
        );
    }

    public String getNotaXml(UUID medicoId, UUID notaId) {
        List<String> result = jdbc.query("""
                SELECT nf.xml_nota
                FROM fiscal.notas_fiscais nf
                LEFT JOIN faturamento.participacoes_producao pp
                    ON pp.producao_id = nf.producao_id AND pp.medico_id = ?
                WHERE nf.id = ?
                  AND (nf.medico_id = ? OR pp.medico_id IS NOT NULL)
                """,
                (rs, row) -> rs.getString("xml_nota"),
                medicoId, notaId, medicoId);
        return result.isEmpty() ? null : result.get(0);
    }

    public byte[] getNotaPdf(UUID medicoId, UUID notaId) {
        List<byte[]> result = jdbc.query("""
                SELECT nf.pdf_nota
                FROM fiscal.notas_fiscais nf
                LEFT JOIN faturamento.participacoes_producao pp
                    ON pp.producao_id = nf.producao_id AND pp.medico_id = ?
                WHERE nf.id = ?
                  AND (nf.medico_id = ? OR pp.medico_id IS NOT NULL)
                """,
                (rs, row) -> rs.getBytes("pdf_nota"),
                medicoId, notaId, medicoId);
        return result.isEmpty() ? null : result.get(0);
    }

    private OffsetDateTime toOffsetDateTime(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
