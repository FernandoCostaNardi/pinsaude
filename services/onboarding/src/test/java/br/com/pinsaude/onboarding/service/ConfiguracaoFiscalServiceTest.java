package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.*;
import br.com.pinsaude.onboarding.repository.AliquotaCompetenciaRepository;
import br.com.pinsaude.onboarding.repository.ConfiguracaoFiscalRepository;
import br.com.pinsaude.onboarding.repository.EmpresaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoFiscalServiceTest {

    @Mock ConfiguracaoFiscalRepository configuracaoRepo;
    @Mock AliquotaCompetenciaRepository aliquotaRepo;
    @Mock EmpresaRepository empresaRepository;
    @InjectMocks ConfiguracaoFiscalService service;

    private static final UUID EMPRESA_ID = UUID.randomUUID();
    private static final String USUARIO   = "gestao@pinsaude.com.br";
    private static final String COMPETENCIA = "2026-06";

    private Empresa empresa() {
        Empresa e = new Empresa();
        e.setId(EMPRESA_ID);
        e.setCnpj("11.222.333/0001-81");
        e.setRazaoSocial("Clínica Teste");
        e.setInscricaoMunicipal("IM-001");
        e.setRegimeTributario(RegimeTributario.SIMPLES_NACIONAL);
        e.setAtivo(true);
        return e;
    }

    private ConfiguracaoFiscal configExistente() {
        ConfiguracaoFiscal c = new ConfiguracaoFiscal();
        c.setEmpresaId(EMPRESA_ID);
        c.setCnaeCodigo("8630503");
        c.setCnaeDescricao("Atividade médica ambulatorial");
        c.setCodigoLc116("4.02");
        c.setIndicadorEquiparacaoHospitalar(false);
        c.setVencimentoCertificadoA1(LocalDate.now().plusYears(1));
        return c;
    }

    private AliquotaCompetencia aliquotaExistente() {
        AliquotaCompetencia a = new AliquotaCompetencia();
        a.setEmpresaId(EMPRESA_ID);
        a.setCompetencia(COMPETENCIA);
        a.setAliquotaIss(new BigDecimal("2.00"));
        a.setAliquotaIr(new BigDecimal("1.50"));
        a.setAliquotaCsll(new BigDecimal("1.00"));
        a.setAliquotaPis(new BigDecimal("0.65"));
        a.setAliquotaCofins(new BigDecimal("3.00"));
        a.setRegimePresuncao(RegimePresuncao.CHEIA);
        a.setCreatedBy(USUARIO);
        return a;
    }

    private AliquotaCompetenciaRequest aliquotaRequest() {
        return new AliquotaCompetenciaRequest(
            COMPETENCIA,
            new BigDecimal("2.00"), new BigDecimal("1.50"),
            new BigDecimal("1.00"), new BigDecimal("0.65"),
            new BigDecimal("3.00"), RegimePresuncao.CHEIA
        );
    }

    private ConfiguracaoFiscalRequest requestValido() {
        return new ConfiguracaoFiscalRequest(
            "8630503", "Atividade médica ambulatorial",
            "4.02", false,
            LocalDate.now().plusYears(1),
            aliquotaRequest()
        );
    }

    // ─── buscar ───────────────────────────────────────────────────────────────

    @Test
    void buscar_retornaConfigExistente() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(configuracaoRepo.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.of(configExistente()));
        when(aliquotaRepo.findByEmpresaIdOrderByCompetenciaDesc(EMPRESA_ID))
            .thenReturn(List.of(aliquotaExistente()));

        ConfiguracaoFiscalResponse resp = service.buscar(EMPRESA_ID);

        assertThat(resp.cnaeCodigo()).isEqualTo("8630503");
        assertThat(resp.historicoAliquotas()).hasSize(1);
        assertThat(resp.historicoAliquotas().get(0).competencia()).isEqualTo(COMPETENCIA);
    }

    @Test
    void buscar_semConfigCadastrada_retornaConfigVazia() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(configuracaoRepo.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.empty());
        when(aliquotaRepo.findByEmpresaIdOrderByCompetenciaDesc(EMPRESA_ID)).thenReturn(List.of());

        ConfiguracaoFiscalResponse resp = service.buscar(EMPRESA_ID);

        assertThat(resp.cnaeCodigo()).isNull();
        assertThat(resp.statusCertificado()).isEqualTo("NAO_CONFIGURADO");
        assertThat(resp.historicoAliquotas()).isEmpty();
    }

    @Test
    void buscar_empresaInexistente_lancaNotFound() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(EMPRESA_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ─── statusCertificado ────────────────────────────────────────────────────

    @Test
    void buscar_certificadoValido_retornaStatusValido() {
        ConfiguracaoFiscal c = configExistente();
        c.setVencimentoCertificadoA1(LocalDate.now().plusMonths(6));
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(configuracaoRepo.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.of(c));
        when(aliquotaRepo.findByEmpresaIdOrderByCompetenciaDesc(EMPRESA_ID)).thenReturn(List.of());

        assertThat(service.buscar(EMPRESA_ID).statusCertificado()).isEqualTo("VALIDO");
    }

    @Test
    void buscar_certificadoExpirando_retornaStatusExpirando() {
        ConfiguracaoFiscal c = configExistente();
        c.setVencimentoCertificadoA1(LocalDate.now().plusDays(10));
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(configuracaoRepo.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.of(c));
        when(aliquotaRepo.findByEmpresaIdOrderByCompetenciaDesc(EMPRESA_ID)).thenReturn(List.of());

        assertThat(service.buscar(EMPRESA_ID).statusCertificado()).isEqualTo("EXPIRANDO");
    }

    @Test
    void buscar_certificadoVencido_retornaStatusVencido() {
        ConfiguracaoFiscal c = configExistente();
        c.setVencimentoCertificadoA1(LocalDate.now().minusDays(1));
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(configuracaoRepo.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.of(c));
        when(aliquotaRepo.findByEmpresaIdOrderByCompetenciaDesc(EMPRESA_ID)).thenReturn(List.of());

        assertThat(service.buscar(EMPRESA_ID).statusCertificado()).isEqualTo("VENCIDO");
    }

    // ─── salvar ───────────────────────────────────────────────────────────────

    @Test
    void salvar_novaConfig_criaConfigEAliquota() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(configuracaoRepo.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.empty());
        when(aliquotaRepo.findByEmpresaIdAndCompetencia(EMPRESA_ID, COMPETENCIA)).thenReturn(Optional.empty());
        when(configuracaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aliquotaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aliquotaRepo.findByEmpresaIdOrderByCompetenciaDesc(EMPRESA_ID)).thenReturn(List.of());

        service.salvar(EMPRESA_ID, requestValido(), USUARIO);

        ArgumentCaptor<ConfiguracaoFiscal> configCaptor = ArgumentCaptor.forClass(ConfiguracaoFiscal.class);
        verify(configuracaoRepo).save(configCaptor.capture());
        assertThat(configCaptor.getValue().getCnaeCodigo()).isEqualTo("8630503");
        assertThat(configCaptor.getValue().getUpdatedBy()).isEqualTo(USUARIO);

        ArgumentCaptor<AliquotaCompetencia> aliquotaCaptor = ArgumentCaptor.forClass(AliquotaCompetencia.class);
        verify(aliquotaRepo).save(aliquotaCaptor.capture());
        assertThat(aliquotaCaptor.getValue().getCompetencia()).isEqualTo(COMPETENCIA);
        assertThat(aliquotaCaptor.getValue().getAliquotaIss()).isEqualByComparingTo("2.00");
        assertThat(aliquotaCaptor.getValue().getCreatedBy()).isEqualTo(USUARIO);
    }

    @Test
    void salvar_aliquotaExistenteParaCompetencia_atualizaSemAlterarCreatedBy() {
        AliquotaCompetencia existente = aliquotaExistente();
        existente.setCreatedBy("outro_usuario@pinsaude.com.br");

        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(configuracaoRepo.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.of(configExistente()));
        when(aliquotaRepo.findByEmpresaIdAndCompetencia(EMPRESA_ID, COMPETENCIA))
            .thenReturn(Optional.of(existente));
        when(configuracaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aliquotaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aliquotaRepo.findByEmpresaIdOrderByCompetenciaDesc(EMPRESA_ID)).thenReturn(List.of(existente));

        ConfiguracaoFiscalRequest reqNovo = new ConfiguracaoFiscalRequest(
            "8630503", "Atividade médica", "4.02", false, null,
            new AliquotaCompetenciaRequest(COMPETENCIA,
                new BigDecimal("5.00"), new BigDecimal("1.50"),
                new BigDecimal("1.00"), new BigDecimal("0.65"),
                new BigDecimal("3.00"), RegimePresuncao.CHEIA)
        );

        service.salvar(EMPRESA_ID, reqNovo, USUARIO);

        ArgumentCaptor<AliquotaCompetencia> captor = ArgumentCaptor.forClass(AliquotaCompetencia.class);
        verify(aliquotaRepo).save(captor.capture());
        assertThat(captor.getValue().getAliquotaIss()).isEqualByComparingTo("5.00");
        // createdBy não muda ao atualizar uma competência já existente
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("outro_usuario@pinsaude.com.br");
    }

    @Test
    void salvar_empresaInexistente_lancaNotFound() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.salvar(EMPRESA_ID, requestValido(), USUARIO))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(configuracaoRepo, never()).save(any());
        verify(aliquotaRepo, never()).save(any());
    }

    @Test
    void salvar_competenciasDiferentes_mantemHistoricoIndependente() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID)).thenReturn(Optional.of(empresa()));
        when(configuracaoRepo.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.of(configExistente()));
        when(aliquotaRepo.findByEmpresaIdAndCompetencia(eq(EMPRESA_ID), any())).thenReturn(Optional.empty());
        when(configuracaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aliquotaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aliquotaRepo.findByEmpresaIdOrderByCompetenciaDesc(EMPRESA_ID)).thenReturn(List.of());

        ConfiguracaoFiscalRequest reqMesAnterior = new ConfiguracaoFiscalRequest(
            "8630503", "Atividade médica", "4.02", false, null,
            new AliquotaCompetenciaRequest("2026-05",
                new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, RegimePresuncao.REDUZIDA)
        );

        service.salvar(EMPRESA_ID, reqMesAnterior, USUARIO);

        ArgumentCaptor<AliquotaCompetencia> captor = ArgumentCaptor.forClass(AliquotaCompetencia.class);
        verify(aliquotaRepo).save(captor.capture());
        assertThat(captor.getValue().getCompetencia()).isEqualTo("2026-05");
        assertThat(captor.getValue().getRegimePresuncao()).isEqualTo(RegimePresuncao.REDUZIDA);
    }
}
