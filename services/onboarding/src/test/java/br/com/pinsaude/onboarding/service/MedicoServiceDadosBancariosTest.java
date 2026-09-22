package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.DadosBancariosMedicoRequest;
import br.com.pinsaude.onboarding.dto.DadosBancariosMedicoResponse;
import br.com.pinsaude.onboarding.port.ContratoAssinaturaPort;
import br.com.pinsaude.onboarding.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class MedicoServiceDadosBancariosTest {

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

    @InjectMocks MedicoService service;

    private static final UUID MEDICO_ID = UUID.randomUUID();
    private static final UUID CONTA_ID = UUID.randomUUID();

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

    private DadosBancariosMedico contaExistente() {
        DadosBancariosMedico d = new DadosBancariosMedico();
        d.setId(CONTA_ID);
        d.setMedicoId(MEDICO_ID);
        return d;
    }

    // ── listar ───────────────────────────────────────────────────────────────

    @Test
    void listarDadosBancarios_retornaTodasAsContas() {
        DadosBancariosMedico pix = contaExistente();
        pix.setTipoRecebimento("PIX");
        DadosBancariosMedico ted = new DadosBancariosMedico();
        ted.setId(UUID.randomUUID());
        ted.setMedicoId(MEDICO_ID);
        ted.setTipoRecebimento("TED");

        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.findByMedicoIdOrderByCreatedAtAsc(MEDICO_ID))
            .thenReturn(List.of(pix, ted));

        List<DadosBancariosMedicoResponse> resp = service.listarDadosBancarios(MEDICO_ID);

        assertThat(resp).hasSize(2);
        assertThat(resp).extracting(DadosBancariosMedicoResponse::tipoRecebimento)
            .containsExactly("PIX", "TED");
    }

    // ── adicionar — TED ──────────────────────────────────────────────────────

    @Test
    void adicionarDadosBancarios_ted_persisteCamposTed() {
        when(medicoRepo.findById(MEDICO_ID))
            .thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "341", "Itaú Unibanco", "1234", "56789-0", "CORRENTE", true, "TED salário"
        );

        DadosBancariosMedicoResponse resp = service.adicionarDadosBancarios(MEDICO_ID, req);

        assertThat(resp.tipoRecebimento()).isEqualTo("TED");
        assertThat(resp.apelido()).isEqualTo("TED salário");
        assertThat(resp.bancoCodigo()).isEqualTo("341");
        assertThat(resp.bancoNome()).isEqualTo("Itaú Unibanco");
        assertThat(resp.agencia()).isEqualTo("1234");
        assertThat(resp.conta()).isEqualTo("56789-0");
        assertThat(resp.tipoConta()).isEqualTo("CORRENTE");
        assertThat(resp.tipoPix()).isNull();
        assertThat(resp.chavePix()).isNull();
        verify(historicoRepo).save(any(HistoricoMedico.class));
    }

    @Test
    void adicionarDadosBancarios_duasContasTed_naoSobrescreveAAnterior() {
        when(medicoRepo.findById(MEDICO_ID))
            .thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DadosBancariosMedicoRequest reqUm = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "341", "Itaú", "1234", "11111-1", "CORRENTE", true, "Conta 1");
        DadosBancariosMedicoRequest reqDois = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "033", "Santander", "5678", "22222-2", "POUPANCA", true, "Conta 2");

        service.adicionarDadosBancarios(MEDICO_ID, reqUm);
        service.adicionarDadosBancarios(MEDICO_ID, reqDois);

        // adicionar sempre cria um registro novo — nunca reaproveita/consulta o existente
        verify(dadosBancariosRepo, never()).findByMedicoIdOrderByCreatedAtAsc(any());
        verify(dadosBancariosRepo, times(2)).save(any());
    }

    // ── adicionar — PIX ──────────────────────────────────────────────────────

    @Test
    void adicionarDadosBancarios_pix_persisteChavePix() {
        when(cryptoService.encrypt(any())).thenReturn(new byte[]{9, 9});
        when(cryptoService.decrypt(any())).thenReturn("medico@exemplo.com");
        when(medicoRepo.findById(MEDICO_ID))
            .thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "PIX", TipoPix.EMAIL, "medico@exemplo.com", null,
            null, null, null, null, null, true, null
        );

        DadosBancariosMedicoResponse resp = service.adicionarDadosBancarios(MEDICO_ID, req);

        assertThat(resp.tipoRecebimento()).isEqualTo("PIX");
        assertThat(resp.tipoPix()).isEqualTo(TipoPix.EMAIL);
        assertThat(resp.bancoCodigo()).isNull();
        verify(cryptoService).encrypt("medico@exemplo.com");
    }

    // ── atualizar ────────────────────────────────────────────────────────────

    @Test
    void atualizarDadosBancarios_ted_limpaChavePix() {
        DadosBancariosMedico existente = contaExistente();
        existente.setTipoRecebimento("PIX");
        existente.setTipoPix(TipoPix.CPF);
        existente.setChavePIXCriptografada(new byte[]{1});

        when(medicoRepo.findById(MEDICO_ID))
            .thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.findByIdAndMedicoId(CONTA_ID, MEDICO_ID)).thenReturn(Optional.of(existente));
        when(dadosBancariosRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "033", "Santander", "9999", "11111-1", "POUPANCA", true, null
        );

        service.atualizarDadosBancarios(MEDICO_ID, CONTA_ID, req);

        ArgumentCaptor<DadosBancariosMedico> captor = ArgumentCaptor.forClass(DadosBancariosMedico.class);
        verify(dadosBancariosRepo).save(captor.capture());

        DadosBancariosMedico salvo = captor.getValue();
        assertThat(salvo.getTipoPix()).isNull();
        assertThat(salvo.getChavePIXCriptografada()).isNull();
        assertThat(salvo.getTipoRecebimento()).isEqualTo("TED");
        assertThat(salvo.getBancoCodigo()).isEqualTo("033");
        assertThat(salvo.getTipoConta()).isEqualTo("POUPANCA");
    }

    @Test
    void atualizarDadosBancarios_pix_limpaCamposTed() {
        when(cryptoService.encrypt(any())).thenReturn(new byte[]{9, 9});
        when(cryptoService.decrypt(any())).thenReturn("+5511999999999");
        DadosBancariosMedico existente = contaExistente();
        existente.setTipoRecebimento("TED");
        existente.setBancoCodigo("341");
        existente.setBancoNome("Itaú");
        existente.setAgencia("1234");
        existente.setConta("56789-0");
        existente.setTipoConta("CORRENTE");

        when(medicoRepo.findById(MEDICO_ID))
            .thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.findByIdAndMedicoId(CONTA_ID, MEDICO_ID)).thenReturn(Optional.of(existente));
        when(dadosBancariosRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "PIX", TipoPix.TELEFONE, "+5511999999999", null,
            null, null, null, null, null, true, null
        );

        service.atualizarDadosBancarios(MEDICO_ID, CONTA_ID, req);

        ArgumentCaptor<DadosBancariosMedico> captor = ArgumentCaptor.forClass(DadosBancariosMedico.class);
        verify(dadosBancariosRepo).save(captor.capture());

        DadosBancariosMedico salvo = captor.getValue();
        assertThat(salvo.getBancoCodigo()).isNull();
        assertThat(salvo.getBancoNome()).isNull();
        assertThat(salvo.getAgencia()).isNull();
        assertThat(salvo.getConta()).isNull();
        assertThat(salvo.getTipoConta()).isNull();
    }

    @Test
    void atualizarDadosBancarios_contaDeOutroMedico_lancaNotFound() {
        when(medicoRepo.findById(MEDICO_ID))
            .thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.findByIdAndMedicoId(CONTA_ID, MEDICO_ID)).thenReturn(Optional.empty());

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "341", "Itaú", "1234", "56789-0", "CORRENTE", true, null
        );

        assertThatThrownBy(() -> service.atualizarDadosBancarios(MEDICO_ID, CONTA_ID, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── remover ──────────────────────────────────────────────────────────────

    @Test
    void removerDadosBancarios_sucesso_deletaConta() {
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.findByIdAndMedicoId(CONTA_ID, MEDICO_ID)).thenReturn(Optional.of(contaExistente()));

        service.removerDadosBancarios(MEDICO_ID, CONTA_ID);

        verify(dadosBancariosRepo).delete(any(DadosBancariosMedico.class));
        verify(historicoRepo).save(any(HistoricoMedico.class));
    }

    @Test
    void removerDadosBancarios_contaInexistente_lancaNotFound() {
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medicoAtivo()));
        when(dadosBancariosRepo.findByIdAndMedicoId(CONTA_ID, MEDICO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removerDadosBancarios(MEDICO_ID, CONTA_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── Validações ────────────────────────────────────────────────────────────

    @Test
    void adicionarDadosBancarios_semConfirmacao_lancaBadRequest() {
        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "341", "Itaú", "1234", "56789-0", "CORRENTE", false, null
        );

        assertThatThrownBy(() -> service.adicionarDadosBancarios(MEDICO_ID, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adicionarDadosBancarios_medicoInexistente_lancaNotFound() {
        when(medicoRepo.findById(MEDICO_ID))
            .thenReturn(Optional.empty());

        DadosBancariosMedicoRequest req = new DadosBancariosMedicoRequest(
            "TED", null, null, null,
            "341", "Itaú", "1234", "56789-0", "CORRENTE", true, null
        );

        assertThatThrownBy(() -> service.adicionarDadosBancarios(MEDICO_ID, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
