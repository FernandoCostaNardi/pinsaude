package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.*;
import br.com.pinsaude.fiscal.dto.RegraApuracaoRequest;
import br.com.pinsaude.fiscal.dto.RegraApuracaoResponse;
import br.com.pinsaude.fiscal.dto.TributoRegraApuracaoRequest;
import br.com.pinsaude.fiscal.repository.RegraApuracaoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegraApuracaoServiceTest {

    private static final String CNPJ = "12345678000190";

    @Mock RegraApuracaoRepository repo;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RegraApuracaoService service() {
        return new RegraApuracaoService(repo);
    }

    private static void login(String cnpj) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject("sub")
            .claim("preferred_username", "contador@pinsaude.com.br")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (cnpj != null) b.claim("cnpj_id", cnpj);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(b.build()));
    }

    static TributoRegraApuracaoRequest trib(TributoApuracao t, String aliq, BaseCalculoApuracao base) {
        return new TributoRegraApuracaoRequest(t, true, AplicacaoRegime.AMBOS, aliq == null ? null : new BigDecimal(aliq),
            base, RegimeReconhecimento.COMPETENCIA, PeriodicidadeApuracao.MENSAL, true, null, "DARF", 25, 1,
            AjusteVencimento.ANTECIPA);
    }

    static RegraApuracaoRequest req(String cnpj, String comp, List<TributoRegraApuracaoRequest> tributos) {
        return new RegraApuracaoRequest(cnpj, comp, new BigDecimal("0.08"), new BigDecimal("0.12"),
            new BigDecimal("0.32"), new BigDecimal("0.32"), new BigDecimal("0.10"), 2_000_000L,
            7_800_000_000L, new BigDecimal("0.8"), 1_000L, "teste", null, tributos);
    }

    private static final List<TributoRegraApuracaoRequest> TRIBUTOS_OK = List.of(
        trib(TributoApuracao.IRPJ, "0.15", BaseCalculoApuracao.BASE_PRESUMIDA),
        trib(TributoApuracao.PIS, "0.0065", BaseCalculoApuracao.RECEITA_BRUTA),
        trib(TributoApuracao.CBS, null, BaseCalculoApuracao.SOMA_NOTAS));

    private static RegraApuracao regra(String cnpj, boolean homologado) {
        RegraApuracao r = new RegraApuracao();
        r.setCnpjId(cnpj);
        r.setCompetenciaInicio("2026-01");
        r.setHomologado(homologado);
        return r;
    }

    @Test
    void criar_semTenantESemCnpj_criaPadraoGlobal() {
        login(null);
        when(repo.existsByCnpjIdIsNullAndCompetenciaInicio("2027-01")).thenReturn(false);
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        RegraApuracaoResponse r = service().criar(req(null, "2027-01", TRIBUTOS_OK));

        assertThat(r.global()).isTrue();
        assertThat(r.homologado()).isFalse();
        assertThat(r.tributos()).hasSize(3);
        assertThat(r.atualizadoPor()).isEqualTo("contador@pinsaude.com.br");
    }

    @Test
    void criar_comTenant_ignoraCnpjInformadoEUsaOdoToken() {
        login("12.345.678/0001-90");
        when(repo.existsByCnpjIdAndCompetenciaInicio(CNPJ, "2027-01")).thenReturn(false);
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        RegraApuracaoResponse r = service().criar(req("99999999000199", "2027-01", TRIBUTOS_OK));

        assertThat(r.cnpjId()).isEqualTo(CNPJ);
    }

    @Test
    void criar_mesmaCompetencia_409() {
        login(null);
        when(repo.existsByCnpjIdAndCompetenciaInicio(CNPJ, "2027-01")).thenReturn(true);

        assertThatThrownBy(() -> service().criar(req(CNPJ, "2027-01", TRIBUTOS_OK)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void validar_basePresumidaForaDeIrpjCsll_422() {
        assertThatThrownBy(() -> RegraApuracaoService.validarTributos(List.of(
            trib(TributoApuracao.PIS, "0.0065", BaseCalculoApuracao.BASE_PRESUMIDA))))
            .hasMessageContaining("base presumida");
    }

    @Test
    void validar_somaNotasForaDeCbsIbs_422() {
        assertThatThrownBy(() -> RegraApuracaoService.validarTributos(List.of(
            trib(TributoApuracao.ISS, "0.02", BaseCalculoApuracao.SOMA_NOTAS))))
            .hasMessageContaining("soma das notas");
    }

    @Test
    void validar_semAliquotaForaDaReforma_422() {
        assertThatThrownBy(() -> RegraApuracaoService.validarTributos(List.of(
            trib(TributoApuracao.ISS, null, BaseCalculoApuracao.RECEITA_BRUTA))))
            .hasMessageContaining("informe a alíquota");
    }

    @Test
    void validar_tributoRepetido_422() {
        assertThatThrownBy(() -> RegraApuracaoService.validarTributos(List.of(
            trib(TributoApuracao.PIS, "0.0065", BaseCalculoApuracao.RECEITA_BRUTA),
            trib(TributoApuracao.PIS, "0.0065", BaseCalculoApuracao.RECEITA_BRUTA))))
            .hasMessageContaining("mais de uma vez");
    }

    @Test
    void validar_vencimentoPelaMetade_422() {
        TributoRegraApuracaoRequest t = new TributoRegraApuracaoRequest(TributoApuracao.ISS, true, AplicacaoRegime.AMBOS,
            new BigDecimal("0.02"), BaseCalculoApuracao.RECEITA_BRUTA, RegimeReconhecimento.COMPETENCIA,
            PeriodicidadeApuracao.MENSAL, true, null, null, 10, null, null);
        assertThatThrownBy(() -> RegraApuracaoService.validarTributos(List.of(t)))
            .hasMessageContaining("vencimento");
    }

    @Test
    void atualizar_regraHomologada_voltaParaRascunho() {
        login(null);
        UUID id = UUID.randomUUID();
        RegraApuracao r = regra(CNPJ, true);
        r.setHomologadoPor("x");
        when(repo.findById(id)).thenReturn(Optional.of(r));
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        RegraApuracaoResponse resp = service().atualizar(id, req(null, "2026-01", TRIBUTOS_OK));

        assertThat(resp.homologado()).isFalse();
        assertThat(resp.homologadoPor()).isNull();
    }

    @Test
    void atualizar_globalPorUsuarioDeEmpresa_403() {
        login(CNPJ);
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(regra(null, false)));

        assertThatThrownBy(() -> service().atualizar(id, req(null, "2026-01", TRIBUTOS_OK)))
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void buscar_regraDeOutraEmpresa_404() {
        login(CNPJ);
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(regra("99999999000199", false)));

        assertThatThrownBy(() -> service().buscar(id))
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void homologar_gravaQuemEQuando() {
        login(null);
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(regra(CNPJ, false)));
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        RegraApuracaoResponse r = service().homologar(id);

        assertThat(r.homologado()).isTrue();
        assertThat(r.homologadoPor()).isEqualTo("contador@pinsaude.com.br");
        assertThat(r.homologadoEm()).isNotNull();
    }

    @Test
    void excluir_homologada_409_rascunho_apaga() {
        login(null);
        UUID h = UUID.randomUUID(), d = UUID.randomUUID();
        RegraApuracao rascunho = regra(CNPJ, false);
        when(repo.findById(h)).thenReturn(Optional.of(regra(CNPJ, true)));
        when(repo.findById(d)).thenReturn(Optional.of(rascunho));

        assertThatThrownBy(() -> service().excluir(h))
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        service().excluir(d);
        verify(repo).delete(rascunho);
    }

    @Test
    void vigente_semRegraDaEmpresa_usaGlobal() {
        login(CNPJ);
        RegraApuracao global = regra(null, false);
        when(repo.findFirstByCnpjIdAndCompetenciaInicioLessThanEqualOrderByCompetenciaInicioDesc(CNPJ, "2026-05"))
            .thenReturn(Optional.empty());
        when(repo.findFirstByCnpjIdIsNullAndCompetenciaInicioLessThanEqualOrderByCompetenciaInicioDesc("2026-05"))
            .thenReturn(Optional.of(global));

        assertThat(service().vigente("2026-05", null).global()).isTrue();
    }

    @Test
    void vigente_nenhumaRegra_422() {
        login(null);
        when(repo.findFirstByCnpjIdIsNullAndCompetenciaInicioLessThanEqualOrderByCompetenciaInicioDesc("2025-12"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().vigente("2025-12", null))
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void competenciaInvalida_400() {
        assertThatThrownBy(() -> RegraApuracaoService.validarCompetencia("2026-13"))
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
