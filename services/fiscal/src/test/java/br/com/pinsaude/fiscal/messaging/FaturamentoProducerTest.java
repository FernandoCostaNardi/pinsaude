package br.com.pinsaude.fiscal.messaging;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FaturamentoProducerTest {

    private static NotaFiscal nota(long bruto, Long iss, Long ir, Long csll, Long pis, Long cofins) {
        NotaFiscal n = new NotaFiscal();
        n.setProducaoId(UUID.randomUUID());
        n.setCnpjIdTenant("12345678000199");
        n.setValorBruto(bruto);
        n.setValorIss(iss);
        n.setValorIr(ir);
        n.setValorCsll(csll);
        n.setValorPis(pis);
        n.setValorCofins(cofins);
        return n;
    }

    @Test
    void notaLegado_comRetencaoFederal_liquidoDescontaOs615() {
        // 2026: IR 1,5% + CSLL 1% + PIS 0,65% + COFINS 3% = 6,15%
        NotaFiscal n = nota(1_000_000L, 0L, 15_000L, 10_000L, 6_500L, 30_000L);
        n.setEmitidaAt(OffsetDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZoneOffset.ofHours(-3)));

        ProducaoEmitidaMessage m = FaturamentoProducer.mensagem(n);

        assertThat(m.valorLiquidoEsperado()).isEqualTo(938_500L);
        assertThat(m.emitidaEm()).isEqualTo("2026-11-10");
        assertThat(m.producaoId()).isEqualTo(n.getProducaoId());
        assertThat(m.cnpjTenant()).isEqualTo("12345678000199");
    }

    @Test
    void notaIbsCbs_retemSoIrECsll_liquidoDescontaOs25() {
        NotaFiscal n = nota(1_000_000L, 0L, 15_000L, 10_000L, 0L, 0L);
        n.setValorCbs(80_000L); // débito da Pin, não retido: não entra
        n.setValorIbs(10_000L);

        assertThat(FaturamentoProducer.mensagem(n).valorLiquidoEsperado()).isEqualTo(975_000L);
    }

    @Test
    void semRetencao_eSemDataDeEmissao_liquidoIgualAoBruto() {
        ProducaoEmitidaMessage m = FaturamentoProducer.mensagem(nota(500_000L, null, null, null, null, null));

        assertThat(m.valorLiquidoEsperado()).isEqualTo(500_000L);
        assertThat(m.emitidaEm()).isNull();
    }

    @Test
    void dataDeEmissao_noFusoDeSaoPaulo() {
        NotaFiscal n = nota(500_000L, 0L, 0L, 0L, 0L, 0L);
        n.setEmitidaAt(OffsetDateTime.of(2026, 11, 11, 1, 0, 0, 0, ZoneOffset.UTC)); // 22h do dia 10 em SP

        assertThat(FaturamentoProducer.mensagem(n).emitidaEm()).isEqualTo("2026-11-10");
    }
}
