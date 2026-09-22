package br.com.pinsaude.gestao.controller;

import br.com.pinsaude.gestao.config.SecurityUtils;
import br.com.pinsaude.gestao.dto.PerfilCustomizadoRequest;
import br.com.pinsaude.gestao.dto.PerfilCustomizadoResponse;
import br.com.pinsaude.gestao.service.PerfilService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/perfis")
public class PerfilController {

    private final PerfilService service;

    public PerfilController(PerfilService service) {
        this.service = service;
    }

    /** Qualquer papel autenticado lê — a Sidebar de cada um precisa resolver seus próprios menus. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PerfilCustomizadoResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping
    @PreAuthorize("hasRole('gestao')")
    public ResponseEntity<PerfilCustomizadoResponse> criar(@RequestBody PerfilCustomizadoRequest request) {
        PerfilCustomizadoResponse perfil = service.criar(request, SecurityUtils.currentUserEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(perfil);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('gestao')")
    public ResponseEntity<PerfilCustomizadoResponse> atualizar(
            @PathVariable UUID id,
            @RequestBody PerfilCustomizadoRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('gestao')")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
