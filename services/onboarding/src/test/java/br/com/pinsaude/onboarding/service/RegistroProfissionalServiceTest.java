package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.Especialidade;
import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.domain.MedicoCrm;
import br.com.pinsaude.onboarding.dto.CrmRequest;
import br.com.pinsaude.onboarding.repository.EspecialidadeRepository;
import br.com.pinsaude.onboarding.repository.MedicoCrmRepository;
import br.com.pinsaude.onboarding.repository.MedicoEspecialidadeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistroProfissionalServiceTest {

    @Mock MedicoCrmRepository crmRepo;
    @Mock MedicoEspecialidadeRepository medicoEspecialidadeRepo;
    @Mock EspecialidadeRepository especialidadeRepo;
    @InjectMocks RegistroProfissionalService service;

    private static final UUID MEDICO_ID = UUID.randomUUID();

    @Test
    void validarCrms_semPrincipal_marcaOPrimeiroENormaliza() {
        when(crmRepo.findByNumeroAndUf(anyString(), anyString())).thenReturn(Optional.empty());

        var crms = service.validarCrms(List.of(
            new CrmRequest(" 12345 ", "pe", false),
            new CrmRequest("999", "SP", false)), MEDICO_ID);

        assertThat(crms).containsExactly(
            new CrmRequest("12345", "PE", true),
            new CrmRequest("999", "SP", false));
    }

    @Test
    void validarCrms_doisPrincipais_mantemSoOPrimeiro() {
        when(crmRepo.findByNumeroAndUf(anyString(), anyString())).thenReturn(Optional.empty());

        var crms = service.validarCrms(List.of(
            new CrmRequest("1", "PE", true),
            new CrmRequest("2", "SP", true)), MEDICO_ID);

        assertThat(crms).extracting(CrmRequest::principal).containsExactly(true, false);
    }

    @Test
    void validarCrms_ufRepetida_lancaBadRequest() {
        assertThatThrownBy(() -> service.validarCrms(List.of(
                new CrmRequest("1", "PE", true),
                new CrmRequest("2", "pe", false)), MEDICO_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("UF repetida: PE");
    }

    @Test
    void validarCrms_crmDeOutroMedico_lancaConflict() {
        when(crmRepo.findByNumeroAndUf("1", "PE"))
            .thenReturn(Optional.of(new MedicoCrm(UUID.randomUUID(), "1", "PE", true)));

        assertThatThrownBy(() -> service.validarCrms(List.of(new CrmRequest("1", "PE", true)), MEDICO_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("1/PE");
    }

    @Test
    void validarCrms_mesmoCrmDoProprioMedico_aceita() {
        when(crmRepo.findByNumeroAndUf("1", "PE"))
            .thenReturn(Optional.of(new MedicoCrm(MEDICO_ID, "1", "PE", true)));

        assertThat(service.validarCrms(List.of(new CrmRequest("1", "PE", true)), MEDICO_ID)).hasSize(1);
    }

    @Test
    void gravarCrms_espelhaPrincipalNoMedico() {
        var medico = new Medico();
        medico.setId(MEDICO_ID);

        service.gravarCrms(medico, List.of(
            new CrmRequest("1", "PE", false),
            new CrmRequest("2", "SP", true)));

        verify(crmRepo).deleteAllByMedicoId(MEDICO_ID);
        verify(crmRepo, times(2)).save(any(MedicoCrm.class));
        assertThat(medico.getCrm()).isEqualTo("2");
        assertThat(medico.getCrmUf()).isEqualTo("SP");
    }

    @Test
    void gravarCrms_listaVazia_limpaEspelho() {
        var medico = new Medico();
        medico.setId(MEDICO_ID);
        medico.setCrm("1");
        medico.setCrmUf("PE");

        service.gravarCrms(medico, List.of());

        assertThat(medico.getCrm()).isNull();
        assertThat(medico.getCrmUf()).isNull();
    }

    @Test
    void gravarEspecialidades_primeiraViraEspelhoEIdsRepetidosSaoIgnorados() {
        var cardio = especialidade("Cardiologia");
        var eco = especialidade("Ecocardiografia");
        when(especialidadeRepo.findAllById(List.of(eco.getId(), cardio.getId()))).thenReturn(List.of(cardio, eco));
        var medico = new Medico();
        medico.setId(MEDICO_ID);

        service.gravarEspecialidades(medico, List.of(eco.getId(), cardio.getId(), eco.getId()));

        verify(medicoEspecialidadeRepo).deleteAllByMedicoId(MEDICO_ID);
        verify(medicoEspecialidadeRepo, times(2)).save(any());
        assertThat(medico.getEspecialidade()).isEqualTo("Ecocardiografia");
    }

    @Test
    void gravarEspecialidades_idInexistente_lancaBadRequest() {
        var medico = new Medico();
        medico.setId(MEDICO_ID);
        UUID inexistente = UUID.randomUUID();
        when(especialidadeRepo.findAllById(List.of(inexistente))).thenReturn(List.of());

        assertThatThrownBy(() -> service.gravarEspecialidades(medico, List.of(inexistente)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Especialidade não encontrada");
        verify(medicoEspecialidadeRepo, never()).deleteAllByMedicoId(any());
    }

    private Especialidade especialidade(String nome) {
        var e = new Especialidade();
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        e.setNome(nome);
        return e;
    }
}
