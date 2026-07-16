package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse;
import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse.NotaEmAberto;
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

        return new PosicaoCaixaResponse(aReceber, recebido, repassado, saldo, emAberto, grafico);
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
