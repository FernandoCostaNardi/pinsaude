package br.com.pinsaude.portal.service;

import br.com.pinsaude.portal.dto.DashboardResponse;
import br.com.pinsaude.portal.dto.EmpresaPortalResponse;
import br.com.pinsaude.portal.dto.ExtratoResponse;
import br.com.pinsaude.portal.dto.NotaPortalResponse;
import br.com.pinsaude.portal.dto.PerfilMedicoResponse;
import br.com.pinsaude.portal.dto.ProducaoPortalResponse;
import br.com.pinsaude.portal.dto.SetorOperacionalPortalResponse;
import br.com.pinsaude.portal.dto.TomadorPortalResponse;
import br.com.pinsaude.portal.dto.ExtratoLancamentoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class PortalService {

    private static final Logger log = LoggerFactory.getLogger(PortalService.class);

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
                LEFT JOIN faturamento.servicos s ON s.id = p.servico_id
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
                SELECT id, nome, email, crm, crm_uf, especialidade, status, taxa_pin_pct
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
                        rs.getString("status"),
                        rs.getBigDecimal("taxa_pin_pct")),
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

    // Setores Operacionais que o médico logado está autorizado a exercer neste tomador —
    // só populado quando o tomador exige controle de frequência (ver TomadorMedicosModal no
    // admin). Lista vazia quando o tomador não usa essa granularidade (a tela filtra pelo flag
    // Tomador.exigeFrequencia, que já vem no shape completo de Tomador consumido pelo Portal).
    // Query em 1 passe: LEFT JOIN setor↔modalidade (N:N, pedido do cliente — um setor pode ter
    // mais de uma modalidade) produz 1 linha por combinação setor+modalidade (ou 1 linha só, com
    // modalidade null, pra setor sem nenhuma). Agrupado em Java num único ResultSetExtractor —
    // evita uma segunda query batch (não há NamedParameterJdbcTemplate/IN aqui) e mantém a ordem
    // de s.nome vinda do banco (LinkedHashMap preserva a ordem de primeira aparição).
    public List<SetorOperacionalPortalResponse> getSetoresDoMedicoNoTomador(UUID medicoId, UUID tomadorId) {
        return jdbc.query("""
                SELECT s.id AS setor_id, s.nome AS setor_nome, s.categoria,
                       m.id AS modalidade_id, m.nome AS modalidade_nome, som.tipo AS modalidade_tipo
                FROM faturamento.medico_tomador_setores mts
                JOIN faturamento.medico_tomadores mt ON mt.id = mts.medico_tomador_id
                JOIN faturamento.tomador_servicos_operacionais s ON s.id = mts.setor_id
                LEFT JOIN faturamento.setor_operacional_modalidades som ON som.setor_id = s.id
                LEFT JOIN faturamento.tomador_modalidades m ON m.id = som.modalidade_id
                WHERE mt.medico_id = ? AND mt.tomador_id = ? AND s.ativo = true
                ORDER BY s.nome, m.nome
                """,
                (ResultSetExtractor<List<SetorOperacionalPortalResponse>>) rs -> {
                    Map<UUID, String[]> setorInfo = new LinkedHashMap<>();
                    Map<UUID, List<SetorOperacionalPortalResponse.ModalidadeResumo>> modalidadesPorSetor = new LinkedHashMap<>();
                    while (rs.next()) {
                        UUID setorId = rs.getObject("setor_id", UUID.class);
                        setorInfo.putIfAbsent(setorId, new String[]{rs.getString("setor_nome"), rs.getString("categoria")});
                        UUID modalidadeId = rs.getObject("modalidade_id", UUID.class);
                        if (modalidadeId != null) {
                            modalidadesPorSetor.computeIfAbsent(setorId, id -> new ArrayList<>())
                                .add(new SetorOperacionalPortalResponse.ModalidadeResumo(
                                        modalidadeId, rs.getString("modalidade_nome"), rs.getString("modalidade_tipo")));
                        }
                    }
                    List<SetorOperacionalPortalResponse> result = new ArrayList<>();
                    for (Map.Entry<UUID, String[]> e : setorInfo.entrySet()) {
                        result.add(new SetorOperacionalPortalResponse(
                                e.getKey(), e.getValue()[0], e.getValue()[1],
                                modalidadesPorSetor.getOrDefault(e.getKey(), List.of())));
                    }
                    return result;
                },
                medicoId, tomadorId);
    }

    /**
     * Extrato do médico: cada Produção e cada Frequência Médica que ele lançou, com o valor
     * previsto a receber (bruto − taxa Pin) e o status:
     * <ul>
     *   <li>PROVISIONADO — só lançado pelo médico (ou já fechado, mas sem NFS-e emitida);</li>
     *   <li>FATURADO — a NFS-e da produção (ou do fechamento da frequência) foi emitida;</li>
     *   <li>PAGO — já faturado e existe repasse registrado no ledger para o médico naquela
     *       competência.</li>
     * </ul>
     * Produções geradas pelo Fechamento por Grupo não aparecem separadas: são representadas
     * pelas frequências que as originaram (evita contar o mesmo valor duas vezes).
     */
    public ExtratoResponse getExtrato(UUID medicoId, String competencia) {
        Set<String> competenciasPagas = competenciasComRepasse(medicoId);

        List<ExtratoLancamentoResponse> todos = new ArrayList<>();
        todos.addAll(lancamentosDeProducao(medicoId, competenciasPagas));
        todos.addAll(lancamentosDeFrequencia(medicoId, competenciasPagas));
        todos.sort(Comparator.comparing(ExtratoLancamentoResponse::competencia).reversed()
                .thenComparing(ExtratoLancamentoResponse::dataRef,
                        Comparator.nullsLast(Comparator.reverseOrder())));

        List<String> competencias = todos.stream()
                .map(ExtratoLancamentoResponse::competencia)
                .distinct()
                .toList();

        String filtro = competencia != null && !competencia.isBlank() ? competencia : null;
        List<ExtratoLancamentoResponse> lancamentos = filtro == null ? todos
                : todos.stream().filter(l -> filtro.equals(l.competencia())).toList();

        long previsto = 0, provisionado = 0, faturado = 0, pago = 0;
        for (ExtratoLancamentoResponse l : lancamentos) {
            previsto += l.valorPrevisto();
            switch (l.status()) {
                case STATUS_PAGO -> pago += l.valorPrevisto();
                case STATUS_FATURADO -> faturado += l.valorPrevisto();
                default -> provisionado += l.valorPrevisto();
            }
        }
        return new ExtratoResponse(filtro, previsto, provisionado, faturado, pago, competencias, lancamentos);
    }

    static final String STATUS_PROVISIONADO = "PROVISIONADO";
    static final String STATUS_FATURADO     = "FATURADO";
    static final String STATUS_PAGO         = "PAGO";

    private List<ExtratoLancamentoResponse> lancamentosDeProducao(UUID medicoId, Set<String> competenciasPagas) {
        return jdbc.query("""
                SELECT p.id, p.competencia, p.created_at,
                       t.razao_social_nome AS tomador_nome,
                       s.descricao_padrao AS servico_descricao,
                       pp.valor_bruto,
                       COALESCE(pp.taxa_pin_pct, 0.15) AS taxa_pin_pct,
                       nf.numero_nota, (nf.id IS NOT NULL) AS faturado
                FROM faturamento.participacoes_producao pp
                JOIN faturamento.producoes p ON p.id = pp.producao_id
                JOIN faturamento.tomadores t ON t.id = p.tomador_id
                LEFT JOIN faturamento.servicos s ON s.id = p.servico_id
                LEFT JOIN LATERAL (
                    SELECT n.id, n.numero_nota
                    FROM fiscal.notas_fiscais n
                    WHERE n.producao_id = p.id AND n.status = 'EMITIDA'
                    ORDER BY n.emitida_at DESC NULLS LAST
                    LIMIT 1
                ) nf ON TRUE
                WHERE pp.medico_id = ?
                  AND p.status <> 'CANCELADA'
                  AND NOT EXISTS (
                      SELECT 1 FROM faturamento.frequencias_medicas fm
                      WHERE fm.producao_id = p.id AND fm.medico_id = pp.medico_id
                  )
                """,
                (rs, row) -> {
                    String comp = rs.getString("competencia");
                    long bruto = rs.getLong("valor_bruto");
                    long taxa = calcularTaxaPin(bruto, rs.getBigDecimal("taxa_pin_pct"));
                    return new ExtratoLancamentoResponse(
                            rs.getObject("id", UUID.class),
                            "PRODUCAO",
                            comp,
                            rs.getString("tomador_nome"),
                            rs.getString("servico_descricao"),
                            1,
                            bruto,
                            taxa,
                            bruto - taxa,
                            resolverStatus(rs.getBoolean("faturado"), comp, competenciasPagas),
                            rs.getString("numero_nota"),
                            toOffsetDateTime(rs.getTimestamp("created_at")));
                },
                medicoId);
    }

    /**
     * Valor bruto de cada frequência calculado com as mesmas regras do faturamento
     * (FrequenciaMedicaResponse.from): soma dos itens (valor + deslocamento + ocorrência por
     * item) + valor mensal da modalidade fixa (Diarista/Evolucionista) + ocorrência fixa da
     * frequência, aplicada uma única vez. Frequências legadas sem modalidade fixa resolvem o
     * valor mensal pelas modalidades fixas usadas nos itens.
     */
    private List<ExtratoLancamentoResponse> lancamentosDeFrequencia(UUID medicoId, Set<String> competenciasPagas) {
        return jdbc.query("""
                SELECT fm.id, fm.competencia, fm.created_at,
                       t.razao_social_nome AS tomador_nome,
                       so.nome AS setor_nome,
                       itens.qtd,
                       itens.total AS total_itens,
                       CASE
                         WHEN fm.modalidade_id IS NOT NULL THEN
                             (CASE WHEN m.tipos[1] IN ('DIARISTA', 'EVOLUCIONISTA') THEN m.valor_centavos ELSE 0 END)
                             + (CASE WHEN o.id IS NOT NULL
                                     THEN COALESCE(ROUND(m.valor_centavos * o.valor_percentual / 100), 0)
                                          + COALESCE(o.valor_centavos, 0)
                                     ELSE 0 END)
                         ELSE COALESCE((
                             SELECT SUM(m2.valor_centavos)
                             FROM faturamento.tomador_modalidades m2
                             WHERE m2.tipos[1] IN ('DIARISTA', 'EVOLUCIONISTA')
                               AND m2.id IN (SELECT i2.modalidade_id FROM faturamento.frequencia_itens i2
                                             WHERE i2.frequencia_id = fm.id)
                         ), 0)
                       END AS valor_fixo,
                       COALESCE(pp.taxa_pin_pct, med.taxa_pin_pct, 0.15) AS taxa_pin_pct,
                       nf.numero_nota, (nf.id IS NOT NULL) AS faturado
                FROM faturamento.frequencias_medicas fm
                JOIN faturamento.tomadores t ON t.id = fm.tomador_id
                LEFT JOIN faturamento.tomador_servicos_operacionais so ON so.id = fm.servico_operacional_id
                LEFT JOIN faturamento.tomador_modalidades m ON m.id = fm.modalidade_id
                LEFT JOIN faturamento.tomador_ocorrencias o ON o.id = fm.ocorrencia_id
                LEFT JOIN onboarding.medicos med ON med.id = fm.medico_id
                LEFT JOIN faturamento.participacoes_producao pp
                       ON pp.producao_id = fm.producao_id AND pp.medico_id = fm.medico_id
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS qtd,
                           COALESCE(SUM(i.valor_unitario_centavos + i.deslocamento_centavos
                                        + COALESCE(i.ocorrencia_valor_centavos, 0)), 0) AS total
                    FROM faturamento.frequencia_itens i
                    WHERE i.frequencia_id = fm.id
                ) itens ON TRUE
                LEFT JOIN LATERAL (
                    SELECT n.id, n.numero_nota
                    FROM fiscal.notas_fiscais n
                    WHERE fm.producao_id IS NOT NULL
                      AND n.producao_id = fm.producao_id AND n.status = 'EMITIDA'
                    ORDER BY n.emitida_at DESC NULLS LAST
                    LIMIT 1
                ) nf ON TRUE
                WHERE fm.medico_id = ?
                """,
                (rs, row) -> {
                    String comp = rs.getString("competencia");
                    int qtd = rs.getInt("qtd");
                    long bruto = rs.getLong("total_itens") + rs.getLong("valor_fixo");
                    if (qtd == 0 && bruto == 0) return null; // frequência aberta sem nada lançado
                    long taxa = calcularTaxaPin(bruto, rs.getBigDecimal("taxa_pin_pct"));
                    return new ExtratoLancamentoResponse(
                            rs.getObject("id", UUID.class),
                            "FREQUENCIA",
                            comp,
                            rs.getString("tomador_nome"),
                            rs.getString("setor_nome"),
                            qtd,
                            bruto,
                            taxa,
                            bruto - taxa,
                            resolverStatus(rs.getBoolean("faturado"), comp, competenciasPagas),
                            rs.getString("numero_nota"),
                            toOffsetDateTime(rs.getTimestamp("created_at")));
                },
                medicoId).stream().filter(Objects::nonNull).toList();
    }

    /**
     * Competências em que o médico já recebeu repasse — lançamentos REPASSE no ledger
     * (gerados pelo evento ledger.repasse.efetuado). Tolerante a falha: se o portal ainda não
     * tiver acesso de leitura ao schema ledger, o extrato continua funcionando, só sem "Pago".
     */
    private Set<String> competenciasComRepasse(UUID medicoId) {
        try {
            return new HashSet<>(jdbc.query("""
                    SELECT DISTINCT competencia
                    FROM ledger.lancamentos_ledger
                    WHERE medico_id = ? AND tipo_origem = 'REPASSE'
                    """,
                    (rs, row) -> rs.getString("competencia"),
                    medicoId));
        } catch (DataAccessException e) {
            log.warn("Extrato: não foi possível consultar repasses no ledger — {}", e.getMessage());
            return Set.of();
        }
    }

    static String resolverStatus(boolean faturado, String competencia, Set<String> competenciasPagas) {
        if (!faturado) return STATUS_PROVISIONADO;
        return competenciasPagas.contains(competencia) ? STATUS_PAGO : STATUS_FATURADO;
    }

    static long calcularTaxaPin(long bruto, BigDecimal pct) {
        BigDecimal fator = pct != null ? pct : new BigDecimal("0.15");
        return BigDecimal.valueOf(bruto).multiply(fator).setScale(0, RoundingMode.HALF_UP).longValue();
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
