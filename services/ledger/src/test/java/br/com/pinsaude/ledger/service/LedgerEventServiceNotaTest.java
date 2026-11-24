package br.com.pinsaude.ledger.service;

import br.com.pinsaude.ledger.domain.TipoPartida;
import br.com.pinsaude.ledger.dto.CriarLancamentoRequest;
import br.com.pinsaude.ledger.dto.PartidaRequest;
import br.com.pinsaude.ledger.messaging.NotaEmitidaEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * CBS-40: lançamento da NFS-e emitida com CBS e IBS a recolher, nos dois regimes.
 */
@ExtendWith(MockitoExtension.class)
class LedgerEventServiceNotaTest {

    @Mock LancamentoService lancamentoService;
    @InjectMocks LedgerEventService service;

    private static final String TENANT = "12345678000195";

    private CriarLancamentoRequest processar(NotaEmitidaEvent e) {
        service.processarNotaEmitida(e);
        ArgumentCaptor<CriarLancamentoRequest> captor = ArgumentCaptor.forClass(CriarLancamentoRequest.class);
        verify(lancamentoService).criar(captor.capture());
        return captor.getValue();
    }

    private static long soma(List<PartidaRequest> partidas, TipoPartida tipo) {
        return partidas.stream().filter(p -> p.tipo() == tipo).mapToLong(PartidaRequest::valorCentavos).sum();
    }

    private static long valor(List<PartidaRequest> partidas, String conta, TipoPartida tipo) {
        return partidas.stream().filter(p -> p.contaCodigo().equals(conta) && p.tipo() == tipo)
            .mapToLong(PartidaRequest::valorCentavos).sum();
    }

    @Test
    void legado_semCamposNovos_mesmoLancamentoDeAntes() {
        var req = processar(new NotaEmitidaEvent(UUID.randomUUID(), UUID.randomUUID(), TENANT, "2026-12",
            1_000_000L, 850_000L, 50_000L, 15_000L, 10_000L, 6_500L, 30_000L));

        var p = req.partidas();
        assertThat(soma(p, TipoPartida.DEBITO)).isEqualTo(soma(p, TipoPartida.CREDITO)).isEqualTo(1_000_000L);
        assertThat(valor(p, "1.1.01", TipoPartida.DEBITO)).isEqualTo(1_000_000L);
        assertThat(valor(p, "2.1.02", TipoPartida.CREDITO)).isEqualTo(850_000L);
        assertThat(valor(p, "2.1.01", TipoPartida.CREDITO)).isEqualTo(111_500L);
        assertThat(valor(p, "3.1.01", TipoPartida.CREDITO)).isEqualTo(38_500L);
        assertThat(p).noneMatch(x -> x.contaCodigo().equals("2.1.03") || x.contaCodigo().equals("2.1.04"));
        assertThat(req.descricao()).doesNotContain("CBS");
    }

    @Test
    void ibsCbs_dentroDoPreco_creditaCbsEIbsARecolherEDescontaDaReceita() {
        // 2027: retenção IR 1,5% + CSLL 1%; ISS 2%; CBS 3.300 e IBS 40 (já com a redução de 60%)
        var req = processar(new NotaEmitidaEvent(UUID.randomUUID(), UUID.randomUUID(), TENANT, "2027-01",
            1_000_000L, 850_000L, 20_000L, 15_000L, 10_000L, 0L, 0L,
            "IBS_CBS", 33_000L, 400L, null));

        var p = req.partidas();
        assertThat(soma(p, TipoPartida.DEBITO)).isEqualTo(soma(p, TipoPartida.CREDITO)).isEqualTo(1_000_000L);
        assertThat(valor(p, "1.1.01", TipoPartida.DEBITO)).isEqualTo(1_000_000L);
        assertThat(valor(p, "2.1.01", TipoPartida.CREDITO)).isEqualTo(45_000L); // CBS/IBS fora das retenções
        assertThat(valor(p, "2.1.03", TipoPartida.CREDITO)).isEqualTo(33_000L);
        assertThat(valor(p, "2.1.04", TipoPartida.CREDITO)).isEqualTo(400L);
        assertThat(valor(p, "3.1.01", TipoPartida.CREDITO)).isEqualTo(1_000_000L - 850_000L - 45_000L - 33_400L);
        assertThat(req.descricao()).contains("CBS/IBS");
    }

    @Test
    void ibsCbs_porFora_recebivelIncluiCbsIbsEReceitaNaoMuda() {
        var req = processar(new NotaEmitidaEvent(UUID.randomUUID(), UUID.randomUUID(), TENANT, "2027-01",
            1_000_000L, 850_000L, 20_000L, 15_000L, 10_000L, 0L, 0L,
            "IBS_CBS", 33_000L, 400L, true));

        var p = req.partidas();
        assertThat(soma(p, TipoPartida.DEBITO)).isEqualTo(soma(p, TipoPartida.CREDITO)).isEqualTo(1_033_400L);
        assertThat(valor(p, "1.1.01", TipoPartida.DEBITO)).isEqualTo(1_033_400L);
        assertThat(valor(p, "3.1.01", TipoPartida.CREDITO)).isEqualTo(1_000_000L - 850_000L - 45_000L);
    }

    @Test
    void margemNegativa_debitaReceitaEContinuaBalanceado() {
        // taxa Pin de 5%: tributos (8,24%) maiores que a margem
        var req = processar(new NotaEmitidaEvent(UUID.randomUUID(), UUID.randomUUID(), TENANT, "2027-01",
            1_000_000L, 950_000L, 20_000L, 15_000L, 10_000L, 0L, 0L,
            "IBS_CBS", 33_000L, 400L, false));

        var p = req.partidas();
        assertThat(soma(p, TipoPartida.DEBITO)).isEqualTo(soma(p, TipoPartida.CREDITO));
        assertThat(valor(p, "3.1.01", TipoPartida.DEBITO)).isEqualTo(28_400L);
        assertThat(valor(p, "3.1.01", TipoPartida.CREDITO)).isZero();
    }

    @Test
    void tenantFormatado_viraSomenteDigitos() {
        var req = processar(new NotaEmitidaEvent(UUID.randomUUID(), null, "12.345.678/0001-95", "2027-01",
            100_000L, 85_000L, 0L, 0L, 0L, 0L, 0L, "IBS_CBS", 3_300L, 40L, null));
        assertThat(req.cnpjIdTenant()).isEqualTo(TENANT);
    }

    @Test
    void mensagemAntiga_semCamposNovos_desserializaComoLegado() throws Exception {
        String json = """
            {"notaId":"%s","medicoId":"%s","cnpjTenant":"%s","competencia":"2026-12",
             "valorBrutoCentavos":1000000,"valorLiquidoMedicoCentavos":850000,
             "valorIssCentavos":50000,"valorIrCentavos":15000,"valorCsllCentavos":10000,
             "valorPisCentavos":6500,"valorCofinsCentavos":30000}
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), TENANT);

        NotaEmitidaEvent e = new ObjectMapper().findAndRegisterModules().readValue(json, NotaEmitidaEvent.class);

        assertThat(e.regimeOuLegado()).isEqualTo("LEGADO");
        assertThat(e.valorCbsCentavos()).isZero();
        assertThat(e.valorIbsCentavos()).isZero();
        assertThat(e.porFora()).isFalse();
        assertThat(e.valorCofinsCentavos()).isEqualTo(30_000L);
    }
}
