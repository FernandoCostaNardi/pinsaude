package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.domain.TipoDocumentoEmpresa;
import br.com.pinsaude.onboarding.dto.*;
import br.com.pinsaude.onboarding.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/empresas")
@PreAuthorize("hasRole('gestao')")
public class EmpresaController {

    private final EmpresaService service;

    public EmpresaController(EmpresaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<EmpresaPageResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listar(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<EmpresaResponse> criar(@Valid @RequestBody EmpresaRequest request) {
        EmpresaResponse created = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpresaResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody EmpresaRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Documentos ──────────────────────────────────────────────────────────

    @GetMapping("/{id}/documentos")
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<List<DocumentoEmpresaResponse>> listarDocumentos(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarDocumentos(id));
    }

    @GetMapping("/{id}/documentos/contrato-social/historico")
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<List<DocumentoEmpresaResponse>> historicoContratoSocial(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarHistoricoContratoSocial(id));
    }

    @PostMapping(value = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<DocumentoEmpresaResponse> uploadDocumento(
            @PathVariable UUID id,
            @RequestParam TipoDocumentoEmpresa tipo,
            @RequestParam("arquivo") MultipartFile arquivo) {
        DocumentoEmpresaResponse response = service.uploadDocumento(id, tipo, arquivo);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{docId}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @DeleteMapping("/{id}/documentos/{docId}")
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<Void> deletarDocumento(
            @PathVariable UUID id,
            @PathVariable UUID docId) {
        service.deletarDocumento(id, docId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/documentos/{docId}/validar")
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<DocumentoEmpresaResponse> validarDocumento(
            @PathVariable UUID id,
            @PathVariable UUID docId,
            @Valid @RequestBody ValidarDocumentoRequest request) {
        return ResponseEntity.ok(service.validarDocumento(id, docId, request));
    }

    @GetMapping("/{id}/documentos/{docId}/download")
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<StreamingResponseBody> downloadDocumento(
            @PathVariable UUID id,
            @PathVariable UUID docId) {
        return service.downloadDocumento(id, docId);
    }
}
