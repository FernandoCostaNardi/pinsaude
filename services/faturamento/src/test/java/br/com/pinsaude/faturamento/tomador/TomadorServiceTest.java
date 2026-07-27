package br.com.pinsaude.faturamento.tomador;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.MedicoTomador;
import br.com.pinsaude.faturamento.domain.TipoTomador;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.dto.MedicoTomadorRequest;
import br.com.pinsaude.faturamento.dto.TomadorRequest;
import br.com.pinsaude.faturamento.dto.TomadorResponse;
import br.com.pinsaude.faturamento.port.ConsultaCnpjPort;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import br.com.pinsaude.faturamento.repository.TomadorAliquotaRepository;
import br.com.pinsaude.faturamento.repository.TomadorCnaeRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoFaturamentoRepository;
import br.com.pinsaude.faturamento.repository.TomadorModalidadeRepository;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoOperacionalRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoRepository;
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

import java.util.Collections;
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
    @Mock TomadorAliquotaRepository aliquotaRepo;
    @Mock TomadorCnaeRepository cnaeRepo;
    @Mock TomadorServicoRepository servicoVinculoRepo;
    @Mock ServicoRepository servicoRepo;
    @Mock TomadorGrupoFaturamentoRepository grupoRepo;
    @Mock TomadorModalidadeRepository modalidadeRepo;
    @Mock TomadorServicoOperacionalRepository servicoOperacionalRepo;
    @Mock MedicoTomadorRepository medicoTomadorRepo;

    @InjectMocks TomadorService service;

    private static final String CNPJ_VALIDO   = "11222333000181";
    private static final String CNPJ_ALT      = "45723174000110";
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

        when(aliquotaRepo.findByTomadorId(any())).thenReturn(Collections.emptyList());
        when(cnaeRepo.findByTomadorId(any())).thenReturn(Collections.emptyList());
        when(servicoVinculoRepo.findByTomadorId(any())).thenReturn(Collections.emptyList());
    }

    // ─── buscar ──────────────────────────────────────────────────────────────

    @Test
    void buscar_semFiltro_retornaTodos() {
        Tomador t = tomadorFixture(TENANT);
        when(repo.findAll()).thenReturn(List.of(t));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);

        List<TomadorResponse> result = service.buscar(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).razaoSocialNome()).isEqualTo("Hospital Teste");
    }

    @Test
    void buscar_porNomeParcial_retornaFiltrado() {
        Tomador t = tomadorFixture(TENANT);
        when(repo.findByRazaoSocialNomeContainingIgnoreCase("hosp")).thenReturn(List.of(t));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);

        List<TomadorResponse> result = service.buscar("hosp", null);

        assertThat(result).hasSize(1);
    }

    @Test
    void buscar_porCnpjDigitos_decriptaEFiltra() {
        Tomador t = tomadorFixture(TENANT);
        when(repo.findAll()).thenReturn(List.of(t));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);

        List<TomadorResponse> result = service.buscar("11222333", null);

        assertThat(result).hasSize(1);
    }

    @Test
    void buscar_comMedicoId_filtraSoTomadoresAlocados() {
        Tomador alocado = tomadorFixture(TENANT);
        Tomador naoAlocado = tomadorFixture(TENANT);
        UUID medicoId = UUID.randomUUID();
        when(repo.findAll()).thenReturn(List.of(alocado, naoAlocado));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);
        when(medicoTomadorRepo.findTomadorIdsByMedicoId(medicoId)).thenReturn(List.of(alocado.getId()));

        List<TomadorResponse> result = service.buscar(null, medicoId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(alocado.getId());
    }

    @Test
    void buscar_comMedicoIdSemAlocacao_retornaVazio() {
        Tomador t = tomadorFixture(TENANT);
        UUID medicoId = UUID.randomUUID();
        when(repo.findAll()).thenReturn(List.of(t));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);
        when(medicoTomadorRepo.findTomadorIdsByMedicoId(medicoId)).thenReturn(List.of());

        List<TomadorResponse> result = service.buscar(null, medicoId);

        assertThat(result).isEmpty();
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
    void criar_comCnpjDuplicado_lanca409() {
        Tomador existente = tomadorFixture(TENANT);
        when(repo.findAll()).thenReturn(List.of(existente));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);

        TomadorRequest req = new TomadorRequest(
            "CLINICA", CNPJ_VALIDO, "Outra Clínica",
            null, null, null, false, false, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Já existe um tomador");
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

    @Test
    void atualizar_comCnpjDuplicadoDeOutroTomador_lanca409() {
        UUID id = UUID.randomUUID();
        // existente tem um CNPJ atual diferente; a atualização quer mudá-lo para CNPJ_VALIDO,
        // que já pertence a "outro" tomador → deve lançar 409.
        byte[] encExistente = {1, 2, 3};
        byte[] encOutro     = {9, 9, 9};
        Tomador existente = tomadorFixture(TENANT);
        existente.setId(id);
        existente.setCnpjCpfTomadorCriptografado(encExistente);

        Tomador outro = tomadorFixture(TENANT);
        outro.setCnpjCpfTomadorCriptografado(encOutro);

        when(repo.findById(id)).thenReturn(Optional.of(existente));
        when(repo.findAll()).thenReturn(List.of(existente, outro));
        when(crypto.decrypt(encExistente)).thenReturn(CNPJ_ALT); // CNPJ atual do existente
        when(crypto.decrypt(encOutro)).thenReturn(CNPJ_VALIDO);  // outro já usa o CNPJ pretendido

        TomadorRequest req = new TomadorRequest(
            "OPERADORA", CNPJ_VALIDO, "Operadora Nova",
            null, null, null, true, false, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(id, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Já existe um tomador");
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

    // ─── serviços por tomador ─────────────────────────────────────────────────

    @Test
    void adicionarServico_servicoInexistente_lanca400() {
        UUID tomadorId = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(servicoRepo.findById(servicoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adicionarServico(tomadorId,
                new br.com.pinsaude.faturamento.dto.TomadorServicoRequest(servicoId)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Serviço não encontrado");
    }

    @Test
    void adicionarServico_duplicado_lanca409() {
        UUID tomadorId = UUID.randomUUID();
        br.com.pinsaude.faturamento.domain.Servico servico = servicoFixture();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(servicoRepo.findById(servico.getId())).thenReturn(Optional.of(servico));
        when(servicoVinculoRepo.existsByTomadorIdAndServicoId(tomadorId, servico.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.adicionarServico(tomadorId,
                new br.com.pinsaude.faturamento.dto.TomadorServicoRequest(servico.getId())))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("já cadastrado");
    }

    @Test
    void adicionarServico_valido_salvaRetornaResponse() {
        UUID tomadorId = UUID.randomUUID();
        br.com.pinsaude.faturamento.domain.Servico servico = servicoFixture();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(servicoRepo.findById(servico.getId())).thenReturn(Optional.of(servico));
        when(servicoVinculoRepo.existsByTomadorIdAndServicoId(tomadorId, servico.getId())).thenReturn(false);
        when(servicoVinculoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.adicionarServico(tomadorId,
                new br.com.pinsaude.faturamento.dto.TomadorServicoRequest(servico.getId()));

        assertThat(result.servicoId()).isEqualTo(servico.getId());
        assertThat(result.codigoLc116()).isEqualTo("4.01");
        verify(servicoVinculoRepo).save(any());
    }

    @Test
    void removerServico_deOutroTomador_lanca404() {
        UUID tomadorId = UUID.randomUUID();
        UUID vinculoId = UUID.randomUUID();
        var vinculo = new br.com.pinsaude.faturamento.domain.TomadorServico();
        vinculo.setTomadorId(UUID.randomUUID()); // pertence a outro tomador
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(servicoVinculoRepo.findById(vinculoId)).thenReturn(Optional.of(vinculo));

        assertThatThrownBy(() -> service.removerServico(tomadorId, vinculoId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Serviço não encontrado");
    }

    // ─── médicos alocados ao tomador ─────────────────────────────────────────

    @Test
    void listarMedicos_tomadorInexistente_lanca404() {
        UUID tomadorId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarMedicos(tomadorId))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listarMedicos_retornaVinculosDoTomador() {
        UUID tomadorId = UUID.randomUUID();
        MedicoTomador mt = new MedicoTomador();
        mt.setTomadorId(tomadorId);
        mt.setMedicoId(UUID.randomUUID());
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(medicoTomadorRepo.findByTomadorId(tomadorId)).thenReturn(List.of(mt));

        var result = service.listarMedicos(tomadorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).medicoId()).isEqualTo(mt.getMedicoId());
    }

    @Test
    void adicionarMedico_duplicado_lanca409() {
        UUID tomadorId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(true);

        assertThatThrownBy(() -> service.adicionarMedico(tomadorId, new MedicoTomadorRequest(medicoId)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("já está alocado");
    }

    @Test
    void adicionarMedico_valido_salvaRetornaResponse() {
        UUID tomadorId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(false);
        when(medicoTomadorRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.adicionarMedico(tomadorId, new MedicoTomadorRequest(medicoId));

        assertThat(result.tomadorId()).isEqualTo(tomadorId);
        assertThat(result.medicoId()).isEqualTo(medicoId);
        verify(medicoTomadorRepo).save(any());
    }

    @Test
    void removerMedico_naoAlocado_lanca404() {
        UUID tomadorId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(false);

        assertThatThrownBy(() -> service.removerMedico(tomadorId, medicoId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não está alocado");
    }

    @Test
    void removerMedico_alocado_removeComSucesso() {
        UUID tomadorId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(true);

        service.removerMedico(tomadorId, medicoId);

        verify(medicoTomadorRepo).deleteByTomadorIdAndMedicoId(tomadorId, medicoId);
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private br.com.pinsaude.faturamento.domain.Servico servicoFixture() {
        var s = new br.com.pinsaude.faturamento.domain.Servico();
        s.setId(UUID.randomUUID());
        s.setCodigoLc116("4.01");
        s.setDescricaoPadrao("Medicina e biomedicina");
        return s;
    }

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
