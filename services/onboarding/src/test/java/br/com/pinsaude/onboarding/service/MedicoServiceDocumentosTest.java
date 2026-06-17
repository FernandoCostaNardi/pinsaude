package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.DocumentoMedicoResponse;
import br.com.pinsaude.onboarding.dto.ValidarDocumentoRequest;
import br.com.pinsaude.onboarding.port.ContratoAssinaturaPort;
import br.com.pinsaude.onboarding.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicoServiceDocumentosTest {

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

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Medico medicoExistente(UUID id) {
        var m = new Medico();
        m.setId(id);
        m.setNome("Dr. Teste");
        m.setCpfCriptografado(new byte[0]);
        return m;
    }

    private DocumentoMedico docExistente(UUID medicoId, TipoDocumentoMedico tipo, StatusValidacaoDocumento status) {
        var d = new DocumentoMedico();
        d.setId(UUID.randomUUID());
        d.setMedicoId(medicoId);
        d.setTipo(tipo);
        d.setNomeArquivo("crm.pdf");
        d.setCaminhoStorage("documentos/" + medicoId + "/" + tipo + "/crm.pdf");
        d.setStatusValidacao(status);
        return d;
    }

    // ─── uploadDocumento — adiciona (não substitui) ───────────────────────────

    @Test
    void upload_adicionaNovoDocumento_semDeletarExistentes() {
        UUID medicoId = UUID.randomUUID();
        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medicoExistente(medicoId)));
        when(storageService.upload(any(), any(), any())).thenReturn("novo/caminho.pdf");
        when(documentoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var arquivo = new MockMultipartFile("arquivo", "novo.pdf", "application/pdf", new byte[]{1, 2, 3});
        service.uploadDocumento(medicoId, TipoDocumentoMedico.CRM, arquivo);

        verify(storageService, never()).delete(any());
        verify(documentoRepo, never()).delete(any(DocumentoMedico.class));
        verify(documentoRepo).save(any(DocumentoMedico.class));
    }

    @Test
    void upload_multiplosArquivosDoMesmoTipo_todosPersistidos() {
        UUID medicoId = UUID.randomUUID();
        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medicoExistente(medicoId)));
        when(storageService.upload(any(), any(), any()))
            .thenReturn("contrato1.pdf")
            .thenReturn("contrato2.pdf");
        when(documentoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var a1 = new MockMultipartFile("arquivo", "contrato1.pdf", "application/pdf", new byte[]{1});
        var a2 = new MockMultipartFile("arquivo", "contrato2.pdf", "application/pdf", new byte[]{2});
        service.uploadDocumento(medicoId, TipoDocumentoMedico.CONTRATO, a1);
        service.uploadDocumento(medicoId, TipoDocumentoMedico.CONTRATO, a2);

        verify(storageService, never()).delete(any());
        verify(documentoRepo, never()).delete(any(DocumentoMedico.class));
        verify(documentoRepo, times(2)).save(any(DocumentoMedico.class));
    }

    @Test
    void upload_arquivoVazio_lancaBadRequest() {
        UUID medicoId = UUID.randomUUID();
        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medicoExistente(medicoId)));

        var vazio = new MockMultipartFile("arquivo", "x.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> service.uploadDocumento(medicoId, TipoDocumentoMedico.CRM, vazio))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatusCode.valueOf(400));
    }

    // ─── validarDocumento ─────────────────────────────────────────────────────

    @Test
    void validar_aprovado_limpaMotivo() {
        UUID medicoId = UUID.randomUUID();
        UUID docId    = UUID.randomUUID();
        var doc = docExistente(medicoId, TipoDocumentoMedico.CRM, StatusValidacaoDocumento.REPROVADO);
        doc.setId(docId);
        doc.setMotivoReprovacao("Ilegível");

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medicoExistente(medicoId)));
        when(documentoRepo.findById(docId)).thenReturn(Optional.of(doc));
        when(documentoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ValidarDocumentoRequest(StatusValidacaoDocumento.APROVADO, null);
        DocumentoMedicoResponse resp = service.validarDocumento(medicoId, docId, req);

        assertThat(resp.statusValidacao()).isEqualTo(StatusValidacaoDocumento.APROVADO);
        assertThat(resp.motivoReprovacao()).isNull();
    }

    @Test
    void validar_reprovadoComMotivo_salvaMotivo() {
        UUID medicoId = UUID.randomUUID();
        UUID docId    = UUID.randomUUID();
        var doc = docExistente(medicoId, TipoDocumentoMedico.IDENTIDADE, StatusValidacaoDocumento.PENDENTE);
        doc.setId(docId);

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medicoExistente(medicoId)));
        when(documentoRepo.findById(docId)).thenReturn(Optional.of(doc));
        when(documentoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ValidarDocumentoRequest(StatusValidacaoDocumento.REPROVADO, "Foto ilegível");
        DocumentoMedicoResponse resp = service.validarDocumento(medicoId, docId, req);

        assertThat(resp.statusValidacao()).isEqualTo(StatusValidacaoDocumento.REPROVADO);
        assertThat(resp.motivoReprovacao()).isEqualTo("Foto ilegível");
    }

    @Test
    void validar_reprovadoSemMotivo_lancaBadRequest() {
        UUID medicoId = UUID.randomUUID();
        UUID docId    = UUID.randomUUID();
        var doc = docExistente(medicoId, TipoDocumentoMedico.CRM, StatusValidacaoDocumento.PENDENTE);
        doc.setId(docId);

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medicoExistente(medicoId)));
        when(documentoRepo.findById(docId)).thenReturn(Optional.of(doc));

        var req = new ValidarDocumentoRequest(StatusValidacaoDocumento.REPROVADO, "");
        assertThatThrownBy(() -> service.validarDocumento(medicoId, docId, req))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatusCode.valueOf(400));
    }

    @Test
    void validar_documentoDeOutroMedico_lancaNotFound() {
        UUID medicoId = UUID.randomUUID();
        UUID docId    = UUID.randomUUID();
        UUID outroId  = UUID.randomUUID();

        var doc = docExistente(outroId, TipoDocumentoMedico.CRM, StatusValidacaoDocumento.PENDENTE);
        doc.setId(docId);

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medicoExistente(medicoId)));
        when(documentoRepo.findById(docId)).thenReturn(Optional.of(doc));

        var req = new ValidarDocumentoRequest(StatusValidacaoDocumento.APROVADO, null);
        assertThatThrownBy(() -> service.validarDocumento(medicoId, docId, req))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatusCode.valueOf(404));
    }

    // ─── deletarDocumento ─────────────────────────────────────────────────────

    @Test
    void deletar_documentoDoMedico_deletaArquivoERegistro() {
        UUID medicoId = UUID.randomUUID();
        UUID docId    = UUID.randomUUID();
        var doc = docExistente(medicoId, TipoDocumentoMedico.CONTRATO, StatusValidacaoDocumento.PENDENTE);
        doc.setId(docId);

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medicoExistente(medicoId)));
        when(documentoRepo.findById(docId)).thenReturn(Optional.of(doc));

        service.deletarDocumento(medicoId, docId);

        verify(storageService).delete(doc.getCaminhoStorage());
        verify(documentoRepo).delete(doc);
    }

    @Test
    void deletar_documentoDeOutroMedico_lancaNotFound() {
        UUID medicoId = UUID.randomUUID();
        UUID docId    = UUID.randomUUID();
        var doc = docExistente(UUID.randomUUID(), TipoDocumentoMedico.CRM, StatusValidacaoDocumento.PENDENTE);
        doc.setId(docId);

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medicoExistente(medicoId)));
        when(documentoRepo.findById(docId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.deletarDocumento(medicoId, docId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatusCode.valueOf(404));
    }

    // ─── ativar — bloqueia sem docs aprovados ─────────────────────────────────

    @Test
    void ativar_semDocumentosAprovados_lancaUnprocessable() {
        UUID medicoId = UUID.randomUUID();
        var medico    = medicoExistente(medicoId);
        var checklist = new ChecklistConduta(medicoId);
        checklist.setNumeroConselhoVerificado(true);
        checklist.setRegistrosDisciplinares(true);
        checklist.setProcessosMedicos(true);

        when(medicoRepo.findById(medicoId)).thenReturn(Optional.of(medico));
        when(checklistRepo.findById(medicoId)).thenReturn(Optional.of(checklist));
        when(documentoRepo.findByMedicoId(medicoId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.ativar(medicoId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatusCode.valueOf(422));
    }
}
