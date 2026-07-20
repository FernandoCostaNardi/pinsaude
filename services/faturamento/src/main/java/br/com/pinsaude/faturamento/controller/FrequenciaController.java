package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.dto.FrequenciaItemRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaItemResponse;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaResponse;
import br.com.pinsaude.faturamento.service.FrequenciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/frequencias")
public class FrequenciaController {

    private final FrequenciaService service;

    public FrequenciaController(FrequenciaService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','medico')")
    public ResponseEntity<FrequenciaMedicaResponse> criar(@Valid @RequestBody FrequenciaMedicaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','medico','financeiro','contabil')")
    public ResponseEntity<List<FrequenciaMedicaResponse>> listar(
            @RequestParam(required = false) UUID medicoId,
            @RequestParam(required = false) UUID tomadorId,
            @RequestParam(required = false) UUID setorId,
            @RequestParam(required = false) String competencia,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.listar(medicoId, tomadorId, setorId, competencia, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('operacao','gestao','medico','financeiro','contabil')")
    public ResponseEntity<FrequenciaMedicaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PutMapping("/{id}/gerar-pdf")
    @PreAuthorize("hasAnyRole('operacao','gestao','medico')")
    public ResponseEntity<FrequenciaMedicaResponse> gerarPdf(@PathVariable UUID id) {
        return ResponseEntity.ok(service.gerarPdf(id));
    }

    @PostMapping(value = "/{id}/documento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('operacao','gestao','medico')")
    public ResponseEntity<FrequenciaMedicaResponse> receberDocumentoAssinado(
            @PathVariable UUID id,
            @RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(service.receberDocumentoAssinado(id, arquivo));
    }

    @GetMapping("/{id}/documento/url")
    @PreAuthorize("hasAnyRole('operacao','gestao','medico','financeiro','contabil')")
    public ResponseEntity<Map<String, String>> getDocumentoUrl(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("url", service.getDocumentoUrl(id)));
    }

    @PostMapping("/{id}/itens")
    @PreAuthorize("hasAnyRole('operacao','gestao','medico')")
    public ResponseEntity<FrequenciaItemResponse> adicionarItem(
            @PathVariable UUID id,
            @Valid @RequestBody FrequenciaItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarItem(id, req));
    }

    @PutMapping("/{id}/itens/{itemId}")
    @PreAuthorize("hasAnyRole('operacao','gestao','medico')")
    public ResponseEntity<FrequenciaItemResponse> atualizarItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody FrequenciaItemRequest req) {
        return ResponseEntity.ok(service.atualizarItem(id, itemId, req));
    }

    @DeleteMapping("/{id}/itens/{itemId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId) {
        service.removerItem(id, itemId);
        return ResponseEntity.noContent().build();
    }
}
