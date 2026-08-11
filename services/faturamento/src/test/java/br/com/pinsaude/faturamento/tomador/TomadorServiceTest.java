package br.com.pinsaude.faturamento.tomador;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.MedicoTomador;
import br.com.pinsaude.faturamento.domain.TipoTomador;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.domain.TomadorEmpresa;
import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;
import br.com.pinsaude.faturamento.dto.MedicoTomadorRequest;
import br.com.pinsaude.faturamento.dto.TomadorEmpresaRequest;
import br.com.pinsaude.faturamento.dto.TomadorOcorrenciaRequest;
import br.com.pinsaude.faturamento.dto.TomadorRequest;
import br.com.pinsaude.faturamento.dto.TomadorResponse;
import br.com.pinsaude.faturamento.port.ConsultaCnpjPort;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import br.com.pinsaude.faturamento.repository.TomadorAliquotaRepository;
import br.com.pinsaude.faturamento.repository.TomadorCnaeRepository;
import br.com.pinsaude.faturamento.repository.TomadorEmpresaRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoFaturamentoRepository;
import br.com.pinsaude.faturamento.repository.TomadorModalidadeRepository;
import br.com.pinsaude.faturamento.repository.TomadorOcorrenciaRepository;
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
    @Mock TomadorEmpresaRepository empresaTomadorRepo;
    @Mock TomadorOcorrenciaRepository ocorrenciaRepo;

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
        when(empresaTomadorRepo.findByTomadorId(any())).thenReturn(Collections.emptyList());
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
    void buscar_tomadorComGrupoFaturamentoAtivo_retornaTemGrupoFaturamentoTrue() {
        Tomador t = tomadorFixture(TENANT);
        when(repo.findAll()).thenReturn(List.of(t));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);
        when(grupoRepo.existsByTomadorIdAndAtivoTrue(t.getId())).thenReturn(true);

        List<TomadorResponse> result = service.buscar(null, null);

        assertThat(result.get(0).temGrupoFaturamento()).isTrue();
    }

    @Test
    void buscar_tomadorSemGrupoFaturamento_retornaTemGrupoFaturamentoFalse() {
        Tomador t = tomadorFixture(TENANT);
        when(repo.findAll()).thenReturn(List.of(t));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);
        when(grupoRepo.existsByTomadorIdAndAtivoTrue(t.getId())).thenReturn(false);

        List<TomadorResponse> result = service.buscar(null, null);

        assertThat(result.get(0).temGrupoFaturamento()).isFalse();
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

    // ─── empresas Pin vinculadas ao tomador (PINSAUDE-13.12) ──────────────────

    @Test
    void listarEmpresas_tomadorInexistente_lanca404() {
        UUID tomadorId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarEmpresas(tomadorId))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listarEmpresas_retornaVinculosDoTomador() {
        UUID tomadorId = UUID.randomUUID();
        TomadorEmpresa te = new TomadorEmpresa();
        te.setTomadorId(tomadorId);
        te.setEmpresaId(UUID.randomUUID());
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(empresaTomadorRepo.findByTomadorId(tomadorId)).thenReturn(List.of(te));

        var result = service.listarEmpresas(tomadorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).empresaId()).isEqualTo(te.getEmpresaId());
    }

    @Test
    void adicionarEmpresa_duplicado_lanca409() {
        UUID tomadorId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(empresaTomadorRepo.existsByTomadorIdAndEmpresaId(tomadorId, empresaId)).thenReturn(true);

        assertThatThrownBy(() -> service.adicionarEmpresa(tomadorId, new TomadorEmpresaRequest(empresaId)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("já está vinculada");
    }

    @Test
    void adicionarEmpresa_valido_salvaRetornaResponse() {
        UUID tomadorId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(empresaTomadorRepo.existsByTomadorIdAndEmpresaId(tomadorId, empresaId)).thenReturn(false);
        when(empresaTomadorRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.adicionarEmpresa(tomadorId, new TomadorEmpresaRequest(empresaId));

        assertThat(result.tomadorId()).isEqualTo(tomadorId);
        assertThat(result.empresaId()).isEqualTo(empresaId);
        verify(empresaTomadorRepo).save(any());
    }

    @Test
    void removerEmpresa_naoVinculada_lanca404() {
        UUID tomadorId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(empresaTomadorRepo.existsByTomadorIdAndEmpresaId(tomadorId, empresaId)).thenReturn(false);

        assertThatThrownBy(() -> service.removerEmpresa(tomadorId, empresaId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não está vinculada");
    }

    @Test
    void removerEmpresa_vinculada_removeComSucesso() {
        UUID tomadorId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(empresaTomadorRepo.existsByTomadorIdAndEmpresaId(tomadorId, empresaId)).thenReturn(true);

        service.removerEmpresa(tomadorId, empresaId);

        verify(empresaTomadorRepo).deleteByTomadorIdAndEmpresaId(tomadorId, empresaId);
    }

    @Test
    void buscar_comEmpresaId_filtraSoTomadoresVinculados() {
        Tomador vinculado = tomadorFixture(TENANT);
        Tomador naoVinculado = tomadorFixture(TENANT);
        UUID empresaId = UUID.randomUUID();
        when(repo.findAll()).thenReturn(List.of(vinculado, naoVinculado));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);
        when(empresaTomadorRepo.findTomadorIdsByEmpresaId(empresaId)).thenReturn(List.of(vinculado.getId()));

        List<TomadorResponse> result = service.buscar(null, null, empresaId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(vinculado.getId());
    }

    @Test
    void buscar_semFiltro_incluiEmpresasVinculadas() {
        Tomador t = tomadorFixture(TENANT);
        TomadorEmpresa te = new TomadorEmpresa();
        te.setTomadorId(t.getId());
        te.setEmpresaId(UUID.randomUUID());
        when(repo.findAll()).thenReturn(List.of(t));
        when(crypto.decrypt(any())).thenReturn(CNPJ_VALIDO);
        when(empresaTomadorRepo.findByTomadorId(t.getId())).thenReturn(List.of(te));

        List<TomadorResponse> result = service.buscar(null, null);

        assertThat(result.get(0).empresas()).hasSize(1);
        assertThat(result.get(0).empresas().get(0).empresaId()).isEqualTo(te.getEmpresaId());
    }

    // ─── ocorrências pré-cadastradas com valor (PINSAUDE-13.19.5) ──────────────

    @Test
    void listarOcorrencias_retornaCatalogoDoTomador() {
        UUID tomadorId = UUID.randomUUID();
        TomadorOcorrencia o = ocorrenciaFixture(tomadorId, "PERCENTUAL", new java.math.BigDecimal("10"), null);
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(ocorrenciaRepo.findByTomadorIdOrderByNomeAsc(tomadorId)).thenReturn(List.of(o));

        var result = service.listarOcorrencias(tomadorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Feriado");
    }

    @Test
    void criarOcorrencia_percentual_salvaComValorPercentual() {
        UUID tomadorId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(ocorrenciaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new TomadorOcorrenciaRequest("Feriado", "PERCENTUAL", new java.math.BigDecimal("10"), null, true);
        var result = service.criarOcorrencia(tomadorId, req);

        assertThat(result.tipoValor()).isEqualTo("PERCENTUAL");
        assertThat(result.valorPercentual()).isEqualByComparingTo("10");
        assertThat(result.valorCentavos()).isNull();
    }

    @Test
    void criarOcorrencia_percentualSemValor_lanca422() {
        UUID tomadorId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));

        var req = new TomadorOcorrenciaRequest("Feriado", "PERCENTUAL", null, null, true);

        assertThatThrownBy(() -> service.criarOcorrencia(tomadorId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Valor percentual é obrigatório");
    }

    @Test
    void criarOcorrencia_fixo_salvaComValorCentavos() {
        UUID tomadorId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(ocorrenciaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new TomadorOcorrenciaRequest("Ajuda de custo", "FIXO", null, 5000L, true);
        var result = service.criarOcorrencia(tomadorId, req);

        assertThat(result.tipoValor()).isEqualTo("FIXO");
        assertThat(result.valorCentavos()).isEqualTo(5000L);
    }

    @Test
    void criarOcorrencia_fixoSemValor_lanca422() {
        UUID tomadorId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));

        var req = new TomadorOcorrenciaRequest("Ajuda de custo", "FIXO", null, null, true);

        assertThatThrownBy(() -> service.criarOcorrencia(tomadorId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Valor fixo é obrigatório");
    }

    @Test
    void criarOcorrencia_percentualComFixoExtra_salvaAmbosOsCampos() {
        UUID tomadorId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(ocorrenciaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // "e/ou": PERCENTUAL pode ter um valor fixo extra combinado
        var req = new TomadorOcorrenciaRequest("Feriado + ajuda", "PERCENTUAL", new java.math.BigDecimal("10"), 5000L, true);
        var result = service.criarOcorrencia(tomadorId, req);

        assertThat(result.valorPercentual()).isEqualByComparingTo("10");
        assertThat(result.valorCentavos()).isEqualTo(5000L);
    }

    @Test
    void criarOcorrencia_semValor_zeraAmbosOsCampos() {
        UUID tomadorId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(ocorrenciaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new TomadorOcorrenciaRequest("Observação livre", "SEM_VALOR", new java.math.BigDecimal("10"), 5000L, true);
        var result = service.criarOcorrencia(tomadorId, req);

        assertThat(result.tipoValor()).isEqualTo("SEM_VALOR");
        assertThat(result.valorPercentual()).isNull();
        assertThat(result.valorCentavos()).isNull();
    }

    @Test
    void atualizarOcorrencia_inexistente_lanca404() {
        UUID tomadorId = UUID.randomUUID();
        UUID ocorrenciaId = UUID.randomUUID();
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(ocorrenciaRepo.findById(ocorrenciaId)).thenReturn(Optional.empty());

        var req = new TomadorOcorrenciaRequest("Feriado", "SEM_VALOR", null, null, true);

        assertThatThrownBy(() -> service.atualizarOcorrencia(tomadorId, ocorrenciaId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não encontrada");
    }

    @Test
    void removerOcorrencia_existente_deletaComSucesso() {
        UUID tomadorId = UUID.randomUUID();
        TomadorOcorrencia o = ocorrenciaFixture(tomadorId, "SEM_VALOR", null, null);
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(ocorrenciaRepo.findById(o.getId())).thenReturn(Optional.of(o));

        service.removerOcorrencia(tomadorId, o.getId());

        verify(ocorrenciaRepo).delete(o);
    }

    @Test
    void removerOcorrencia_deOutroTomador_lanca404() {
        UUID tomadorId = UUID.randomUUID();
        UUID outroTomadorId = UUID.randomUUID();
        TomadorOcorrencia o = ocorrenciaFixture(outroTomadorId, "SEM_VALOR", null, null);
        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomadorFixture(TENANT)));
        when(ocorrenciaRepo.findById(o.getId())).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.removerOcorrencia(tomadorId, o.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não encontrada");
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private TomadorOcorrencia ocorrenciaFixture(UUID tomadorId, String tipoValor,
                                                java.math.BigDecimal valorPercentual, Long valorCentavos) {
        TomadorOcorrencia o = new TomadorOcorrencia();
        try {
            var f = TomadorOcorrencia.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(o, UUID.randomUUID());
        } catch (Exception ignored) {}
        o.setTomadorId(tomadorId);
        o.setNome("Feriado");
        o.setTipoValor(tipoValor);
        o.setValorPercentual(valorPercentual);
        o.setValorCentavos(valorCentavos);
        o.setAtivo(true);
        return o;
    }

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
