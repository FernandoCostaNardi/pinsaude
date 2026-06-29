package br.com.pinsaude.portal.service;

import br.com.pinsaude.portal.dto.DashboardResponse;
import br.com.pinsaude.portal.dto.EmpresaPortalResponse;
import br.com.pinsaude.portal.dto.ExtratoLancamentoResponse;
import br.com.pinsaude.portal.dto.ExtratoResponse;
import br.com.pinsaude.portal.dto.NotaPortalResponse;
import br.com.pinsaude.portal.dto.PerfilMedicoResponse;
import br.com.pinsaude.portal.dto.ProducaoPortalResponse;
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
import java.util.ArrayList;
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
                SELECT p.id, p.competencia, t.razao_social AS tomador_nome,
                       s.descricao_padrao AS servico_descricao,
                       pp.valor_bruto AS valor_bruto_medico, p.status, p.created_at
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
            return new ProducaoPortalResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("competencia"),
                    rs.getString("tomador_nome"),
                    rs.getString("servico_descricao"),
                    bruto,
                    Math.round(bruto * 0.85),
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

    public ExtratoResponse getExtrato(UUID medicoId, LocalDate dtInicio, LocalDate dtFim) {
        StringBuilder sql = new StringBuilder(notasSql());
        List<Object> params = new ArrayList<>();
        params.add(medicoId);
        params.add(medicoId);

        sql.append(" AND nf.status = 'EMITIDA'");
        if (dtInicio != null) {
            sql.append(" AND DATE(COALESCE(nf.emitida_at, nf.created_at)) >= ?");
            params.add(dtInicio);
        }
        if (dtFim != null) {
            sql.append(" AND DATE(COALESCE(nf.emitida_at, nf.created_at)) <= ?");
            params.add(dtFim);
        }
        sql.append(" ORDER BY COALESCE(nf.emitida_at, nf.created_at) ASC");

        List<NotaPortalResponse> notas = jdbc.query(
                sql.toString(), (rs, row) -> mapNota(rs), params.toArray());

        List<ExtratoLancamentoResponse> lancamentos = new ArrayList<>();
        long saldo = 0;
        long totalCreditos = 0;
        long totalRetencoes = 0;
        long totalTaxaPin = 0;

        for (NotaPortalResponse nota : notas) {
            String dataRef = nota.emitidaAt() != null
                    ? nota.emitidaAt().toString()
                    : nota.createdAt().toString();
            String ref = nota.numeroNota() != null
                    ? nota.numeroNota()
                    : nota.id().toString().substring(0, 8).toUpperCase();
            String tom = nota.tomadorNome() != null ? " — " + nota.tomadorNome() : "";

            // Crédito: valor bruto da nota
            saldo += nota.valorBrutoCentavos();
            totalCreditos += nota.valorBrutoCentavos();
            lancamentos.add(new ExtratoLancamentoResponse(
                    "CREDITO", "NFS_E",
                    "NFS-e nº " + ref + tom,
                    nota.valorBrutoCentavos(), saldo,
                    nota.competencia(), ref, dataRef));

            // Débitos: cada tributo, somente quando > 0
            if (nota.valorIss() > 0) {
                saldo -= nota.valorIss();
                totalRetencoes += nota.valorIss();
                lancamentos.add(new ExtratoLancamentoResponse(
                        "DEBITO", "ISS", "ISS retido — NFS-e nº " + ref,
                        nota.valorIss(), saldo, nota.competencia(), ref, dataRef));
            }
            if (nota.valorIr() > 0) {
                saldo -= nota.valorIr();
                totalRetencoes += nota.valorIr();
                lancamentos.add(new ExtratoLancamentoResponse(
                        "DEBITO", "IR", "IR retido na fonte — NFS-e nº " + ref,
                        nota.valorIr(), saldo, nota.competencia(), ref, dataRef));
            }
            if (nota.valorCsll() > 0) {
                saldo -= nota.valorCsll();
                totalRetencoes += nota.valorCsll();
                lancamentos.add(new ExtratoLancamentoResponse(
                        "DEBITO", "CSLL", "CSLL retida — NFS-e nº " + ref,
                        nota.valorCsll(), saldo, nota.competencia(), ref, dataRef));
            }
            if (nota.valorPis() > 0) {
                saldo -= nota.valorPis();
                totalRetencoes += nota.valorPis();
                lancamentos.add(new ExtratoLancamentoResponse(
                        "DEBITO", "PIS", "PIS retido — NFS-e nº " + ref,
                        nota.valorPis(), saldo, nota.competencia(), ref, dataRef));
            }
            if (nota.valorCofins() > 0) {
                saldo -= nota.valorCofins();
                totalRetencoes += nota.valorCofins();
                lancamentos.add(new ExtratoLancamentoResponse(
                        "DEBITO", "COFINS", "COFINS retida — NFS-e nº " + ref,
                        nota.valorCofins(), saldo, nota.competencia(), ref, dataRef));
            }

            // Débito: taxa Pin Saúde (15%)
            saldo -= nota.taxaPinCentavos();
            totalTaxaPin += nota.taxaPinCentavos();
            lancamentos.add(new ExtratoLancamentoResponse(
                    "DEBITO", "TAXA_PIN", "Taxa Pin Saúde (15%) — NFS-e nº " + ref,
                    nota.taxaPinCentavos(), saldo, nota.competencia(), ref, dataRef));
        }

        // Retorna mais recentes primeiro (leitura natural de extrato)
        Collections.reverse(lancamentos);

        long totalDebitos = totalRetencoes + totalTaxaPin;
        return new ExtratoResponse(
                saldo, totalCreditos, totalDebitos, totalRetencoes, totalTaxaPin, lancamentos);
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
                            ELSE pp.valor_bruto - CAST(ROUND(pp.valor_bruto * 0.15) AS BIGINT)
                       END AS valor_liquido_medico,
                       CASE WHEN nf.medico_id IS NOT NULL THEN nf.taxa_pin
                            ELSE CAST(ROUND(pp.valor_bruto * 0.15) AS BIGINT)
                       END AS taxa_pin,
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
                         ELSE pp.valor_bruto - CAST(ROUND(pp.valor_bruto * 0.15) AS BIGINT) END
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
                         ELSE pp.valor_bruto - CAST(ROUND(pp.valor_bruto * 0.15) AS BIGINT) END
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
