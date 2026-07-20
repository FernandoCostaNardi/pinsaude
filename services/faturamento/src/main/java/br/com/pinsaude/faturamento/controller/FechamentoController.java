package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.dto.FechamentoPreviewResponse;
import br.com.pinsaude.faturamento.dto.FechamentoRequest;
import br.com.pinsaude.faturamento.dto.FechamentoResponse;
import br.com.pinsaude.faturamento.service.FechamentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fechamentos")
public class FechamentoController {

    private final FechamentoService service;

    public FechamentoController(FechamentoService service) {
        this.service = service;
    }

    @GetMapping("/preview")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<FechamentoPreviewResponse> preview(
            @RequestParam UUID tomadorId,
            @RequestParam String competencia) {
        return ResponseEntity.ok(service.preview(tomadorId, competencia));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<FechamentoResponse> executar(
            @Valid @RequestBody FechamentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.executar(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<List<FechamentoResponse>> listar(
            @RequestParam(required = false) UUID tomadorId) {
        return ResponseEntity.ok(service.listar(tomadorId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<FechamentoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }
}
