package br.com.pinsaude.faturamento.conciliacao;

import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse;
import br.com.pinsaude.faturamento.dto.RecebimentoProducaoResponse;
import br.com.pinsaude.faturamento.service.PosicaoCaixaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosicaoCaixaServiceTest {

    @Mock
    JdbcTemplate jdbc;

    PosicaoCaixaService service;

    final List<String> sqls = new ArrayList<>();
    final List<Object[]> argsPorSql = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PosicaoCaixaService(jdbc);
    }

    /** ResultSet falso a partir de um mapa coluna → valor. */
    private static ResultSet rs(Map<String, Object> cols) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        lenient().when(rs.getLong(anyString())).thenAnswer(i -> ((Number) cols.getOrDefault(i.getArgument(0), 0L)).longValue());
        lenient().when(rs.getInt(anyString())).thenAnswer(i -> ((Number) cols.getOrDefault(i.getArgument(0), 0)).intValue());
        lenient().when(rs.getString(anyString())).thenAnswer(i -> (String) cols.get(i.getArgument(0)));
        lenient().when(rs.getDate(anyString())).thenAnswer(i -> (Date) cols.get(i.getArgument(0)));
        return rs;
    }

    /**
     * Stub de jdbc.query: o resumo, a página, o histórico mensal e o "a receber" respondem cada um
     * com as linhas dadas (rodando o RowMapper real do service). Guarda SQL e argumentos.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stub(List<Map<String, Object>> resumo, List<Map<String, Object>> pagina,
                      List<Map<String, Object>> meses, List<Map<String, Object>> aReceber) {
        lenient().when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            RowMapper mapper = inv.getArgument(1);
            Object[] args = inv.getRawArguments().length > 2
                    ? java.util.Arrays.copyOfRange(inv.getRawArguments(), 2, inv.getRawArguments().length)
                    : new Object[0];
            if (args.length == 1 && args[0] instanceof Object[] arr) args = arr;
            sqls.add(sql);
            argsPorSql.add(args);
            List<Map<String, Object>> linhas =
                    sql.contains("AS valor_notas") ? resumo
                    : sql.contains("LIMIT ? OFFSET ?") ? pagina
                    : sql.contains("TO_CHAR(recebido_em") ? meses
                    : List.of();
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < linhas.size(); i++) out.add(mapper.mapRow(rs(linhas.get(i)), i));
            return out;
        });
        lenient().when(jdbc.query(contains("p.status = 'EMITIDA'"), any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper mapper = inv.getArgument(1);
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < aReceber.size(); i++) out.add(mapper.mapRow(rs(aReceber.get(i)), i));
            return out;
        });
    }

    @Test
    void calcular_semDados_retornaZerosESeisMesesVazios() {
        stub(List.of(Map.of("qtd", 0)), List.of(), List.of(), List.of(Map.of("valor", 0L, "qtd", 0L)));

        PosicaoCaixaResponse resp = service.calcular(null, null, 0, 10);

        assertThat(resp.resumo().quantidade()).isZero();
        assertThat(resp.resumo().recebido()).isZero();
        assertThat(resp.resumo().resultadoPin()).isZero();
        assertThat(resp.recebimentos().content()).isEmpty();
        assertThat(resp.recebimentos().totalPages()).isZero();
        assertThat(resp.recebimentosPorMes()).hasSize(6);
        assertThat(resp.recebimentosPorMes()).allMatch(m -> m.valor() == 0);
        assertThat(resp.recebimentosPorMes().get(5).mes()).isEqualTo(YearMonth.now().toString());
        assertThat(resp.aReceber()).isZero();
    }

    @Test
    void calcular_resumo_separaParteDosMedicosERetencoesEResultadoPin() {
        // 2 notas de R$ 10.000 recebidas líquidas de 6,15% (R$ 9.385 cada);
        // parte dos médicos R$ 8.500 + R$ 8.800.
        stub(List.of(Map.of("qtd", 2, "valor_notas", 2_000_000L, "recebido", 1_877_000L,
                        "parte_medicos", 1_730_000L, "retencoes", 123_000L)),
                List.of(), List.of(), List.of(Map.of("valor", 500_000L, "qtd", 1L)));

        PosicaoCaixaResponse.Resumo r = service.calcular(null, null, 0, 10).resumo();

        assertThat(r.quantidade()).isEqualTo(2);
        assertThat(r.valorNotas()).isEqualTo(2_000_000L);
        assertThat(r.retencoes()).isEqualTo(123_000L);
        assertThat(r.recebido()).isEqualTo(1_877_000L);
        assertThat(r.parteMedicos()).isEqualTo(1_730_000L);
        assertThat(r.repassado()).isZero();
        assertThat(r.aRepassar()).isEqualTo(1_730_000L);
        assertThat(r.resultadoPin()).isEqualTo(147_000L);
    }

    @Test
    void calcular_pagina_mapeiaLinhaECalculaRetencaoEResultadoPin() {
        UUID id = UUID.randomUUID();
        stub(List.of(Map.of("qtd", 11)),
                List.of(Map.of("producao_id", id.toString(), "tomador_nome", "UNIMED",
                        "competencia", "2026-10", "recebido_em", Date.valueOf(LocalDate.of(2026, 10, 25)),
                        "valor_bruto", 1_000_000L, "valor_recebido", 938_500L, "parte_medicos", 850_000L,
                        "qtd_medicos", 2, "tipo_match", "MANUAL")),
                List.of(), List.of());

        PosicaoCaixaResponse.Pagina p = service.calcular(null, null, 1, 10).recebimentos();

        assertThat(p.totalElements()).isEqualTo(11);
        assertThat(p.totalPages()).isEqualTo(2);
        assertThat(p.page()).isEqualTo(1);
        PosicaoCaixaResponse.Recebimento rec = p.content().get(0);
        assertThat(rec.producaoId()).isEqualTo(id);
        assertThat(rec.recebidoEm()).isEqualTo("2026-10-25");
        assertThat(rec.retencoes()).isEqualTo(61_500L);
        assertThat(rec.resultadoPin()).isEqualTo(88_500L);
        assertThat(rec.statusRepasse()).isEqualTo("AGUARDANDO");
        // offset = page × size
        Object[] argsPagina = argsPorSql.get(sqls.indexOf(sqls.stream().filter(s -> s.contains("LIMIT ?")).findFirst().orElseThrow()));
        assertThat(argsPagina).containsExactly(10, 10L);
    }

    @Test
    void calcular_filtroMesEBusca_viramParametrosNoResumoENaPagina() {
        stub(List.of(Map.of("qtd", 0)), List.of(), List.of(), List.of());

        service.calcular(YearMonth.of(2026, 10), "  unimed ", 0, 10);

        String sqlResumo = sqls.stream().filter(s -> s.contains("AS valor_notas")).findFirst().orElseThrow();
        assertThat(sqlResumo).contains("recebido_em >= ? AND recebido_em < ?").contains("tomador_nome ILIKE ?");
        Object[] args = argsPorSql.get(sqls.indexOf(sqlResumo));
        assertThat(args).containsExactly(Date.valueOf("2026-10-01"), Date.valueOf("2026-11-01"), "%unimed%");
    }

    @Test
    void calcular_soConsideraCreditosConciliados() {
        stub(List.of(Map.of("qtd", 0)), List.of(), List.of(), List.of());

        service.calcular(null, null, 0, 10);

        assertThat(sqls).allMatch(s -> s.contains("status_conciliacao = 'CONCILIADO'") && s.contains("le.tipo = 'CREDITO'"));
    }

    @Test
    void calcular_tamanhoDePaginaLimitadoA100() {
        stub(List.of(Map.of("qtd", 500)), List.of(), List.of(), List.of());

        PosicaoCaixaResponse.Pagina p = service.calcular(null, null, -3, 5000).recebimentos();

        assertThat(p.size()).isEqualTo(100);
        assertThat(p.page()).isZero();
        assertThat(p.totalPages()).isEqualTo(5);
    }

    @Test
    void calcular_historicoMensal_preencheMesesSemDeposito() {
        String mesAtual = YearMonth.now().toString();
        stub(List.of(Map.of("qtd", 1)), List.of(),
                List.of(Map.of("mes", mesAtual, "valor", 938_500L, "qtd", 1)), List.of());

        List<PosicaoCaixaResponse.RecebimentoMes> meses = service.calcular(null, null, 0, 10).recebimentosPorMes();

        assertThat(meses).hasSize(6);
        assertThat(meses.get(5).valor()).isEqualTo(938_500L);
        assertThat(meses.subList(0, 5)).allMatch(m -> m.valor() == 0);
    }

    @Test
    void listarRecebimentos_consultaSoConciliados() {
        var rec = new RecebimentoProducaoResponse(UUID.randomUUID(), "2026-10-20", 938_500L, "MANUAL");
        when(jdbc.query(contains("status_conciliacao = 'CONCILIADO'"), any(RowMapper.class)))
                .thenReturn(List.of(rec));

        assertThat(service.listarRecebimentos()).containsExactly(rec);
    }
}
