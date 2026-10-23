package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.dto.EmitirNfseRequest;
import br.com.pinsaude.fiscal.dto.EmitirNfseResponse;
import br.com.pinsaude.fiscal.dto.MotivoRequest;
import br.com.pinsaude.fiscal.dto.NotaFiscalStatusResponse;
import br.com.pinsaude.fiscal.dto.RegistrarEmissaoExternaRequest;
import br.com.pinsaude.fiscal.service.NfseEmissaoExternaService;
import br.com.pinsaude.fiscal.service.NfseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/nfse")
public class NfseController {

    private final NfseService nfseService;
    private final NfseEmissaoExternaService emissaoExternaService;

    public NfseController(NfseService nfseService, NfseEmissaoExternaService emissaoExternaService) {
        this.nfseService           = nfseService;
        this.emissaoExternaService = emissaoExternaService;
    }

    /**
     * Inicia a emissão de NFS-e para uma produção.
     * Resposta 202: nota enfileirada, processamento assíncrono via RabbitMQ.
     */
    @PostMapping("/emitir")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro') or hasRole('perm_notas')")
    public ResponseEntity<EmitirNfseResponse> emitir(@Valid @RequestBody EmitirNfseRequest request) {
        String cnpjTenant = SecurityUtils.currentCnpjTenant();
        EmitirNfseResponse response = nfseService.emitir(request, cnpjTenant);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Registra uma NFS-e emitida fora do sistema (portal da prefeitura): dados da nota + número e
     * data de emissão na parte "dados" (JSON) e o PDF e/ou XML recebidos (ao menos um).
     */
    @PostMapping(value = "/emissao-externa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro') or hasRole('perm_notas')")
    public ResponseEntity<NotaFiscalStatusResponse> registrarEmissaoExterna(
            @Valid @RequestPart("dados") RegistrarEmissaoExternaRequest dados,
            @RequestPart(value = "pdf", required = false) MultipartFile pdf,
            @RequestPart(value = "xml", required = false) MultipartFile xml) {
        String cnpjTenant = SecurityUtils.currentCnpjTenant();
        return ResponseEntity.status(201).body(emissaoExternaService.registrar(dados, pdf, xml, cnpjTenant));
    }

    /**
     * Anexa ou substitui o PDF e/ou XML de uma nota emitida.
     */
    @PutMapping(value = "/{notaId}/arquivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro') or hasRole('perm_notas')")
    public ResponseEntity<NotaFiscalStatusResponse> anexarArquivos(
            @PathVariable UUID notaId,
            @RequestPart(value = "pdf", required = false) MultipartFile pdf,
            @RequestPart(value = "xml", required = false) MultipartFile xml) {
        return ResponseEntity.ok(emissaoExternaService.anexarArquivos(notaId, pdf, xml));
    }

    /**
     * Lista todas as notas fiscais (mais recentes primeiro).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro') or hasRole('perm_notas')")
    public ResponseEntity<List<NotaFiscalStatusResponse>> listar() {
        return ResponseEntity.ok(nfseService.listar());
    }

    /**
     * Retorna o status atual de uma nota pelo producaoId — usado para polling.
     */
    @GetMapping("/status/{producaoId}")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro', 'medico') or hasRole('perm_notas')")
    public ResponseEntity<NotaFiscalStatusResponse> getStatus(@PathVariable UUID producaoId) {
        return ResponseEntity.ok(nfseService.getStatus(producaoId));
    }

    /**
     * Download do XML da NFS-e emitida.
     */
    @GetMapping("/{notaId}/download/xml")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro', 'medico') or hasRole('perm_notas')")
    public ResponseEntity<byte[]> downloadXml(@PathVariable UUID notaId) {
        String xml = nfseService.getXml(notaId);
        // Devolve os bytes no encoding que o próprio XML declara (XML da prefeitura pode ser ISO-8859-1)
        var charset = NfseEmissaoExternaService.charsetDeclarado(xml);
        return ResponseEntity.ok()
            .contentType(new MediaType(MediaType.APPLICATION_XML, charset))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nfse-" + notaId + ".xml\"")
            .body(xml.getBytes(charset));
    }

    /**
     * Download do PDF (DANFSE) da NFS-e emitida.
     */
    @GetMapping("/{notaId}/download/pdf")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro', 'medico') or hasRole('perm_notas')")
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
    @PreAuthorize("hasAnyRole('gestao', 'contabil') or hasRole('perm_notas')")
    public ResponseEntity<Void> aprovar(@PathVariable UUID notaId) {
        nfseService.aprovar(notaId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lista notas na fila de exceções (AGUARDANDO_VALIDACAO — 1ª nota de cada médico).
     */
    @GetMapping("/excecoes")
    @PreAuthorize("hasAnyRole('gestao', 'contabil', 'operacao', 'financeiro') or hasRole('perm_notas')")
    public ResponseEntity<List<NotaFiscalStatusResponse>> listarExcecoes() {
        return ResponseEntity.ok(nfseService.listarExcecoes());
    }

    /**
     * Cancela uma nota emitida com registro de motivo para auditoria.
     */
    @PutMapping("/{notaId}/cancelar")
    @PreAuthorize("hasAnyRole('gestao', 'contabil', 'financeiro') or hasRole('perm_notas')")
    public ResponseEntity<Void> cancelar(@PathVariable UUID notaId,
                                         @Valid @RequestBody MotivoRequest body) {
        nfseService.cancelar(notaId, body.motivo());
        return ResponseEntity.noContent().build();
    }

    /**
     * Rejeita nota na fila de exceções (AGUARDANDO_VALIDACAO → CANCELADA) com motivo.
     */
    @PutMapping("/{notaId}/rejeitar")
    @PreAuthorize("hasAnyRole('gestao', 'contabil') or hasRole('perm_notas')")
    public ResponseEntity<Void> rejeitar(@PathVariable UUID notaId,
                                          @Valid @RequestBody MotivoRequest body) {
        nfseService.rejeitar(notaId, body.motivo());
        return ResponseEntity.noContent().build();
    }

    /**
     * Reprocessa nota individual com status ERRO ou AGUARDANDO_EMISSAO_MANUAL.
     * Reseta para PENDENTE e republica na fila RabbitMQ.
     */
    @PostMapping("/{notaId}/reprocessar")
    @PreAuthorize("hasAnyRole('gestao', 'contabil', 'operacao') or hasRole('perm_notas')")
    public ResponseEntity<Void> reprocessar(@PathVariable UUID notaId) {
        nfseService.reprocessarNota(notaId);
        return ResponseEntity.noContent().build();
    }
}
