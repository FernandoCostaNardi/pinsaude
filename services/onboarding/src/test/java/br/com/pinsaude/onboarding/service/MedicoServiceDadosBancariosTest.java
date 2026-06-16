package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.DadosBancariosMedicoRequest;
import br.com.pinsaude.onboarding.dto.DadosBancariosMedicoResponse;
import br.com.pinsaude.onboarding.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicoServiceDadosBancariosTest {

    @Mock MedicoRepository medicoRepo;
    @Mock VinculoMedicoEmpresaRepository vinculoRepo;
    @Mock DadosBancariosMedicoRepository dadosBancariosRepo;
    @Mock DocumentoMedicoRepository documentoRepo;
    @Mock ChecklistCondutaRepository checklistRepo;
    @Mock CryptoService cryptoService;
    @Mock StorageService storageService;

    @InjectMocks MedicoService service;

    private static final UUID MEDICO_ID = UUID.randomUUID();

    private Medico medicoAtivo() {
        Medico m = new Medico();
        m.setId(MEDICO_ID);
        m.setNome("Dr. João Silva");
        m.setCrm("12345");
        m.setCrmUf("SP");
        m.setStatus(StatusMedico.ATIVO);
        m.setCpfCriptografado(new byte[]{1, 2, 3});
        return m;
    }

    // ── TED ──────────────────────────────────────────────────────────────────

    @Test
    void atualizarDadosBancarios_ted_persisteCamposTed() {
        when(medicoRepo.findByIdAndStatusNot(MEDICO_ID, StatusMedico.INATIVO.name()))
            .thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.findByMedicoId(MEDICO_ID)).thenReturn(Optional.empty());
        when(dadosBancariosRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "341", "Itaú Unibanco", "1234", "56789-0", "CORRENTE", true
        );

        DadosBancariosMedicoResponse resp = service.atualizarDadosBancarios(MEDICO_ID, req);

        assertThat(resp.tipoRecebimento()).isEqualTo("TED");
        assertThat(resp.bancoCodigo()).isEqualTo("341");
        assertThat(resp.bancoNome()).isEqualTo("Itaú Unibanco");
        assertThat(resp.agencia()).isEqualTo("1234");
        assertThat(resp.conta()).isEqualTo("56789-0");
        assertThat(resp.tipoConta()).isEqualTo("CORRENTE");
        assertThat(resp.tipoPix()).isNull();
        assertThat(resp.chavePix()).isNull();
    }

    @Test
    void atualizarDadosBancarios_ted_limpaChavePix() {
        DadosBancariosMedico existente = new DadosBancariosMedico();
        existente.setMedicoId(MEDICO_ID);
        existente.setTipoRecebimento("PIX");
        existente.setTipoPix(TipoPix.CPF);
        existente.setChavePIXCriptografada(new byte[]{1});

        when(medicoRepo.findByIdAndStatusNot(MEDICO_ID, StatusMedico.INATIVO.name()))
            .thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.findByMedicoId(MEDICO_ID)).thenReturn(Optional.of(existente));
        when(dadosBancariosRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "033", "Santander", "9999", "11111-1", "POUPANCA", true
        );

        service.atualizarDadosBancarios(MEDICO_ID, req);

        ArgumentCaptor<DadosBancariosMedico> captor = ArgumentCaptor.forClass(DadosBancariosMedico.class);
        verify(dadosBancariosRepo).save(captor.capture());

        DadosBancariosMedico salvo = captor.getValue();
        assertThat(salvo.getTipoPix()).isNull();
        assertThat(salvo.getChavePIXCriptografada()).isNull();
        assertThat(salvo.getTipoRecebimento()).isEqualTo("TED");
        assertThat(salvo.getBancoCodigo()).isEqualTo("033");
        assertThat(salvo.getTipoConta()).isEqualTo("POUPANCA");
    }

    // ── PIX ──────────────────────────────────────────────────────────────────

    @Test
    void atualizarDadosBancarios_pix_persisteChavePix() {
        when(cryptoService.encrypt(any())).thenReturn(new byte[]{9, 9});
        when(cryptoService.decrypt(any())).thenReturn("medico@exemplo.com");
        when(medicoRepo.findByIdAndStatusNot(MEDICO_ID, StatusMedico.INATIVO.name()))
            .thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.findByMedicoId(MEDICO_ID)).thenReturn(Optional.empty());
        when(dadosBancariosRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "PIX", TipoPix.EMAIL, "medico@exemplo.com", null,
            null, null, null, null, null, true
        );

        DadosBancariosMedicoResponse resp = service.atualizarDadosBancarios(MEDICO_ID, req);

        assertThat(resp.tipoRecebimento()).isEqualTo("PIX");
        assertThat(resp.tipoPix()).isEqualTo(TipoPix.EMAIL);
        assertThat(resp.bancoCodigo()).isNull();
        verify(cryptoService).encrypt("medico@exemplo.com");
    }

    @Test
    void atualizarDadosBancarios_pix_limpaCamposTed() {
        when(cryptoService.encrypt(any())).thenReturn(new byte[]{9, 9});
        when(cryptoService.decrypt(any())).thenReturn("+5511999999999");
        DadosBancariosMedico existente = new DadosBancariosMedico();
        existente.setMedicoId(MEDICO_ID);
        existente.setTipoRecebimento("TED");
        existente.setBancoCodigo("341");
        existente.setBancoNome("Itaú");
        existente.setAgencia("1234");
        existente.setConta("56789-0");
        existente.setTipoConta("CORRENTE");

        when(medicoRepo.findByIdAndStatusNot(MEDICO_ID, StatusMedico.INATIVO.name()))
            .thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.findByMedicoId(MEDICO_ID)).thenReturn(Optional.of(existente));
        when(dadosBancariosRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "PIX", TipoPix.TELEFONE, "+5511999999999", null,
            null, null, null, null, null, true
        );

        service.atualizarDadosBancarios(MEDICO_ID, req);

        ArgumentCaptor<DadosBancariosMedico> captor = ArgumentCaptor.forClass(DadosBancariosMedico.class);
        verify(dadosBancariosRepo).save(captor.capture());

        DadosBancariosMedico salvo = captor.getValue();
        assertThat(salvo.getBancoCodigo()).isNull();
        assertThat(salvo.getBancoNome()).isNull();
        assertThat(salvo.getAgencia()).isNull();
        assertThat(salvo.getConta()).isNull();
        assertThat(salvo.getTipoConta()).isNull();
    }

    // ── Validações ────────────────────────────────────────────────────────────

    @Test
    void atualizarDadosBancarios_semConfirmacao_lancaBadRequest() {
        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "341", "Itaú", "1234", "56789-0", "CORRENTE", false
        );

        assertThatThrownBy(() -> service.atualizarDadosBancarios(MEDICO_ID, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void atualizarDadosBancarios_medicoInexistente_lancaNotFound() {
        when(medicoRepo.findByIdAndStatusNot(MEDICO_ID, StatusMedico.INATIVO.name()))
            .thenReturn(Optional.empty());

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "341", "Itaú", "1234", "56789-0", "CORRENTE", true
        );

        assertThatThrownBy(() -> service.atualizarDadosBancarios(MEDICO_ID, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
