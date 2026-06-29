package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.dto.PreviewCalculoRequest;
import br.com.pinsaude.faturamento.dto.PreviewCalculoResponse;
import br.com.pinsaude.faturamento.dto.ProducaoRequest;
import br.com.pinsaude.faturamento.dto.ProducaoResponse;
import br.com.pinsaude.faturamento.service.ProducaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/producoes")
public class ProducaoController {

    private final ProducaoService service;

    public ProducaoController(ProducaoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<List<ProducaoResponse>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String competencia,
            @RequestParam(required = false) UUID medicoId,
            @RequestParam(required = false) UUID tomadorId,
            @RequestParam(required = false) String periodoInicio,
            @RequestParam(required = false) String periodoFim) {
        return ResponseEntity.ok(service.listar(status, competencia, medicoId, tomadorId, periodoInicio, periodoFim));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<ProducaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','medico')")
    public ResponseEntity<ProducaoResponse> criar(@Valid @RequestBody ProducaoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
    }

    @PostMapping("/preview-calculo")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<PreviewCalculoResponse> previewCalculo(
            @Valid @RequestBody PreviewCalculoRequest req) {
        return ResponseEntity.ok(service.calcularPreview(req));
    }
}
