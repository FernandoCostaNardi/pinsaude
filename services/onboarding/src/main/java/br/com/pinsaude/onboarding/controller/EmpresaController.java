package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.config.SecurityUtils;
import br.com.pinsaude.onboarding.dto.EmpresaPageResponse;
import br.com.pinsaude.onboarding.dto.EmpresaRequest;
import br.com.pinsaude.onboarding.dto.EmpresaResponse;
import br.com.pinsaude.onboarding.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/empresas")
@PreAuthorize("hasRole('gestao') or hasRole('operacao')")
public class EmpresaController {

    private final EmpresaService service;

    public EmpresaController(EmpresaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<EmpresaPageResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listar(SecurityUtils.currentCnpjTenant(), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscarPorId(id, SecurityUtils.currentCnpjTenant()));
    }

    @PostMapping
    @PreAuthorize("hasRole('gestao')")
    public ResponseEntity<EmpresaResponse> criar(@Valid @RequestBody EmpresaRequest request) {
        EmpresaResponse created = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('gestao')")
    public ResponseEntity<EmpresaResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody EmpresaRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request, SecurityUtils.currentCnpjTenant()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('gestao')")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        service.deletar(id, SecurityUtils.currentCnpjTenant());
        return ResponseEntity.noContent().build();
    }
}
