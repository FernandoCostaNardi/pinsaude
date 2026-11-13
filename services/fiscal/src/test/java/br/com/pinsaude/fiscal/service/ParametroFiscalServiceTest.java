package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.ParametroFiscal;
import br.com.pinsaude.fiscal.domain.RegimeTributario;
import br.com.pinsaude.fiscal.dto.IbsCbsRequest;
import br.com.pinsaude.fiscal.dto.ParametroFiscalRequest;
import br.com.pinsaude.fiscal.dto.ParametroFiscalResponse;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CBS-14: chave CBS/IBS por empresa + competência, sem mexer em IR/CSLL e travada por notas. */
@ExtendWith(MockitoExtension.class)
class ParametroFiscalServiceTest {

    static final String CNPJ = "11222333000181";
    static final LocalDate JAN_2027 = LocalDate.of(2027, 1, 1);

    @Mock ParametroFiscalRepository repo;
    @Mock NotaFiscalRepository notaRepo;

    ParametroFiscalService service;

    @BeforeEach
    void setUp() {
        service = new ParametroFiscalService(repo, notaRepo);
        autenticar("11.222.333/0001-81");
        lenient().when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(repo.findFirstByCnpjIdAndVigenciaInicioGreaterThanOrderByVigenciaInicioAsc(anyString(), any()))
            .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(String cnpjId) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none")
            .subject("sub-1").claim("preferred_username", "contador@pinsaude.com.br")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (cnpjId != null) b.claim("cnpj_id", cnpjId);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(b.build()));
    }

    private static ParametroFiscal legado(LocalDate vigencia) {
        ParametroFiscal p = new ParametroFiscal();
        p.setCnpjId(CNPJ);
        p.setVigenciaInicio(vigencia);
        p.setAliqIss(new BigDecimal("0.0500"));
        p.setAliqIr(new BigDecimal("0.0480"));
        p.setAliqCsll(new BigDecimal("0.0288"));
        p.setAliqPis(new BigDecimal("0.0065"));
        p.setAliqCofins(new BigDecimal("0.0300"));
        p.setIbsCbsAtivo(false);
        p.setHomologado(true);
        return p;
    }

    private static ParametroFiscal comId(ParametroFiscal p) throws Exception {
        Field f = ParametroFiscal.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(p, UUID.randomUUID());
        return p;
    }

    private static IbsCbsRequest ligar(String competencia) {
        return new IbsCbsRequest(competencia, null, null, null, null);
    }

    // ─── Ligar ────────────────────────────────────────────────────────────────

    @Test
    void ligar_copiaAliquotasDoVigente_semZerarIrCsll_eNasceSemHomologacao() {
        when(repo.findByCnpjIdAndVigenciaInicio(CNPJ, JAN_2027)).thenReturn(Optional.empty());
        when(repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(CNPJ, JAN_2027))
            .thenReturn(Optional.of(legado(LocalDate.of(2026, 1, 1))));

        ParametroFiscalResponse r = service.criarIbsCbs(ligar("2027-01"));

        assertThat(r.ibsCbsAtivo()).isTrue();
        assertThat(r.homologado()).isFalse();
        assertThat(r.vigenciaInicio()).isEqualTo(JAN_2027);
        assertThat(r.aliqIr()).isEqualByComparingTo("0.0480");
        assertThat(r.aliqCsll()).isEqualByComparingTo("0.0288");
        assertThat(r.aliqIss()).isEqualByComparingTo("0.0500");
        assertThat(r.aliqPis()).isEqualByComparingTo("0.0065");
        assertThat(r.aliqCofins()).isEqualByComparingTo("0.0300");
        assertThat(r.aliqIbsCbs()).isNull();
    }

    @Test
    void ligar_aliqIssInformada_sobrescreveSoOIss() {
        when(repo.findByCnpjIdAndVigenciaInicio(CNPJ, JAN_2027)).thenReturn(Optional.empty());
        when(repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(CNPJ, JAN_2027))
            .thenReturn(Optional.of(legado(LocalDate.of(2026, 1, 1))));

        ParametroFiscalResponse r = service.criarIbsCbs(
            new IbsCbsRequest("2027-01", null, null, new BigDecimal("0.0200"), null));

        assertThat(r.aliqIss()).isEqualByComparingTo("0.0200");
        assertThat(r.aliqIr()).isEqualByComparingTo("0.0480");
    }

    @Test
    void ligar_semParametroAnterior_422() {
        when(repo.findByCnpjIdAndVigenciaInicio(CNPJ, JAN_2027)).thenReturn(Optional.empty());
        when(repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(CNPJ, JAN_2027))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarIbsCbs(ligar("2027-01")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void ligar_naMesmaVigenciaDeUmParametroLegado_ligaNoProprioRegistroEMantemAliquotas() {
        ParametroFiscal existente = legado(JAN_2027);
        existente.setHomologadoPor("antigo");
        existente.setHomologadoEm(OffsetDateTime.now());
        when(repo.findByCnpjIdAndVigenciaInicio(CNPJ, JAN_2027)).thenReturn(Optional.of(existente));

        ParametroFiscalResponse r = service.criarIbsCbs(ligar("2027-01"));

        assertThat(r.ibsCbsAtivo()).isTrue();
        assertThat(r.aliqIr()).isEqualByComparingTo("0.0480");
        assertThat(r.homologado()).isFalse();
        assertThat(r.homologadoPor()).isNull();
        assertThat(r.homologadoEm()).isNull();
    }

    @Test
    void ligar_jaLigada_409() {
        ParametroFiscal existente = legado(JAN_2027);
        existente.setIbsCbsAtivo(true);
        when(repo.findByCnpjIdAndVigenciaInicio(CNPJ, JAN_2027)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.criarIbsCbs(ligar("2027-01")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void ligar_comNotaJaEmitidaNoPeriodo_422_eNaoGrava() {
        when(repo.findByCnpjIdAndVigenciaInicio(CNPJ, JAN_2027)).thenReturn(Optional.empty());
        when(notaRepo.existeNotaNoPeriodo(eq(CNPJ), eq("2027-01"), eq("9999-12"), anyCollection()))
            .thenReturn(true);

        assertThatThrownBy(() -> service.criarIbsCbs(ligar("2027-01")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(repo, never()).save(any());
    }

    @Test
    void ligar_periodoTerminaNoProximoParametro() {
        when(repo.findByCnpjIdAndVigenciaInicio(CNPJ, JAN_2027)).thenReturn(Optional.empty());
        when(repo.findFirstByCnpjIdAndVigenciaInicioGreaterThanOrderByVigenciaInicioAsc(CNPJ, JAN_2027))
            .thenReturn(Optional.of(legado(LocalDate.of(2027, 6, 1))));
        when(repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(CNPJ, JAN_2027))
            .thenReturn(Optional.of(legado(LocalDate.of(2026, 1, 1))));

        service.criarIbsCbs(ligar("2027-01"));

        verify(notaRepo).existeNotaNoPeriodo(eq(CNPJ), eq("2027-01"), eq("2027-06"), anyCollection());
    }

    @Test
    void ligar_semTenant_400() {
        autenticar(null);
        assertThatThrownBy(() -> service.criarIbsCbs(ligar("2027-01")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ─── Desligar ─────────────────────────────────────────────────────────────

    @Test
    void desligar_semNotasNoRegimeNovo_desligaEPerdeHomologacao() throws Exception {
        ParametroFiscal p = comId(legado(JAN_2027));
        p.setIbsCbsAtivo(true);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));

        ParametroFiscalResponse r = service.desligarIbsCbs(p.getId());

        assertThat(r.ibsCbsAtivo()).isFalse();
        assertThat(r.homologado()).isFalse();
        assertThat(r.aliqIr()).isEqualByComparingTo("0.0480");
        verify(notaRepo).existeNotaNoPeriodoNoRegime(eq(CNPJ), eq("2027-01"), eq("9999-12"),
            anyCollection(), eq(RegimeTributario.IBS_CBS));
    }

    @Test
    void desligar_comNotaNoRegimeNovo_422_eNaoGrava() throws Exception {
        ParametroFiscal p = comId(legado(JAN_2027));
        p.setIbsCbsAtivo(true);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));
        when(notaRepo.existeNotaNoPeriodoNoRegime(eq(CNPJ), eq("2027-01"), eq("9999-12"),
            anyCollection(), eq(RegimeTributario.IBS_CBS))).thenReturn(true);

        assertThatThrownBy(() -> service.desligarIbsCbs(p.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(repo, never()).save(any());
    }

    @Test
    void desligar_jaDesligada_409() throws Exception {
        ParametroFiscal p = comId(legado(JAN_2027));
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.desligarIbsCbs(p.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void desligar_parametroDeOutraEmpresa_naoEncontrado() throws Exception {
        ParametroFiscal p = comId(legado(JAN_2027));
        p.setCnpjId("99888777000166");
        p.setIbsCbsAtivo(true);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.desligarIbsCbs(p.getId()))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── Homologar ────────────────────────────────────────────────────────────

    @Test
    void homologar_chaveLigadaSemNotas_gravaQuemEQuando() throws Exception {
        ParametroFiscal p = comId(legado(JAN_2027));
        p.setIbsCbsAtivo(true);
        p.setHomologado(false);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));

        ParametroFiscalResponse r = service.homologar(p.getId());

        assertThat(r.homologado()).isTrue();
        assertThat(r.homologadoPor()).isEqualTo("contador@pinsaude.com.br");
        assertThat(r.homologadoEm()).isNotNull();
    }

    @Test
    void homologar_chaveLigadaComNotaJaEmitida_422() throws Exception {
        ParametroFiscal p = comId(legado(JAN_2027));
        p.setIbsCbsAtivo(true);
        p.setHomologado(false);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));
        when(notaRepo.existeNotaNoPeriodo(eq(CNPJ), eq("2027-01"), eq("9999-12"), anyCollection()))
            .thenReturn(true);

        assertThatThrownBy(() -> service.homologar(p.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void homologar_jaHomologado_ehIdempotente() throws Exception {
        ParametroFiscal p = comId(legado(JAN_2027));
        p.setIbsCbsAtivo(true);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));

        assertThat(service.homologar(p.getId()).homologado()).isTrue();
        verify(repo, never()).save(any());
    }

    @Test
    void homologar_parametroLegado_naoConsultaNotas() throws Exception {
        ParametroFiscal p = comId(legado(JAN_2027));
        p.setHomologado(false);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));

        service.homologar(p.getId());

        verify(notaRepo, never()).existeNotaNoPeriodo(anyString(), anyString(), anyString(), anyCollection());
    }

    // ─── Criação genérica ─────────────────────────────────────────────────────

    private static ParametroFiscalRequest generico(String competencia, boolean ibsCbs) {
        return new ParametroFiscalRequest(competencia,
            new BigDecimal("0.05"), new BigDecimal("0.048"), new BigDecimal("0.0288"),
            new BigDecimal("0.0065"), new BigDecimal("0.03"),
            ibsCbs, ibsCbs ? new BigDecimal("0.01") : null, ibsCbs ? new BigDecimal("0.6") : null, null);
    }

    @Test
    void criarGenerico_desligandoDepoisDeRegimeNovoComNotas_422() {
        ParametroFiscal anterior = legado(JAN_2027);
        anterior.setIbsCbsAtivo(true);
        LocalDate mar = LocalDate.of(2027, 3, 1);
        when(repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(CNPJ, mar))
            .thenReturn(Optional.of(anterior));
        when(notaRepo.existeNotaNoPeriodoNoRegime(eq(CNPJ), eq("2027-03"), eq("9999-12"),
            anyCollection(), eq(RegimeTributario.IBS_CBS))).thenReturn(true);

        assertThatThrownBy(() -> service.criar(generico("2027-03", false)))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void criarGenerico_ligandoComNotaNoPeriodo_422() {
        when(notaRepo.existeNotaNoPeriodo(eq(CNPJ), eq("2027-01"), eq("9999-12"), anyCollection()))
            .thenReturn(true);

        assertThatThrownBy(() -> service.criar(generico("2027-01", true)))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void criarGenerico_legadoSemRegimeNovoAntes_gravaSemConsultarNotas() {
        when(repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(CNPJ, JAN_2027))
            .thenReturn(Optional.of(legado(LocalDate.of(2026, 1, 1))));

        assertThat(service.criar(generico("2027-01", false)).ibsCbsAtivo()).isFalse();
        verify(notaRepo, never()).existeNotaNoPeriodoNoRegime(anyString(), anyString(), anyString(),
            anyCollection(), any());
    }
}
