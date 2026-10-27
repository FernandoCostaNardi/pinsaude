package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse;
import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse.NotaEmAberto;
import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse.RecebidaAguardandoRepasse;
import br.com.pinsaude.faturamento.dto.RecebimentoProducaoResponse;
import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse.RecebimentoSemana;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class PosicaoCaixaService {

    private final JdbcTemplate jdbc;

    public PosicaoCaixaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PosicaoCaixaResponse calcular() {

        long aReceber = calcularAReceber();
        long recebido = calcularRecebidoNaoRepassado();
        long repassado = 0L; // EPIC-09 pendente
        long saldo = recebido - repassado;

        List<NotaEmAberto> emAberto = listarNotasEmAberto();
        List<RecebimentoSemana> grafico = listarRecebimentosPorSemana();
        List<RecebidaAguardandoRepasse> aguardando = listarRecebidasAguardandoRepasse();
        // Enquanto o repasse (EPIC-09) não existir, toda produção recebida está aguardando repasse.
        long aRepassar = aguardando.stream().mapToLong(RecebidaAguardandoRepasse::aRepassar).sum();

        return new PosicaoCaixaResponse(aReceber, recebido, aRepassar, repassado, saldo,
                emAberto, aguardando, grafico);
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

    private long calcularAReceber() {
        Long val = jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.valor_bruto), 0)
                FROM faturamento.producoes p
                WHERE p.status = 'EMITIDA'
                  AND NOT EXISTS (
                      SELECT 1 FROM faturamento.conciliacoes c
                      WHERE c.nota_id = p.id
                  )
                """, Long.class);
        return val != null ? val : 0L;
    }

    private long calcularRecebidoNaoRepassado() {
        Long val = jdbc.queryForObject("""
                SELECT COALESCE(SUM(le.valor), 0)
                FROM faturamento.lancamentos_extrato le
                WHERE le.status_conciliacao = 'CONCILIADO'
                  AND le.tipo = 'CREDITO'
                """, Long.class);
        return val != null ? val : 0L;
    }

    private List<NotaEmAberto> listarNotasEmAberto() {
        LocalDate hoje = LocalDate.now();
        return jdbc.query("""
                SELECT p.id, p.medico_id, p.valor_bruto, p.created_at::date AS data_ref,
                       COALESCE(t.nome_fantasia, t.razao_social_nome, '') AS tomador_nome
                FROM faturamento.producoes p
                JOIN faturamento.tomadores t ON t.id = p.tomador_id
                WHERE p.status = 'EMITIDA'
                  AND NOT EXISTS (
                      SELECT 1 FROM faturamento.conciliacoes c
                      WHERE c.nota_id = p.id
                  )
                ORDER BY p.created_at ASC
                LIMIT 100
                """,
                (rs, i) -> {
                    LocalDate dataRef = rs.getDate("data_ref").toLocalDate();
                    int dias = (int) ChronoUnit.DAYS.between(dataRef, hoje);
                    String medicoRaw = rs.getString("medico_id");
                    return new NotaEmAberto(
                            UUID.fromString(rs.getString("id")),
                            medicoRaw != null ? UUID.fromString(medicoRaw) : null,
                            rs.getString("tomador_nome"),
                            rs.getLong("valor_bruto"),
                            dataRef.toString(),
                            dias
                    );
                });
    }

    /**
     * Produções recebidas (conciliadas) e ainda não repassadas, com a parte dos médicos:
     * soma, por participação, de bruto − taxa Pin (mesma regra do extrato do portal). Produção
     * sem participações (legado de um médico só) usa o bruto da produção e a taxa padrão de 15%.
     */
    private List<RecebidaAguardandoRepasse> listarRecebidasAguardandoRepasse() {
        return jdbc.query("""
                SELECT rp.id, rp.competencia, rp.valor_bruto,
                       COALESCE(t.nome_fantasia, t.razao_social_nome, '') AS tomador_nome,
                       le.data_lancamento AS recebido_em,
                       le.valor AS valor_recebido,
                       COALESCE(part.qtd, 0) AS qtd_medicos,
                       COALESCE(part.a_repassar,
                                rp.valor_bruto - CAST(ROUND(rp.valor_bruto * 0.15) AS BIGINT)) AS a_repassar
                FROM faturamento.conciliacoes c
                JOIN faturamento.lancamentos_extrato le ON le.id = c.lancamento_extrato_id
                JOIN faturamento.producoes rp ON rp.id = c.nota_id
                JOIN faturamento.tomadores t ON t.id = rp.tomador_id
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS qtd,
                           SUM(pp.valor_bruto
                               - CAST(ROUND(pp.valor_bruto * COALESCE(pp.taxa_pin_pct, 0.15)) AS BIGINT)) AS a_repassar
                    FROM faturamento.participacoes_producao pp
                    WHERE pp.producao_id = rp.id
                    HAVING COUNT(*) > 0
                ) part ON TRUE
                WHERE le.status_conciliacao = 'CONCILIADO'
                ORDER BY le.data_lancamento ASC
                LIMIT 100
                """,
                (rs, i) -> new RecebidaAguardandoRepasse(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("tomador_nome"),
                        rs.getString("competencia"),
                        rs.getDate("recebido_em").toLocalDate().toString(),
                        rs.getLong("valor_bruto"),
                        rs.getLong("valor_recebido"),
                        rs.getLong("a_repassar"),
                        rs.getInt("qtd_medicos")
                ));
    }

    private List<RecebimentoSemana> listarRecebimentosPorSemana() {
        return jdbc.query("""
                SELECT
                    TO_CHAR(DATE_TRUNC('week', le.data_lancamento), 'IYYY-"W"IW') AS semana_key,
                    TO_CHAR(DATE_TRUNC('week', le.data_lancamento), 'DD/MM')       AS semana_label,
                    COALESCE(SUM(le.valor), 0)                                     AS valor
                FROM faturamento.lancamentos_extrato le
                WHERE le.status_conciliacao = 'CONCILIADO'
                  AND le.tipo = 'CREDITO'
                  AND le.data_lancamento >= CURRENT_DATE - INTERVAL '3 months'
                GROUP BY DATE_TRUNC('week', le.data_lancamento)
                ORDER BY DATE_TRUNC('week', le.data_lancamento) ASC
                """,
                (rs, i) -> new RecebimentoSemana(
                        rs.getString("semana_key"),
                        rs.getString("semana_label"),
                        rs.getLong("valor")
                ));
    }
}
