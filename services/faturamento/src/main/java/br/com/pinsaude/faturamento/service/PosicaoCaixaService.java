package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse;
import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse.Pagina;
import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse.Recebimento;
import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse.RecebimentoMes;
import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse.Resumo;
import br.com.pinsaude.faturamento.dto.RecebimentoProducaoResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PosicaoCaixaService {

    private final JdbcTemplate jdbc;

    public PosicaoCaixaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Crédito do extrato conciliado com uma produção, já com a parte dos médicos calculada. */
    private static final String RECEBIMENTOS_CTE = """
            WITH rec AS (
                SELECT rp.id AS producao_id, rp.competencia, rp.valor_bruto,
                       COALESCE(t.nome_fantasia, t.razao_social_nome, '') AS tomador_nome,
                       le.data_lancamento AS recebido_em,
                       le.valor AS valor_recebido,
                       c.tipo_match,
                       COALESCE(part.qtd, 0) AS qtd_medicos,
                       COALESCE(part.parte_medicos,
                                rp.valor_bruto - CAST(ROUND(rp.valor_bruto * 0.15) AS BIGINT)) AS parte_medicos
                FROM faturamento.conciliacoes c
                JOIN faturamento.lancamentos_extrato le ON le.id = c.lancamento_extrato_id
                JOIN faturamento.producoes rp ON rp.id = c.nota_id
                JOIN faturamento.tomadores t ON t.id = rp.tomador_id
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS qtd,
                           SUM(pp.valor_bruto
                               - CAST(ROUND(pp.valor_bruto * COALESCE(pp.taxa_pin_pct, 0.15)) AS BIGINT)) AS parte_medicos
                    FROM faturamento.participacoes_producao pp
                    WHERE pp.producao_id = rp.id
                    HAVING COUNT(*) > 0
                ) part ON TRUE
                WHERE le.status_conciliacao = 'CONCILIADO'
                  AND le.tipo = 'CREDITO'
            )
            """;

    static final int MESES_HISTORICO = 6;
    static final int TAMANHO_MAXIMO_PAGINA = 100;

    /**
     * @param mes  YYYY-MM do depósito; nulo = todos os períodos
     * @param busca trecho do nome do tomador; nulo/vazio = todos
     */
    @Transactional(readOnly = true)
    public PosicaoCaixaResponse calcular(YearMonth mes, String busca, int page, int size) {
        int tamanho = Math.max(1, Math.min(size, TAMANHO_MAXIMO_PAGINA));
        int pagina = Math.max(0, page);

        List<Object> args = new ArrayList<>();
        String filtro = montarFiltro(mes, busca, args);

        Resumo resumo = calcularResumo(filtro, args);
        List<Recebimento> content = listarRecebimentosPagina(filtro, args, pagina, tamanho);
        int totalPages = (int) Math.ceil(resumo.quantidade() / (double) tamanho);
        Pagina paginaResp = new Pagina(content, pagina, tamanho, resumo.quantidade(), totalPages);

        long[] emAberto = calcularAReceber();

        return new PosicaoCaixaResponse(resumo, listarRecebimentosPorMes(), paginaResp,
                emAberto[0], (int) emAberto[1]);
    }

    private static String montarFiltro(YearMonth mes, String busca, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1 = 1");
        if (mes != null) {
            sb.append(" AND recebido_em >= ? AND recebido_em < ?");
            args.add(Date.valueOf(mes.atDay(1)));
            args.add(Date.valueOf(mes.plusMonths(1).atDay(1)));
        }
        if (busca != null && !busca.isBlank()) {
            sb.append(" AND tomador_nome ILIKE ?");
            args.add("%" + busca.trim() + "%");
        }
        return sb.toString();
    }

    private Resumo calcularResumo(String filtro, List<Object> args) {
        List<Resumo> linhas = jdbc.query(RECEBIMENTOS_CTE + """
                SELECT COUNT(*) AS qtd,
                       COALESCE(SUM(valor_bruto), 0)     AS valor_notas,
                       COALESCE(SUM(valor_recebido), 0)  AS recebido,
                       COALESCE(SUM(parte_medicos), 0)   AS parte_medicos,
                       COALESCE(SUM(GREATEST(valor_bruto - valor_recebido, 0)), 0) AS retencoes
                FROM rec""" + filtro,
                (rs, i) -> {
                    long recebido = rs.getLong("recebido");
                    long parteMedicos = rs.getLong("parte_medicos");
                    long repassado = 0L; // EPIC-09 pendente
                    return new Resumo(rs.getInt("qtd"), rs.getLong("valor_notas"), rs.getLong("retencoes"),
                            recebido, parteMedicos, repassado, parteMedicos - repassado, recebido - parteMedicos);
                },
                args.toArray());
        return linhas.isEmpty() ? new Resumo(0, 0, 0, 0, 0, 0, 0, 0) : linhas.get(0);
    }

    private List<Recebimento> listarRecebimentosPagina(String filtro, List<Object> args, int page, int size) {
        List<Object> argsPagina = new ArrayList<>(args);
        argsPagina.add(size);
        argsPagina.add((long) page * size);
        return jdbc.query(RECEBIMENTOS_CTE + """
                SELECT * FROM rec""" + filtro + """
                 ORDER BY recebido_em DESC, tomador_nome ASC
                 LIMIT ? OFFSET ?
                """,
                (rs, i) -> {
                    long bruto = rs.getLong("valor_bruto");
                    long recebido = rs.getLong("valor_recebido");
                    long parteMedicos = rs.getLong("parte_medicos");
                    return new Recebimento(
                            UUID.fromString(rs.getString("producao_id")),
                            rs.getString("tomador_nome"),
                            rs.getString("competencia"),
                            rs.getDate("recebido_em").toLocalDate().toString(),
                            bruto,
                            Math.max(bruto - recebido, 0),
                            recebido,
                            parteMedicos,
                            recebido - parteMedicos,
                            rs.getInt("qtd_medicos"),
                            rs.getString("tipo_match"),
                            "AGUARDANDO" // EPIC-09 pendente: nenhuma produção foi repassada ainda
                    );
                },
                argsPagina.toArray());
    }

    /** Últimos {@link #MESES_HISTORICO} meses, inclusive o atual, com zero nos meses sem depósito. */
    private List<RecebimentoMes> listarRecebimentosPorMes() {
        YearMonth atual = YearMonth.now();
        YearMonth inicio = atual.minusMonths(MESES_HISTORICO - 1L);
        Map<String, RecebimentoMes> porMes = new HashMap<>();
        jdbc.query(RECEBIMENTOS_CTE + """
                SELECT TO_CHAR(recebido_em, 'YYYY-MM') AS mes,
                       COALESCE(SUM(valor_recebido), 0) AS valor,
                       COUNT(*) AS qtd
                FROM rec
                WHERE recebido_em >= ?
                GROUP BY TO_CHAR(recebido_em, 'YYYY-MM')
                """,
                (rs, i) -> new RecebimentoMes(rs.getString("mes"), rs.getLong("valor"), rs.getInt("qtd")),
                Date.valueOf(inicio.atDay(1)))
            .forEach(m -> porMes.put(m.mes(), m));

        List<RecebimentoMes> meses = new ArrayList<>();
        for (YearMonth m = inicio; !m.isAfter(atual); m = m.plusMonths(1)) {
            meses.add(porMes.getOrDefault(m.toString(), new RecebimentoMes(m.toString(), 0L, 0)));
        }
        return meses;
    }

    /**
     * Produções conciliadas com um crédito do extrato (recebidas do tomador), com a data e o
     * valor do depósito. Usado pela tela de Notas para marcar as notas já recebidas.
     */
    @Transactional(readOnly = true)
    public List<RecebimentoProducaoResponse> listarRecebimentos() {
        return jdbc.query("""
                SELECT c.nota_id AS producao_id, le.data_lancamento, le.valor, c.tipo_match
                FROM faturamento.conciliacoes c
                JOIN faturamento.lancamentos_extrato le ON le.id = c.lancamento_extrato_id
                WHERE le.status_conciliacao = 'CONCILIADO'
                ORDER BY le.data_lancamento DESC
                """,
                (rs, i) -> new RecebimentoProducaoResponse(
                        UUID.fromString(rs.getString("producao_id")),
                        rs.getDate("data_lancamento").toLocalDate().toString(),
                        rs.getLong("valor"),
                        rs.getString("tipo_match")
                ));
    }

    /** [bruto, quantidade] das notas emitidas que ainda não têm crédito conciliado. */
    private long[] calcularAReceber() {
        List<long[]> linhas = jdbc.query("""
                SELECT COALESCE(SUM(p.valor_bruto), 0) AS valor, COUNT(*) AS qtd
                FROM faturamento.producoes p
                WHERE p.status = 'EMITIDA'
                  AND NOT EXISTS (
                      SELECT 1 FROM faturamento.conciliacoes c
                      WHERE c.nota_id = p.id
                  )
                """,
                (rs, i) -> new long[] { rs.getLong("valor"), rs.getLong("qtd") });
        return linhas.isEmpty() ? new long[] { 0L, 0L } : linhas.get(0);
    }
}
