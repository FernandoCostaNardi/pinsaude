package br.com.pinsaude.faturamento.conciliacao.messaging;

import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.StatusProducao;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProducaoEmitidaConsumerTest {

    @Mock ProducaoRepository producaoRepo;
    @InjectMocks ProducaoEmitidaConsumer consumer;

    private Producao producao(StatusProducao status) {
        Producao p = new Producao();
        p.setId(UUID.randomUUID());
        p.setValorBruto(1_000_000L);
        p.setStatus(status);
        when(producaoRepo.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void emitida_gravaStatusLiquidoEsperadoEData() {
        Producao p = producao(StatusProducao.CONFIRMADA);

        consumer.consumir(new ProducaoEmitidaMessage(p.getId(), "123", 938_500L, "2026-11-05"));

        assertThat(p.getStatus()).isEqualTo(StatusProducao.EMITIDA);
        assertThat(p.getValorLiquidoEsperado()).isEqualTo(938_500L);
        assertThat(p.getNotaEmitidaEm()).isEqualTo(LocalDate.of(2026, 11, 5));
        verify(producaoRepo).save(p);
    }

    @Test
    void jaEmitida_notaSubstituida_atualizaLiquido() {
        Producao p = producao(StatusProducao.EMITIDA);
        p.setValorLiquidoEsperado(1_000_000L);

        consumer.consumir(new ProducaoEmitidaMessage(p.getId(), "123", 975_000L, "2027-02-03"));

        assertThat(p.getValorLiquidoEsperado()).isEqualTo(975_000L);
        verify(producaoRepo).save(p);
    }

    @Test
    void jaEmitidaComOsMesmosDados_naoSalva() {
        Producao p = producao(StatusProducao.EMITIDA);
        p.setValorLiquidoEsperado(938_500L);
        p.setNotaEmitidaEm(LocalDate.of(2026, 11, 5));

        consumer.consumir(new ProducaoEmitidaMessage(p.getId(), "123", 938_500L, "2026-11-05"));

        verify(producaoRepo, never()).save(any());
    }

    @Test
    void mensagemDaVersaoAnterior_semLiquido_soMudaStatus() {
        Producao p = producao(StatusProducao.CONFIRMADA);

        consumer.consumir(new ProducaoEmitidaMessage(p.getId(), "123"));

        assertThat(p.getStatus()).isEqualTo(StatusProducao.EMITIDA);
        assertThat(p.getValorLiquidoEsperado()).isNull();
        assertThat(p.getNotaEmitidaEm()).isNull();
    }

    @Test
    void liquidoForaDoIntervalo_eDataInvalida_saoIgnorados() {
        Producao p = producao(StatusProducao.CONFIRMADA);

        consumer.consumir(new ProducaoEmitidaMessage(p.getId(), "123", 2_000_000L, "05/11/2026"));

        assertThat(p.getValorLiquidoEsperado()).isNull();
        assertThat(p.getNotaEmitidaEm()).isNull();
        assertThat(p.getStatus()).isEqualTo(StatusProducao.EMITIDA);
    }
}
