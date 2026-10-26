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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosicaoCaixaServiceTest {

    @Mock
    JdbcTemplate jdbc;

    PosicaoCaixaService service;

    @BeforeEach
    void setUp() {
        service = new PosicaoCaixaService(jdbc);
    }

    @Test
    void calcular_semDados_retornaZeros() {
        when(jdbc.queryForObject(contains("SUM(p.valor_bruto)"), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForObject(contains("SUM(le.valor)"), eq(Long.class))).thenReturn(0L);
        when(jdbc.query(contains("producoes p"), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(contains("semana_key"), any(RowMapper.class))).thenReturn(List.of());

        PosicaoCaixaResponse resp = service.calcular();

        assertThat(resp.aReceber()).isEqualTo(0L);
        assertThat(resp.recebidoNaoRepassado()).isEqualTo(0L);
        assertThat(resp.repassadoNoMes()).isEqualTo(0L);
        assertThat(resp.saldoEstimado()).isEqualTo(0L);
        assertThat(resp.notasEmAberto()).isEmpty();
        assertThat(resp.recebimentosPorSemana()).isEmpty();
    }

    @Test
    void calcular_comDados_retornaValoresCorretos() {
        when(jdbc.queryForObject(contains("SUM(p.valor_bruto)"), eq(Long.class))).thenReturn(500_000L);
        when(jdbc.queryForObject(contains("SUM(le.valor)"), eq(Long.class))).thenReturn(300_000L);

        var nota = new PosicaoCaixaResponse.NotaEmAberto(
                UUID.randomUUID(), UUID.randomUUID(), "Hospital ABC",
                500_000L, LocalDate.now().minusDays(45).toString(), 45);
        when(jdbc.query(contains("producoes p"), any(RowMapper.class))).thenReturn(List.of(nota));

        var semana = new PosicaoCaixaResponse.RecebimentoSemana("2026-W26", "23/06", 300_000L);
        when(jdbc.query(contains("semana_key"), any(RowMapper.class))).thenReturn(List.of(semana));

        PosicaoCaixaResponse resp = service.calcular();

        assertThat(resp.aReceber()).isEqualTo(500_000L);
        assertThat(resp.recebidoNaoRepassado()).isEqualTo(300_000L);
        assertThat(resp.repassadoNoMes()).isEqualTo(0L);
        assertThat(resp.saldoEstimado()).isEqualTo(300_000L);
        assertThat(resp.notasEmAberto()).hasSize(1);
        assertThat(resp.notasEmAberto().get(0).diasEmAberto()).isEqualTo(45);
        assertThat(resp.recebimentosPorSemana()).hasSize(1);
    }

    @Test
    void calcular_saldoEstimado_ehRecebidoMinusRepassado() {
        when(jdbc.queryForObject(contains("SUM(p.valor_bruto)"), eq(Long.class))).thenReturn(1_000_000L);
        when(jdbc.queryForObject(contains("SUM(le.valor)"), eq(Long.class))).thenReturn(400_000L);
        when(jdbc.query(contains("producoes p"), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(contains("semana_key"), any(RowMapper.class))).thenReturn(List.of());

        PosicaoCaixaResponse resp = service.calcular();

        // saldoEstimado = recebidoNaoRepassado (400k) - repassadoNoMes (0) = 400k
        assertThat(resp.saldoEstimado()).isEqualTo(400_000L);
    }

    @Test
    void calcular_aReceber_naoIncluiNotasConciliadas() {
        // A Receber vem de producoes EMITIDAS sem conciliacao
        // Se aReceber = 0, significa todas as notas já foram pagas (conciliadas)
        when(jdbc.queryForObject(contains("SUM(p.valor_bruto)"), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForObject(contains("SUM(le.valor)"), eq(Long.class))).thenReturn(250_000L);
        when(jdbc.query(contains("producoes p"), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(contains("semana_key"), any(RowMapper.class))).thenReturn(List.of());

        PosicaoCaixaResponse resp = service.calcular();

        assertThat(resp.aReceber()).isEqualTo(0L);
        assertThat(resp.recebidoNaoRepassado()).isEqualTo(250_000L);
        assertThat(resp.notasEmAberto()).isEmpty();
    }

    @Test
    void calcular_aRepassar_somaParteDosMedicosDasRecebidas() {
        when(jdbc.queryForObject(contains("SUM(p.valor_bruto)"), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForObject(contains("SUM(le.valor)"), eq(Long.class))).thenReturn(1_876_998L);
        // O alias da produção nessa query não pode casar com contains("producoes p") dos outros testes.
        // Nota de R$ 10.000 (2 médicos, taxa 15%) recebida líquida de 6,15% → R$ 9.385
        var r1 = new PosicaoCaixaResponse.RecebidaAguardandoRepasse(UUID.randomUUID(), "Hospital A",
                "2026-10", "2026-10-20", 1_000_000L, 938_500L, 850_000L, 2);
        // Nota de R$ 10.000 de um médico com taxa 12% → R$ 8.800 a repassar
        var r2 = new PosicaoCaixaResponse.RecebidaAguardandoRepasse(UUID.randomUUID(), "Hospital B",
                "2026-10", "2026-10-22", 1_000_000L, 938_498L, 880_000L, 1);
        when(jdbc.query(contains("a_repassar"), any(RowMapper.class))).thenReturn(List.of(r1, r2));
        when(jdbc.query(contains("FROM faturamento.producoes p"), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(contains("semana_key"), any(RowMapper.class))).thenReturn(List.of());

        PosicaoCaixaResponse resp = service.calcular();

        assertThat(resp.aRepassar()).isEqualTo(1_730_000L);
        assertThat(resp.recebidasAguardandoRepasse()).hasSize(2);
        assertThat(resp.recebidoNaoRepassado()).isEqualTo(1_876_998L);
    }

    @Test
    void calcular_semRecebidas_aRepassarZero() {
        when(jdbc.queryForObject(contains("SUM(p.valor_bruto)"), eq(Long.class))).thenReturn(100L);
        when(jdbc.queryForObject(contains("SUM(le.valor)"), eq(Long.class))).thenReturn(0L);

        PosicaoCaixaResponse resp = service.calcular();

        assertThat(resp.aRepassar()).isZero();
        assertThat(resp.recebidasAguardandoRepasse()).isEmpty();
    }

    @Test
    void listarRecebimentos_consultaSoConciliados() {
        var rec = new RecebimentoProducaoResponse(UUID.randomUUID(), "2026-10-20", 938_500L, "MANUAL");
        when(jdbc.query(contains("status_conciliacao = 'CONCILIADO'"), any(RowMapper.class)))
                .thenReturn(List.of(rec));

        assertThat(service.listarRecebimentos()).containsExactly(rec);
    }
}
