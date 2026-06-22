package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.dto.EmitirNfseRequest;
import br.com.pinsaude.fiscal.dto.EmitirNfseResponse;
import br.com.pinsaude.fiscal.dto.NotaFiscalStatusResponse;
import br.com.pinsaude.fiscal.service.NfseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/nfse")
public class NfseController {

    private final NfseService nfseService;

    public NfseController(NfseService nfseService) {
        this.nfseService = nfseService;
    }

    /**
     * Inicia a emissão de NFS-e para uma produção.
     * Resposta 202: nota enfileirada, processamento assíncrono via RabbitMQ.
     */
    @PostMapping("/emitir")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro')")
    public ResponseEntity<EmitirNfseResponse> emitir(@Valid @RequestBody EmitirNfseRequest request) {
        String cnpjTenant = SecurityUtils.currentCnpjTenant();
        EmitirNfseResponse response = nfseService.emitir(request, cnpjTenant);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Lista todas as notas fiscais (mais recentes primeiro).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro')")
    public ResponseEntity<List<NotaFiscalStatusResponse>> listar() {
        return ResponseEntity.ok(nfseService.listar());
    }

    /**
     * Retorna o status atual de uma nota pelo producaoId — usado para polling.
     */
    @GetMapping("/status/{producaoId}")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro', 'medico')")
    public ResponseEntity<NotaFiscalStatusResponse> getStatus(@PathVariable UUID producaoId) {
        return ResponseEntity.ok(nfseService.getStatus(producaoId));
    }

    /**
     * Download do XML da NFS-e emitida.
     */
    @GetMapping("/{notaId}/download/xml")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro', 'medico')")
    public ResponseEntity<String> downloadXml(@PathVariable UUID notaId) {
        String xml = nfseService.getXml(notaId);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_XML)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nfse-" + notaId + ".xml\"")
            .body(xml);
    }

    /**
     * Download do PDF (DANFSE) da NFS-e emitida.
     */
    @GetMapping("/{notaId}/download/pdf")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro', 'medico')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID notaId) {
        byte[] pdf = nfseService.getPdf(notaId);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nfse-" + notaId + ".pdf\"")
            .body(pdf);
    }

    /**
     * Aprova a 1ª nota de um médico (AGUARDANDO_VALIDACAO → PENDENTE).
     * Exclusivo para gestao/contabil.
     */
    @PostMapping("/{notaId}/aprovar")
    @PreAuthorize("hasAnyRole('gestao', 'contabil')")
    public ResponseEntity<Void> aprovar(@PathVariable UUID notaId) {
        nfseService.aprovar(notaId);
        return ResponseEntity.noContent().build();
    }
}
