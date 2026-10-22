package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.dto.TomadorContatoRequest;
import br.com.pinsaude.faturamento.dto.TomadorContatoResponse;
import br.com.pinsaude.faturamento.service.TomadorContatoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tomadores/{id}/contatos")
public class TomadorContatoController {

    private final TomadorContatoService service;

    public TomadorContatoController(TomadorContatoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil') or hasRole('perm_tomadores')")
    public ResponseEntity<List<TomadorContatoResponse>> listar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listar(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('operacao','gestao') or hasRole('perm_tomadores')")
    public ResponseEntity<TomadorContatoResponse> criar(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorContatoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(id, req));
    }

    @PutMapping("/{contatoId}")
    @PreAuthorize("hasAnyRole('operacao','gestao') or hasRole('perm_tomadores')")
    public ResponseEntity<TomadorContatoResponse> atualizar(
            @PathVariable UUID id,
            @PathVariable UUID contatoId,
            @Valid @RequestBody TomadorContatoRequest req) {
        return ResponseEntity.ok(service.atualizar(id, contatoId, req));
    }

    @DeleteMapping("/{contatoId}")
    @PreAuthorize("hasAnyRole('operacao','gestao') or hasRole('perm_tomadores')")
    public ResponseEntity<Void> remover(
            @PathVariable UUID id,
            @PathVariable UUID contatoId) {
        service.remover(id, contatoId);
        return ResponseEntity.noContent().build();
    }
}
