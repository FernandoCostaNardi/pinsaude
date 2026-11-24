package br.com.pinsaude.fiscal.messaging;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.RegimeTributario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** CBS-40: evento da nota emitida para o ledger. */
class LedgerProducerTest {

    private static NotaFiscal nota() {
        NotaFiscal n = new NotaFiscal();
        n.setProducaoId(UUID.randomUUID());
        n.setMedicoId(UUID.randomUUID());
        n.setCnpjIdTenant("12345678000199");
        n.setCompetencia("2027-01");
        n.setValorBruto(1_000_000L);
        n.setTaxaPin(150_000L);
        n.setValorLiquidoMedico(850_000L);
        n.setValorIss(20_000L);
        n.setValorIr(15_000L);
        n.setValorCsll(10_000L);
        n.setValorPis(0L);
        n.setValorCofins(0L);
        return n;
    }

    @Test
    void notaIbsCbs_levaRegimeECbsIbsDaNota() {
        NotaFiscal n = nota();
        n.setRegimeTributario(RegimeTributario.IBS_CBS);
        n.setValorCbs(33_000L);
        n.setValorIbs(400L);

        LedgerNotaEmitidaMessage m = LedgerProducer.mensagem(n);

        assertThat(m.regime()).isEqualTo("IBS_CBS");
        assertThat(m.valorCbsCentavos()).isEqualTo(33_000L);
        assertThat(m.valorIbsCentavos()).isEqualTo(400L);
        assertThat(m.valorBrutoCentavos()).isEqualTo(1_000_000L);
        assertThat(m.valorLiquidoMedicoCentavos()).isEqualTo(850_000L);
        assertThat(m.valorIssCentavos()).isEqualTo(20_000L);
        assertThat(m.medicoId()).isEqualTo(n.getMedicoId());
        assertThat(m.cnpjTenant()).isEqualTo("12345678000199");
        assertThat(m.competencia()).isEqualTo("2027-01");
        assertThat(m.cbsIbsPorFora()).isFalse();   // CBS-41: a nota grava; dentro do preço
    }

    @Test
    void notaIbsCbsPorFora_avisaOLedger() {
        NotaFiscal n = nota();
        n.setRegimeTributario(RegimeTributario.IBS_CBS);
        n.setValorCbs(44_800L);
        n.setValorIbs(400L);
        n.setCbsIbsPorFora(true);

        assertThat(LedgerProducer.mensagem(n).cbsIbsPorFora()).isTrue();
    }

    @Test
    void notaLegado_semCbsIbs_eLiquidoNuloCaiNoBrutoMenosTaxa() {
        NotaFiscal n = nota();
        n.setValorLiquidoMedico(null);
        n.setValorCbs(null);
        n.setValorIbs(null);

        LedgerNotaEmitidaMessage m = LedgerProducer.mensagem(n);

        assertThat(m.regime()).isEqualTo("LEGADO");
        assertThat(m.valorCbsCentavos()).isZero();
        assertThat(m.valorIbsCentavos()).isZero();
        assertThat(m.valorLiquidoMedicoCentavos()).isEqualTo(850_000L);
        assertThat(m.cbsIbsPorFora()).isNull();
    }

    @Test
    void json_temOsCamposDoNotaEmitidaEventDoLedger() throws Exception {
        JsonNode json = new ObjectMapper().valueToTree(LedgerProducer.mensagem(nota()));
        List<String> campos = new ArrayList<>();
        json.fieldNames().forEachRemaining(campos::add);

        assertThat(campos).containsExactlyInAnyOrder(
            "notaId", "medicoId", "cnpjTenant", "competencia",
            "valorBrutoCentavos", "valorLiquidoMedicoCentavos",
            "valorIssCentavos", "valorIrCentavos", "valorCsllCentavos", "valorPisCentavos", "valorCofinsCentavos",
            "regime", "valorCbsCentavos", "valorIbsCentavos", "cbsIbsPorFora");
    }
}
