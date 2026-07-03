package br.com.pinsaude.faturamento.tomador;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.TipoTomador;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.dto.TomadorRequest;
import br.com.pinsaude.faturamento.dto.TomadorResponse;
import br.com.pinsaude.faturamento.port.ConsultaCnpjPort;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import br.com.pinsaude.faturamento.service.CryptoService;
import br.com.pinsaude.faturamento.service.TomadorService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TomadorServiceTest {

    @Mock TomadorRepository repo;
    @Mock CryptoService crypto;
    @Mock ConsultaCnpjPort consultaCnpjPort;

    @InjectMocks TomadorService service;

    private static final String CNPJ_VALIDO   = "11222333000181";
    private static final String CNPJ_INVALIDO = "11111111111111";
    private static final String CPF_VALIDO    = "52998224725";
    private static final String TENANT        = "12345678000195";

    @BeforeEach
    void setUpSecurityContext() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("cnpj_id")).thenReturn(TENANT);
        var auth = new JwtAuthenticationToken(jwt,
            List.of(new SimpleGrantedAuthority("ROLE_operacao")));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
    }

    // ─── buscar ──────────────────────────────────────────────────────────────

    @Test
    void buscar_semFiltro_retornaTodos() {
        Tomador t = tomadorFixture(TENANT);
        when(repo.findAll()).thenReturn(List.of(t));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);

        List<TomadorResponse> result = service.buscar(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).razaoSocialNome()).isEqualTo("Hospital Teste");
    }

    @Test
    void buscar_porNomeParcial_retornaFiltrado() {
        Tomador t = tomadorFixture(TENANT);
        when(repo.findByRazaoSocialNomeContainingIgnoreCase("hosp")).thenReturn(List.of(t));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);

        List<TomadorResponse> result = service.buscar("hosp");

        assertThat(result).hasSize(1);
    }

    @Test
    void buscar_porCnpjDigitos_decriptaEFiltra() {
        Tomador t = tomadorFixture(TENANT);
        when(repo.findAll()).thenReturn(List.of(t));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);

        // Busca pelos primeiros 8 dígitos do CNPJ
        List<TomadorResponse> result = service.buscar("11222333");

        assertThat(result).hasSize(1);
    }

    // ─── criar ───────────────────────────────────────────────────────────────

    @Test
    void criar_comCnpjValido_salvaRetornaResponse() {
        TomadorRequest req = new TomadorRequest(
            "HOSPITAL", CNPJ_VALIDO, "Hospital Novo",
            null, null, null, false, false, null, null, null, null, null, null, null);

        Tomador saved = tomadorFixture(TENANT);
        when(crypto.encrypt(CNPJ_VALIDO)).thenReturn(new byte[]{1, 2, 3});
        when(repo.save(any())).thenReturn(saved);
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);

        TomadorResponse result = service.criar(req);

        assertThat(result).isNotNull();
        assertThat(result.tipo()).isEqualTo("HOSPITAL");
        verify(repo).save(any(Tomador.class));
    }

    @Test
    void criar_comCnpjInvalido_lanca400() {
        TomadorRequest req = new TomadorRequest(
            "CLINICA", CNPJ_INVALIDO, "Clínica Inválida",
            null, null, null, false, false, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CNPJ inválido");
    }

    @Test
    void criar_pacientePfComCpfValido_salva() {
        TomadorRequest req = new TomadorRequest(
            "PACIENTE_PF", CPF_VALIDO, "João da Silva",
            null, null, null, false, false, null, null, null, null, null, null, null);

        Tomador saved = tomadorPfFixture(TENANT);
        when(crypto.encrypt(CPF_VALIDO)).thenReturn(new byte[]{4, 5, 6});
        when(repo.save(any())).thenReturn(saved);
        when(crypto.decrypt(any())).thenReturn(CPF_VALIDO);

        TomadorResponse result = service.criar(req);

        assertThat(result.tipo()).isEqualTo("PACIENTE_PF");
        verify(repo).save(any(Tomador.class));
    }

    @Test
    void criar_pacientePfComCpfInvalido_lanca400() {
        TomadorRequest req = new TomadorRequest(
            "PACIENTE_PF", "11111111111", "Nome",
            null, null, null, false, false, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CPF inválido");
    }

    @Test
    void criar_tipoInvalido_lanca400() {
        TomadorRequest req = new TomadorRequest(
            "INVALIDO", CNPJ_VALIDO, "Teste",
            null, null, null, false, false, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Tipo inválido");
    }

    // ─── buscarPorId ─────────────────────────────────────────────────────────

    @Test
    void buscarPorId_naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(id))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── atualizar ───────────────────────────────────────────────────────────

    @Test
    void atualizar_comCnpjValido_salva() {
        UUID id = UUID.randomUUID();
        Tomador existente = tomadorFixture(TENANT);
        TomadorRequest req = new TomadorRequest(
            "OPERADORA", CNPJ_VALIDO, "Operadora Nova",
            null, null, null, true, false, null, null, null, null, null, null, null);

        when(repo.findById(id)).thenReturn(Optional.of(existente));
        when(crypto.encrypt(CNPJ_VALIDO)).thenReturn(new byte[]{7, 8, 9});
        when(repo.save(any())).thenReturn(existente);
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);

        TomadorResponse result = service.atualizar(id, req);

        assertThat(result).isNotNull();
        verify(repo).save(existente);
    }

    // ─── consultarReceita ─────────────────────────────────────────────────────

    @Test
    void consultarReceita_cnpjInvalido_lanca400() {
        assertThatThrownBy(() -> service.consultarReceita(CNPJ_INVALIDO))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CNPJ inválido");
    }

    @Test
    void consultarReceita_cnpjValido_delegaAoPort() {
        when(consultaCnpjPort.consultar(CNPJ_VALIDO)).thenReturn(Optional.empty());

        Optional<br.com.pinsaude.faturamento.dto.ReceitaFederalResponse> result =
            service.consultarReceita(CNPJ_VALIDO);

        assertThat(result).isEmpty();
        verify(consultaCnpjPort).consultar(CNPJ_VALIDO);
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private Tomador tomadorFixture(String tenant) {
        Tomador t = new Tomador();
        t.setId(UUID.randomUUID());
        t.setCnpjIdTenant(tenant);
        t.setTipo(TipoTomador.HOSPITAL);
        t.setCnpjCpfTomadorCriptografado(new byte[]{1, 2, 3});
        t.setRazaoSocialNome("Hospital Teste");
        t.setIndicadorRetencaoFederal(false);
        t.setIndicadorRetencaoIss(false);
        return t;
    }

    private Tomador tomadorPfFixture(String tenant) {
        Tomador t = new Tomador();
        t.setId(UUID.randomUUID());
        t.setCnpjIdTenant(tenant);
        t.setTipo(TipoTomador.PACIENTE_PF);
        t.setCnpjCpfTomadorCriptografado(new byte[]{4, 5, 6});
        t.setRazaoSocialNome("João da Silva");
        t.setIndicadorRetencaoFederal(false);
        t.setIndicadorRetencaoIss(false);
        return t;
    }
}
