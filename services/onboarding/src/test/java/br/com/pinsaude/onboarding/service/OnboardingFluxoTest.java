package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.*;
import br.com.pinsaude.onboarding.port.ContratoAssinaturaPort;
import br.com.pinsaude.onboarding.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingFluxoTest {

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
    @Mock JavaMailSender                 mailSender;
    @Mock EmpresaRepository              empresaRepo;

    @InjectMocks MedicoService medicoService;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Medico medicoComEmail(UUID id) {
        var m = new Medico();
        m.setId(id);
        m.setNome("Dr. Auto");
        m.setEmail("dr.auto@pinsaude.com.br");
        m.setCpfCriptografado(new byte[0]);
        m.setStatus(StatusMedico.RASCUNHO);
        m.setStatusJuntaComercial("AGUARDANDO");
        return m;
    }

    private ChecklistConduta checklistCompleto(UUID medicoId) {
        var c = new ChecklistConduta(medicoId);
        c.setNumeroConselhoVerificado(true);
        c.setRegistrosDisciplinares(true);
        c.setProcessosMedicos(true);
        return c;
    }

    private DocumentoMedico docAprovado(UUID medicoId, TipoDocumentoMedico tipo) {
        var d = new DocumentoMedico();
        d.setId(UUID.randomUUID());
        d.setMedicoId(medicoId);
        d.setTipo(tipo);
        d.setNomeArquivo(tipo.name() + ".pdf");
        d.setCaminhoStorage("x/" + tipo.name() + ".pdf");
        d.setStatusValidacao(StatusValidacaoDocumento.APROVADO);
        return d;
    }

    // ─── enviarConvite ────────────────────────────────────────────────────────

    @Test
    void enviarConvite_medicoComEmail_delegaAoConviteService() {
        UUID medicoId = UUID.randomUUID();
        var medico = medicoComEmail(medicoId);
        var conviteFake = new ConviteMedico();
        conviteFake.setMedicoId(medicoId);
        conviteFake.setToken("tok123");
        conviteFake.setEmailDestino(medico.getEmail());
        conviteFake.setStatus("ENVIADO");

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medico));
        when(conviteService.enviarConvite(medico)).thenReturn(conviteFake);

        var resp = medicoService.enviarConvite(medicoId);

        verify(conviteService).enviarConvite(medico);
        assertThat(resp.emailDestino()).isEqualTo(medico.getEmail());
        assertThat(resp.status()).isEqualTo("ENVIADO");
    }

    @Test
    void enviarConvite_medicoNaoEncontrado_lancaNotFound() {
        UUID id = UUID.randomUUID();
        when(medicoRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicoService.enviarConvite(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatusCode.valueOf(404));
    }

    // ─── ConviteService.enviarConvite (teste unitário direto) ─────────────────

    @Test
    void conviteService_enviaEmail_statusEnviado() {
        var svc = new ConviteService(
            conviteRepo, mailSender,
            "noreply@pinsaude.com.br", "http://localhost:3000", 168L
        );

        UUID medicoId = UUID.randomUUID();
        var medico = medicoComEmail(medicoId);
        var conviteSalvo = new ConviteMedico();
        conviteSalvo.setMedicoId(medicoId);
        conviteSalvo.setEmailDestino(medico.getEmail());
        conviteSalvo.setStatus("ENVIADO");
        when(conviteRepo.save(any())).thenReturn(conviteSalvo);

        var result = svc.enviarConvite(medico);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).contains(medico.getEmail());
        assertThat(msg.getSubject()).contains("Convite");
        assertThat(result.getStatus()).isEqualTo("ENVIADO");
    }

    @Test
    void conviteService_semEmail_lancaIllegalState() {
        var svc = new ConviteService(
            conviteRepo, mailSender,
            "noreply@pinsaude.com.br", "http://localhost:3000", 168L
        );
        var medico = new Medico();
        medico.setId(UUID.randomUUID());
        medico.setNome("Dr Sem Email");

        assertThatThrownBy(() -> svc.enviarConvite(medico))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("e-mail");
    }

    // ─── atualizarJuntaComercial → auto-ativação ──────────────────────────────

    @Test
    void atualizarJunta_aprovado_comTodosReqCumpridos_ativaAutomaticamente() {
        UUID medicoId = UUID.randomUUID();
        var medico = medicoComEmail(medicoId);
        var checklist = checklistCompleto(medicoId);
        var docs = List.of(
            docAprovado(medicoId, TipoDocumentoMedico.CRM),
            docAprovado(medicoId, TipoDocumentoMedico.DIPLOMA),
            docAprovado(medicoId, TipoDocumentoMedico.IDENTIDADE),
            docAprovado(medicoId, TipoDocumentoMedico.RESIDENCIA)
        );
        var contrato = new ContratoAssinatura();
        contrato.setMedicoId(medicoId);
        contrato.setStatus("ASSINADO");

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medico));
        when(medicoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(checklistRepo.findById(medicoId)).thenReturn(Optional.of(checklist));
        when(documentoRepo.findByMedicoId(medicoId)).thenReturn(docs);
        when(contratoRepo.findTopByMedicoIdOrderByCreatedAtDesc(medicoId)).thenReturn(Optional.of(contrato));
        when(dadosBancariosRepo.findByMedicoId(medicoId)).thenReturn(Optional.empty());
        when(cryptoService.decrypt(any())).thenReturn("000.000.000-00");
        when(vinculoRepo.findByIdMedicoId(medicoId)).thenReturn(List.of());
        when(conviteRepo.findTopByMedicoIdOrderByCreatedAtDesc(medicoId)).thenReturn(Optional.empty());

        var req = new AtualizarJuntaComercialRequest("APROVADO", null);
        medicoService.atualizarJuntaComercial(medicoId, req);

        ArgumentCaptor<Medico> captor = ArgumentCaptor.forClass(Medico.class);
        verify(medicoRepo, atLeast(2)).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(m -> m.getStatus() == StatusMedico.ATIVO);
    }

    @Test
    void atualizarJunta_aprovado_semContrato_naoAtiva() {
        UUID medicoId = UUID.randomUUID();
        var medico = medicoComEmail(medicoId);
        var checklist = checklistCompleto(medicoId);

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medico));
        when(medicoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(checklistRepo.findById(medicoId)).thenReturn(Optional.of(checklist));
        when(documentoRepo.findByMedicoId(medicoId)).thenReturn(List.of(
            docAprovado(medicoId, TipoDocumentoMedico.CRM),
            docAprovado(medicoId, TipoDocumentoMedico.DIPLOMA),
            docAprovado(medicoId, TipoDocumentoMedico.IDENTIDADE),
            docAprovado(medicoId, TipoDocumentoMedico.RESIDENCIA)
        ));
        when(contratoRepo.findTopByMedicoIdOrderByCreatedAtDesc(medicoId)).thenReturn(Optional.empty());
        when(dadosBancariosRepo.findByMedicoId(medicoId)).thenReturn(Optional.empty());
        when(cryptoService.decrypt(any())).thenReturn("000.000.000-00");
        when(vinculoRepo.findByIdMedicoId(medicoId)).thenReturn(List.of());
        when(conviteRepo.findTopByMedicoIdOrderByCreatedAtDesc(medicoId)).thenReturn(Optional.empty());

        var req = new AtualizarJuntaComercialRequest("APROVADO", null);
        medicoService.atualizarJuntaComercial(medicoId, req);

        ArgumentCaptor<Medico> captor = ArgumentCaptor.forClass(Medico.class);
        verify(medicoRepo, atLeast(1)).save(captor.capture());
        assertThat(captor.getAllValues()).noneMatch(m -> m.getStatus() == StatusMedico.ATIVO);
    }

    // ─── processarWebhookClicksign ────────────────────────────────────────────

    @Test
    void webhook_sign_contratoExistente_atualizaStatusAssinado() {
        UUID medicoId = UUID.randomUUID();
        String docKey = "doc-key-abc";
        var contrato = new ContratoAssinatura();
        contrato.setMedicoId(medicoId);
        contrato.setDocumentoKey(docKey);
        contrato.setStatus("ENVIADO");

        var medico = medicoComEmail(medicoId);
        medico.setStatusJuntaComercial("AGUARDANDO");

        when(contratoRepo.findByDocumentoKey(docKey)).thenReturn(Optional.of(contrato));
        when(contratoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medico));
        when(checklistRepo.findById(medicoId))
            .thenReturn(Optional.of(new ChecklistConduta(medicoId)));

        medicoService.processarWebhookClicksign(docKey, "sign");

        ArgumentCaptor<ContratoAssinatura> captor = ArgumentCaptor.forClass(ContratoAssinatura.class);
        verify(contratoRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ASSINADO");
        assertThat(captor.getValue().getAssinadoEm()).isNotNull();
    }

    @Test
    void webhook_documentoKeyDesconhecido_ignoraSilenciosamente() {
        when(contratoRepo.findByDocumentoKey("chave-inexistente")).thenReturn(Optional.empty());

        medicoService.processarWebhookClicksign("chave-inexistente", "sign");

        verify(contratoRepo, never()).save(any());
        verify(medicoRepo, never()).findById(any());
    }

    // ─── ativar manual — checklist incompleto ────────────────────────────────

    @Test
    void ativar_checklistIncompleto_lancaUnprocessable() {
        UUID medicoId = UUID.randomUUID();
        var medico = medicoComEmail(medicoId);
        var checklist = new ChecklistConduta(medicoId);

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medico));
        when(checklistRepo.findById(medicoId)).thenReturn(Optional.of(checklist));

        assertThatThrownBy(() -> medicoService.ativar(medicoId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatusCode.valueOf(422));
    }
}
