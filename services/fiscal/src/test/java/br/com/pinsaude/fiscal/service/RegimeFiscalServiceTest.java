package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.CronogramaReforma;
import br.com.pinsaude.fiscal.domain.ParametroFiscal;
import br.com.pinsaude.fiscal.dto.RegimeCompetenciaResponse;
import br.com.pinsaude.fiscal.repository.CronogramaReformaRepository;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** CBS-15 — regime da empresa por competência, antes e depois da competência da chave. */
@ExtendWith(MockitoExtension.class)
class RegimeFiscalServiceTest {

    private static final String CNPJ = "12345678000195";
    private static final LocalDate VIRADA = LocalDate.of(2027, 1, 1);

    @Mock ParametroFiscalRepository parametroRepo;
    @Mock CronogramaReformaRepository cronogramaRepo;
    @InjectMocks RegimeFiscalService service;

    private void vigente(ParametroFiscal pf) {
        when(parametroRepo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(eq(CNPJ), any()))
            .thenReturn(Optional.ofNullable(pf));
    }

    private static ParametroFiscal parametro(LocalDate vigencia, boolean ligada, boolean homologado) {
        ParametroFiscal pf = new ParametroFiscal();
        pf.setCnpjId(CNPJ);
        pf.setVigenciaInicio(vigencia);
        pf.setIbsCbsAtivo(ligada);
        pf.setHomologado(homologado);
        return pf;
    }

    private static CronogramaReforma cron(int ano, String cbs, String uf, String mun, String fator, boolean definitivas) {
        CronogramaReforma c = new CronogramaReforma();
        c.setAno((short) ano);
        c.setAliqCbs(cbs == null ? null : new BigDecimal(cbs));
        c.setAliqIbsUf(uf == null ? null : new BigDecimal(uf));
        c.setAliqIbsMun(mun == null ? null : new BigDecimal(mun));
        c.setFatorIss(new BigDecimal(fator));
        c.setAliquotasDefinitivas(definitivas);
        return c;
    }

    @Test
    void antesDaChave_legadoComPisCofins() {
        vigente(parametro(LocalDate.of(2024, 1, 1), false, true));

        RegimeCompetenciaResponse r = service.regime(CNPJ, "2026-12");

        assertThat(r.regime()).isEqualTo("LEGADO");
        assertThat(r.tributosVisiveis()).containsExactly("ISS", "IR", "CSLL", "PIS", "COFINS");
        assertThat(r.chaveLigada()).isFalse();
        assertThat(r.vigenciaParametro()).isEqualTo("2024-01");
        assertThat(r.aliquotas()).isNull();
        verifyNoInteractions(cronogramaRepo);
    }

    @Test
    void naCompetenciaDaChaveHomologada_ibsCbs() {
        vigente(parametro(VIRADA, true, true));
        when(cronogramaRepo.findById((short) 2027))
            .thenReturn(Optional.of(cron(2027, "0.1120", "0.0005", "0.0005", "1.0000", false)));

        RegimeCompetenciaResponse r = service.regime(CNPJ, "2027-01");

        assertThat(r.regime()).isEqualTo("IBS_CBS");
        assertThat(r.tributosVisiveis()).containsExactly("ISS", "IR", "CSLL", "CBS", "IBS");
        assertThat(r.chaveLigada()).isTrue();
        assertThat(r.homologado()).isTrue();
        assertThat(r.vigenciaParametro()).isEqualTo("2027-01");
        assertThat(r.aliquotas().ano()).isEqualTo(2027);
        assertThat(r.aliquotas().cbs()).isEqualByComparingTo("0.1120");
        assertThat(r.aliquotas().ibs()).isEqualByComparingTo("0.0010");
        assertThat(r.aliquotas().fatorIss()).isEqualByComparingTo("1");
        assertThat(r.aliquotas().definitivas()).isFalse();
    }

    @Test
    void chaveLigadaSemHomologacao_continuaLegado() {
        vigente(parametro(VIRADA, true, false));

        RegimeCompetenciaResponse r = service.regime(CNPJ, "2027-03");

        assertThat(r.regime()).isEqualTo("LEGADO");
        assertThat(r.tributosVisiveis()).contains("PIS", "COFINS").doesNotContain("CBS", "IBS");
        assertThat(r.chaveLigada()).isTrue();
        assertThat(r.homologado()).isFalse();
        verifyNoInteractions(cronogramaRepo);
    }

    @Test
    void consultaUsaPrimeiroDiaDaCompetencia() {
        vigente(null);
        service.regime(CNPJ, "2027-02");
        verify(parametroRepo).findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(
            CNPJ, LocalDate.of(2027, 2, 1));
    }

    @Test
    void semParametro_legadoSemVigencia() {
        vigente(null);

        RegimeCompetenciaResponse r = service.regime(CNPJ, "2027-01");

        assertThat(r.regime()).isEqualTo("LEGADO");
        assertThat(r.chaveLigada()).isFalse();
        assertThat(r.vigenciaParametro()).isNull();
    }

    @Test
    void em2033_semIss() {
        vigente(parametro(VIRADA, true, true));
        when(cronogramaRepo.findById((short) 2033))
            .thenReturn(Optional.of(cron(2033, "0.0880", "0.1000", "0.0700", "0.0000", true)));

        RegimeCompetenciaResponse r = service.regime(CNPJ, "2033-06");

        assertThat(r.tributosVisiveis()).containsExactly("IR", "CSLL", "CBS", "IBS");
        assertThat(r.aliquotas().fatorIss()).isEqualByComparingTo("0");
    }

    @Test
    void anoComAliquotasNaoFixadas_devolveNulos() {
        vigente(parametro(VIRADA, true, true));
        when(cronogramaRepo.findById((short) 2029))
            .thenReturn(Optional.of(cron(2029, null, null, null, "0.9000", false)));

        RegimeCompetenciaResponse r = service.regime(CNPJ, "2029-01");

        assertThat(r.regime()).isEqualTo("IBS_CBS");
        assertThat(r.tributosVisiveis()).containsExactly("ISS", "IR", "CSLL", "CBS", "IBS");
        assertThat(r.aliquotas().cbs()).isNull();
        assertThat(r.aliquotas().ibs()).isNull();
    }

    @Test
    void anoSemCronograma_422() {
        vigente(parametro(VIRADA, true, true));
        when(cronogramaRepo.findById((short) 2034)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.regime(CNPJ, "2034-01"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2027", "2027-1", "2027-13", "27-01", "2027/01", "abc"})
    void competenciaInvalida_400(String competencia) {
        assertThatThrownBy(() -> service.regime(CNPJ, competencia))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(parametroRepo, never())
            .findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(anyString(), any());
    }
}
