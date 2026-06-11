package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.Empresa;
import br.com.pinsaude.onboarding.domain.RegimeTributario;
import br.com.pinsaude.onboarding.dto.EmpresaPageResponse;
import br.com.pinsaude.onboarding.dto.EmpresaRequest;
import br.com.pinsaude.onboarding.dto.EmpresaResponse;
import br.com.pinsaude.onboarding.repository.EmpresaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    @Mock
    private EmpresaRepository repository;

    @InjectMocks
    private EmpresaService service;

    private static final String CNPJ_A = "11.222.333/0001-81";
    private static final String CNPJ_B = "22.333.444/0001-81";
    private static final UUID EMPRESA_ID = UUID.randomUUID();

    private Empresa empresaAtiva() {
        Empresa e = new Empresa();
        e.setId(EMPRESA_ID);
        e.setCnpj(CNPJ_A);
        e.setRazaoSocial("Clínica Teste");
        e.setInscricaoMunicipal("1234/2024");
        e.setRegimeTributario(RegimeTributario.SIMPLES_NACIONAL);
        e.setAtivo(true);
        return e;
    }

    private EmpresaRequest requestValido() {
        return new EmpresaRequest(CNPJ_A, "Clínica Teste", "1234/2024",
            "São Paulo", "3550308", RegimeTributario.SIMPLES_NACIONAL);
    }

    @Test
    void listar_retornaPaginaVaziaQuandoSemEmpresas() {
        when(repository.findAllByAtivoTrue(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        EmpresaPageResponse response = service.listar(0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void listar_retornaTodasAsEmpresas() {
        Empresa outra = new Empresa();
        outra.setId(UUID.randomUUID());
        outra.setCnpj(CNPJ_B);
        outra.setRazaoSocial("Outra Clínica");
        outra.setInscricaoMunicipal("5678/2024");
        outra.setRegimeTributario(RegimeTributario.LUCRO_PRESUMIDO);
        outra.setAtivo(true);

        when(repository.findAllByAtivoTrue(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(empresaAtiva(), outra)));

        EmpresaPageResponse response = service.listar(0, 20);

        assertThat(response.content()).hasSize(2);
    }

    @Test
    void buscarPorId_retornaEmpresaExistente() {
        when(repository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.of(empresaAtiva()));

        EmpresaResponse response = service.buscarPorId(EMPRESA_ID);

        assertThat(response.id()).isEqualTo(EMPRESA_ID);
        assertThat(response.cnpj()).isEqualTo(CNPJ_A);
    }

    @Test
    void buscarPorId_lancaNotFoundQuandoNaoExiste() {
        when(repository.findByIdAndAtivoTrue(any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(EMPRESA_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void criar_salvaNaRepoERetornaResponse() {
        when(repository.existsByCnpj(CNPJ_A)).thenReturn(false);
        when(repository.save(any(Empresa.class))).thenReturn(empresaAtiva());

        EmpresaResponse response = service.criar(requestValido());

        assertThat(response.cnpj()).isEqualTo(CNPJ_A);
        verify(repository).save(any(Empresa.class));
    }

    @Test
    void criar_cnpjDuplicado_lancaConflict() {
        when(repository.existsByCnpj(CNPJ_A)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(requestValido()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void atualizar_empresaExistente_atualizaCampos() {
        Empresa empresa = empresaAtiva();
        when(repository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.of(empresa));
        when(repository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));

        EmpresaRequest update = new EmpresaRequest(CNPJ_A, "Novo Nome", "9999/2024",
            "Campinas", "3509502", RegimeTributario.LUCRO_PRESUMIDO);

        EmpresaResponse response = service.atualizar(EMPRESA_ID, update);

        assertThat(response.razaoSocial()).isEqualTo("Novo Nome");
        assertThat(response.regimeTributario()).isEqualTo(RegimeTributario.LUCRO_PRESUMIDO);
    }

    @Test
    void atualizar_empresaInexistente_lancaNotFound() {
        when(repository.findByIdAndAtivoTrue(any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(EMPRESA_ID, requestValido()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deletar_fazSoftDelete() {
        Empresa empresa = empresaAtiva();
        when(repository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.of(empresa));

        service.deletar(EMPRESA_ID);

        ArgumentCaptor<Empresa> captor = ArgumentCaptor.forClass(Empresa.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isAtivo()).isFalse();
    }

    @Test
    void deletar_empresaInexistente_lancaNotFound() {
        when(repository.findByIdAndAtivoTrue(any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletar(EMPRESA_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
