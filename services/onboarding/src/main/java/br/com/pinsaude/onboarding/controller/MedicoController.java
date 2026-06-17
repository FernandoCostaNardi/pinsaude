package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.domain.TipoDocumentoMedico;
import br.com.pinsaude.onboarding.dto.*;
import br.com.pinsaude.onboarding.service.MedicoService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/medicos")
public class MedicoController {

    private final MedicoService service;

    public MedicoController(MedicoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('gestao','operacao','financeiro','contabil')")
    public ResponseEntity<MedicoListResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.listar(page, size, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('gestao','operacao','financeiro','contabil','medico')")
    public ResponseEntity<MedicoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<MedicoResponse> criar(@Valid @RequestBody MedicoRequest request) {
        MedicoResponse created = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<MedicoResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody MedicoRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<MedicoResponse> ativar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.ativar(id));
    }

    @PutMapping("/{id}/inativar")
    @PreAuthorize("hasRole('gestao')")
    public ResponseEntity<MedicoResponse> inativar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.inativar(id));
    }

    @PutMapping("/{id}/dados-bancarios")
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<DadosBancariosMedicoResponse> atualizarDadosBancarios(
            @PathVariable UUID id,
            @Valid @RequestBody DadosBancariosMedicoRequest request) {
        return ResponseEntity.ok(service.atualizarDadosBancarios(id, request));
    }

    @GetMapping("/{id}/documentos")
    @PreAuthorize("hasAnyRole('gestao','operacao','financeiro','contabil','medico')")
    public ResponseEntity<List<DocumentoMedicoResponse>> listarDocumentos(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarDocumentos(id));
    }

    @PostMapping(value = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('gestao','operacao','medico')")
    public ResponseEntity<DocumentoMedicoResponse> uploadDocumento(
            @PathVariable UUID id,
            @RequestParam TipoDocumentoMedico tipo,
            @RequestParam("arquivo") MultipartFile arquivo) {
        DocumentoMedicoResponse response = service.uploadDocumento(id, tipo, arquivo);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{docId}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @DeleteMapping("/{id}/documentos/{docId}")
    @PreAuthorize("hasAnyRole('gestao','operacao','medico')")
    public ResponseEntity<Void> deletarDocumento(
            @PathVariable UUID id,
            @PathVariable UUID docId) {
        service.deletarDocumento(id, docId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/documentos/{docId}/validar")
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<DocumentoMedicoResponse> validarDocumento(
            @PathVariable UUID id,
            @PathVariable UUID docId,
            @Valid @RequestBody ValidarDocumentoRequest request) {
        return ResponseEntity.ok(service.validarDocumento(id, docId, request));
    }

    @GetMapping("/{id}/documentos/{docId}/url")
    @PreAuthorize("hasAnyRole('gestao','operacao','medico')")
    public ResponseEntity<java.util.Map<String, String>> getDocumentoUrl(
            @PathVariable UUID id,
            @PathVariable UUID docId) {
        return ResponseEntity.ok(java.util.Map.of("url", service.getDocumentoUrl(id, docId)));
    }

    @PutMapping("/{id}/checklist")
    @PreAuthorize("hasAnyRole('gestao','operacao')")
    public ResponseEntity<ChecklistCondutaResponse> atualizarChecklist(
            @PathVariable UUID id,
            @RequestBody ChecklistCondutaRequest request) {
        return ResponseEntity.ok(service.atualizarChecklist(id, request));
    }
}
