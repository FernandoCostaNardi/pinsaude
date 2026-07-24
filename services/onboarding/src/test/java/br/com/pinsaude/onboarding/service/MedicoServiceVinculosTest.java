package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.VinculoEmpresaResponse;
import br.com.pinsaude.onboarding.port.ContratoAssinaturaPort;
import br.com.pinsaude.onboarding.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicoServiceVinculosTest {

    @Mock MedicoRepository               medicoRepo;
    @Mock VinculoMedicoEmpresaRepository vinculoRepo;
    @Mock DadosBancariosMedicoRepository dadosBancariosRepo;
    @Mock DocumentoMedicoRepository      documentoRepo;
    @Mock ChecklistCondutaRepository     checklistRepo;
    @Mock HistoricoMedicoRepository      historicoRepo;
    @Mock ConviteMedicoRepository        conviteRepo;
    @Mock ContratoAssinaturaRepository   contratoRepo;
    @Mock CryptoService                  cryptoService;
    @Mock StorageService                 storageService;
    @Mock ConviteService                 conviteService;
    @Mock ContratoAssinaturaPort         contratoPort;
    @Mock NotificacaoService             notificacaoService;
    @Mock EmpresaRepository              empresaRepo;
    @Mock KeycloakAdminService           keycloakAdminService;

    @InjectMocks MedicoService service;

    private static final UUID MEDICO_ID  = UUID.randomUUID();
    private static final UUID EMPRESA_ID = UUID.randomUUID();

    private Medico medicoComKeycloak() {
        Medico m = new Medico();
        m.setId(MEDICO_ID);
        m.setNome("Dra. Auto Cadastro");
        m.setCrm("12345");
        m.setCrmUf("SP");
        m.setStatus(StatusMedico.RASCUNHO);
        m.setKeycloakUserId("kc-user-123");
        return m;
    }

    private Empresa empresa() {
        Empresa e = new Empresa();
        e.setId(EMPRESA_ID);
        e.setCnpj("11.222.333/0001-81");
        e.setRazaoSocial("Hospital Teste LTDA");
        return e;
    }

    @Test
    void adicionarVinculo_primeiroVinculoComKeycloakUserId_sincronizaCnpjId() {
        Medico medico = medicoComKeycloak();
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        when(empresaRepo.findById(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(vinculoRepo.existsByIdMedicoIdAndIdEmpresaId(MEDICO_ID, EMPRESA_ID)).thenReturn(false);
        when(vinculoRepo.findByIdMedicoId(MEDICO_ID)).thenReturn(List.of());
        when(vinculoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VinculoEmpresaResponse resp = service.adicionarVinculo(MEDICO_ID, EMPRESA_ID);

        assertThat(resp.empresaId()).isEqualTo(EMPRESA_ID);
        verify(keycloakAdminService).updateUserAttributeCnpjId("kc-user-123", "11.222.333/0001-81");
    }

    @Test
    void adicionarVinculo_segundoVinculo_naoSincronizaNovamente() {
        Medico medico = medicoComKeycloak();
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        when(empresaRepo.findById(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(vinculoRepo.existsByIdMedicoIdAndIdEmpresaId(MEDICO_ID, EMPRESA_ID)).thenReturn(false);
        VinculoMedicoEmpresa vinculoExistente = new VinculoMedicoEmpresa(
            new VinculoMedicoEmpresaId(MEDICO_ID, UUID.randomUUID()), StatusSocietario.ATIVO);
        when(vinculoRepo.findByIdMedicoId(MEDICO_ID)).thenReturn(List.of(vinculoExistente));
        when(vinculoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.adicionarVinculo(MEDICO_ID, EMPRESA_ID);

        verify(keycloakAdminService, never()).updateUserAttributeCnpjId(any(), any());
    }

    @Test
    void adicionarVinculo_medicoSemKeycloakUserId_naoChamaKeycloak() {
        Medico medico = medicoComKeycloak();
        medico.setKeycloakUserId(null);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        when(empresaRepo.findById(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(vinculoRepo.existsByIdMedicoIdAndIdEmpresaId(MEDICO_ID, EMPRESA_ID)).thenReturn(false);
        when(vinculoRepo.findByIdMedicoId(MEDICO_ID)).thenReturn(List.of());
        when(vinculoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.adicionarVinculo(MEDICO_ID, EMPRESA_ID);

        verifyNoInteractions(keycloakAdminService);
    }

    @Test
    void adicionarVinculo_falhaAoSincronizarKeycloak_naoBloqueiaCriacaoDoVinculo() {
        Medico medico = medicoComKeycloak();
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        when(empresaRepo.findById(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(vinculoRepo.existsByIdMedicoIdAndIdEmpresaId(MEDICO_ID, EMPRESA_ID)).thenReturn(false);
        when(vinculoRepo.findByIdMedicoId(MEDICO_ID)).thenReturn(List.of());
        when(vinculoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Keycloak indisponível"))
            .when(keycloakAdminService).updateUserAttributeCnpjId(any(), any());

        VinculoEmpresaResponse resp = service.adicionarVinculo(MEDICO_ID, EMPRESA_ID);

        assertThat(resp.empresaId()).isEqualTo(EMPRESA_ID);
    }

    @Test
    void adicionarVinculo_empresaNaoEncontrada_lancaNotFound() {
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medicoComKeycloak()));
        when(empresaRepo.findById(EMPRESA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adicionarVinculo(MEDICO_ID, EMPRESA_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void adicionarVinculo_jaVinculado_lancaConflict() {
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medicoComKeycloak()));
        when(empresaRepo.findById(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(vinculoRepo.existsByIdMedicoIdAndIdEmpresaId(MEDICO_ID, EMPRESA_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.adicionarVinculo(MEDICO_ID, EMPRESA_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }
}
