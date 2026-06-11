package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.dto.ContaBancariaRequest;
import br.com.pinsaude.onboarding.dto.ContaBancariaResponse;
import br.com.pinsaude.onboarding.service.ContaBancariaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/empresas/{empresaId}/contas")
@PreAuthorize("hasRole('gestao')")
public class ContaBancariaController {

    private final ContaBancariaService service;

    public ContaBancariaController(ContaBancariaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ContaBancariaResponse>> listar(@PathVariable UUID empresaId) {
        return ResponseEntity.ok(service.listar(empresaId));
    }

    @PostMapping
    public ResponseEntity<ContaBancariaResponse> criar(
            @PathVariable UUID empresaId,
            @Valid @RequestBody ContaBancariaRequest request) {
        ContaBancariaResponse created = service.criar(empresaId, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContaBancariaResponse> atualizar(
            @PathVariable UUID empresaId,
            @PathVariable UUID id,
            @Valid @RequestBody ContaBancariaRequest request) {
        return ResponseEntity.ok(service.atualizar(empresaId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @PathVariable UUID empresaId,
            @PathVariable UUID id) {
        service.deletar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
