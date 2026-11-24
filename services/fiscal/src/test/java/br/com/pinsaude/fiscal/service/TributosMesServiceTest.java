package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.RegimeTributario;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.TributosMesResponse;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TributosMesServiceTest {

    @Mock NotaFiscalRepository notaRepo;
    @InjectMocks TributosMesService service;

    @Test
    void separaRetencoesDeCbsIbs_porRegime_eTotaisBatemComASoma() {
        // IBS_CBS vem antes no resultado do banco: a resposta ordena LEGADO primeiro.
        List<Object[]> linhas = List.of(
            new Object[] { RegimeTributario.IBS_CBS, 2L, 2_000_000L, 0L, 30_000L, 20_000L, 0L, 0L, 15_000L, 25_000L },
            new Object[] { RegimeTributario.LEGADO, 3L, 3_000_000L, 10_000L, 45_000L, 30_000L, 19_500L, 90_000L, 0L, 0L }
        );
        when(notaRepo.somarTributosPorRegime(eq(StatusNota.EMITIDA), any(), any())).thenReturn(linhas);

        TributosMesResponse r = service.calcular(YearMonth.of(2027, 1));

        assertThat(r.mes()).isEqualTo("2027-01");
        assertThat(r.quantidadeNotas()).isEqualTo(5);
        assertThat(r.valorNotas()).isEqualTo(5_000_000L);
        assertThat(r.porRegime()).extracting(TributosMesResponse.PorRegime::regime)
            .containsExactly("LEGADO", "IBS_CBS");

        var legado = r.porRegime().get(0);
        assertThat(legado.retencoes().total()).isEqualTo(10_000 + 45_000 + 30_000 + 19_500 + 90_000);
        assertThat(legado.cbsIbs().total()).isZero();

        var novo = r.porRegime().get(1);
        assertThat(novo.retencoes().pis()).isZero();
        assertThat(novo.retencoes().cofins()).isZero();
        assertThat(novo.retencoes().total()).isEqualTo(50_000);
        assertThat(novo.cbsIbs().total()).isEqualTo(40_000);

        // Totais = soma dos regimes
        assertThat(r.retencoes().ir()).isEqualTo(75_000);
        assertThat(r.retencoes().csll()).isEqualTo(50_000);
        assertThat(r.retencoes().total()).isEqualTo(legado.retencoes().total() + novo.retencoes().total());
        assertThat(r.cbsIbs().cbs()).isEqualTo(15_000);
        assertThat(r.cbsIbs().ibs()).isEqualTo(25_000);
        assertThat(r.cbsIbs().total()).isEqualTo(40_000);
    }

    @Test
    void mesPelaDataDeEmissaoNoFusoDeSaoPaulo() {
        when(notaRepo.somarTributosPorRegime(any(), any(), any())).thenReturn(List.of());

        service.calcular(YearMonth.of(2027, 1));

        ArgumentCaptor<OffsetDateTime> de = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> ate = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(notaRepo).somarTributosPorRegime(eq(StatusNota.EMITIDA), de.capture(), ate.capture());
        assertThat(de.getValue()).isEqualTo(OffsetDateTime.parse("2027-01-01T00:00:00-03:00"));
        assertThat(ate.getValue()).isEqualTo(OffsetDateTime.parse("2027-02-01T00:00:00-03:00"));
    }

    @Test
    void semNotas_tudoZero() {
        when(notaRepo.somarTributosPorRegime(any(), any(), any())).thenReturn(List.of());

        TributosMesResponse r = service.calcular(null);

        assertThat(r.mes()).isNull();
        assertThat(r.quantidadeNotas()).isZero();
        assertThat(r.retencoes().total()).isZero();
        assertThat(r.cbsIbs().total()).isZero();
        assertThat(r.porRegime()).isEmpty();
    }

    @Test
    void regimeNulo_contaComoLegado() {
        List<Object[]> linhas = List.<Object[]>of(new Object[] { null, 1L, 100_000L, null, 1_500L, 1_000L, 650L, 3_000L, null, null });
        when(notaRepo.somarTributosPorRegime(any(), any(), any())).thenReturn(linhas);

        TributosMesResponse r = service.calcular(YearMonth.of(2026, 12));

        assertThat(r.porRegime()).singleElement().satisfies(p -> {
            assertThat(p.regime()).isEqualTo("LEGADO");
            assertThat(p.retencoes().total()).isEqualTo(6_150);
        });
    }
}
