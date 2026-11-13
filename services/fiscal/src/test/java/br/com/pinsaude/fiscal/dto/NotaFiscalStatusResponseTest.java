package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.RegimeTributario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotaFiscalStatusResponseTest {

    private NotaFiscal notaBase() {
        NotaFiscal n = new NotaFiscal();
        n.setProducaoId(UUID.randomUUID());
        n.setTomadorId(UUID.randomUUID());
        n.setCompetencia("2027-01");
        n.setValorBruto(1_000_000L);
        n.setTaxaPin(150_000L);
        n.setValorLiquidoMedico(850_000L);
        return n;
    }

    @Test
    void notaNova_nasceLegado_comCbsIbsZerados() {
        NotaFiscalStatusResponse r = NotaFiscalStatusResponse.from(notaBase());

        assertThat(r.regimeTributario()).isEqualTo(RegimeTributario.LEGADO);
        assertThat(r.valorCbs()).isZero();
        assertThat(r.valorIbs()).isZero();
        assertThat(r.aliqCbs()).isNull();
        assertThat(r.aliqIbs()).isNull();
        assertThat(r.cstIbsCbs()).isNull();
        assertThat(r.cClassTrib()).isNull();
        assertThat(r.fatorIss()).isNull();
    }

    @Test
    void notaIbsCbs_expoeSnapshotDoRegime() {
        NotaFiscal n = notaBase();
        n.setRegimeTributario(RegimeTributario.IBS_CBS);
        n.setValorCbs(44_800L);
        n.setValorIbs(400L);
        n.setAliqCbs(new BigDecimal("0.0448"));
        n.setAliqIbs(new BigDecimal("0.0004"));
        n.setCstIbsCbs("200");
        n.setCClassTrib("200029");
        n.setFatorIss(new BigDecimal("1.0000"));

        NotaFiscalStatusResponse r = NotaFiscalStatusResponse.from(n);

        assertThat(r.regimeTributario()).isEqualTo(RegimeTributario.IBS_CBS);
        assertThat(r.valorCbs()).isEqualTo(44_800L);
        assertThat(r.valorIbs()).isEqualTo(400L);
        assertThat(r.aliqCbs()).isEqualByComparingTo("0.0448");
        assertThat(r.aliqIbs()).isEqualByComparingTo("0.0004");
        assertThat(r.cstIbsCbs()).isEqualTo("200");
        assertThat(r.cClassTrib()).isEqualTo("200029");
        assertThat(r.fatorIss()).isEqualByComparingTo("1");
    }
}
