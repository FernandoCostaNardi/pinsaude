package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.Empresa;
import br.com.pinsaude.onboarding.domain.RegimeTributario;
import br.com.pinsaude.onboarding.dto.EmpresaPageResponse;
import br.com.pinsaude.onboarding.dto.EmpresaRequest;
import br.com.pinsaude.onboarding.dto.EmpresaResponse;
import br.com.pinsaude.onboarding.repository.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    @Mock
    private EmpresaRepository repository;

    @InjectMocks
    private EmpresaService service;

    private static final String TENANT_CNPJ = "11.222.333/0001-81";
    private static final UUID EMPRESA_ID = UUID.randomUUID();

    private Empresa empresaAtiva() {
        Empresa e = new Empresa();
        e.setId(EMPRESA_ID);
        e.setCnpj(TENANT_CNPJ);
        e.setRazaoSocial("Clínica Teste");
        e.setInscricaoMunicipal("1234/2024");
        e.setRegimeTributario(RegimeTributario.SIMPLES_NACIONAL);
        e.setAtivo(true);
        return e;
    }

    private EmpresaRequest requestValido() {
        return new EmpresaRequest(TENANT_CNPJ, "Clínica Teste", "1234/2024",
            "São Paulo", "3550308", RegimeTributario.SIMPLES_NACIONAL, null);
    }

    @Test
    void listar_retornaPaginaVaziaQuandoSemEmpresas() {
        when(repository.findByCnpjAndAtivoTrue(eq(TENANT_CNPJ), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        EmpresaPageResponse response = service.listar(TENANT_CNPJ, 0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void listar_retornaEmpresasDoTenant() {
        when(repository.findByCnpjAndAtivoTrue(eq(TENANT_CNPJ), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(empresaAtiva())));

        EmpresaPageResponse response = service.listar(TENANT_CNPJ, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).cnpj()).isEqualTo(TENANT_CNPJ);
    }

    @Test
    void buscarPorId_retornaEmpresaExistente() {
        when(repository.findByIdAndCnpjAndAtivoTrue(EMPRESA_ID, TENANT_CNPJ))
            .thenReturn(Optional.of(empresaAtiva()));

        EmpresaResponse response = service.buscarPorId(EMPRESA_ID, TENANT_CNPJ);

        assertThat(response.id()).isEqualTo(EMPRESA_ID);
        assertThat(response.cnpj()).isEqualTo(TENANT_CNPJ);
    }

    @Test
    void buscarPorId_lancaNotFoundQuandoNaoExiste() {
        when(repository.findByIdAndCnpjAndAtivoTrue(any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(EMPRESA_ID, TENANT_CNPJ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void buscarPorId_lancaNotFoundQuandoNaoPertenceAoTenant() {
        when(repository.findByIdAndCnpjAndAtivoTrue(EMPRESA_ID, "outro-tenant"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(EMPRESA_ID, "outro-tenant"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void criar_salvaNaRepoERetornaResponse() {
        Empresa saved = empresaAtiva();
        when(repository.save(any(Empresa.class))).thenReturn(saved);

        EmpresaResponse response = service.criar(requestValido());

        assertThat(response.cnpj()).isEqualTo(TENANT_CNPJ);
        verify(repository).save(any(Empresa.class));
    }

    @Test
    void atualizar_empresaExistente_atualizaCampos() {
        Empresa empresa = empresaAtiva();
        when(repository.findByIdAndCnpjAndAtivoTrue(EMPRESA_ID, TENANT_CNPJ))
            .thenReturn(Optional.of(empresa));
        when(repository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));

        EmpresaRequest update = new EmpresaRequest(TENANT_CNPJ, "Novo Nome", "9999/2024",
            "Campinas", "3509502", RegimeTributario.LUCRO_PRESUMIDO, null);

        EmpresaResponse response = service.atualizar(EMPRESA_ID, update, TENANT_CNPJ);

        assertThat(response.razaoSocial()).isEqualTo("Novo Nome");
        assertThat(response.regimeTributario()).isEqualTo(RegimeTributario.LUCRO_PRESUMIDO);
    }

    @Test
    void atualizar_empresaInexistente_lancaNotFound() {
        when(repository.findByIdAndCnpjAndAtivoTrue(any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(EMPRESA_ID, requestValido(), TENANT_CNPJ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deletar_fazSoftDelete() {
        Empresa empresa = empresaAtiva();
        when(repository.findByIdAndCnpjAndAtivoTrue(EMPRESA_ID, TENANT_CNPJ))
            .thenReturn(Optional.of(empresa));

        service.deletar(EMPRESA_ID, TENANT_CNPJ);

        ArgumentCaptor<Empresa> captor = ArgumentCaptor.forClass(Empresa.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isAtivo()).isFalse();
    }

    @Test
    void deletar_empresaInexistente_lancaNotFound() {
        when(repository.findByIdAndCnpjAndAtivoTrue(any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletar(EMPRESA_ID, TENANT_CNPJ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
