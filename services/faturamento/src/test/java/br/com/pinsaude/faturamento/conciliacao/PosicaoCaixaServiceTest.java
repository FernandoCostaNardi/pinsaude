package br.com.pinsaude.faturamento.conciliacao;

import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse;
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
}
