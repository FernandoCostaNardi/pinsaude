package br.com.pinsaude.gestao.controller;

import br.com.pinsaude.gestao.config.SecurityUtils;
import br.com.pinsaude.gestao.dto.AlterarPerfilRequest;
import br.com.pinsaude.gestao.dto.AlterarStatusRequest;
import br.com.pinsaude.gestao.dto.ConviteRequest;
import br.com.pinsaude.gestao.dto.UsuarioDto;
import br.com.pinsaude.gestao.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('gestao')")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<UsuarioDto>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping
    public ResponseEntity<UsuarioDto> convidar(@RequestBody ConviteRequest request) {
        String cnpjId = currentCnpjId();
        UsuarioDto usuario = service.convidar(request, cnpjId);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    @PutMapping("/{id}/perfil")
    public ResponseEntity<UsuarioDto> alterarPerfil(
            @PathVariable String id,
            @RequestBody AlterarPerfilRequest request) {
        return ResponseEntity.ok(service.alterarPerfil(id, request.perfil()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UsuarioDto> alterarStatus(
            @PathVariable String id,
            @RequestBody AlterarStatusRequest request) {
        return ResponseEntity.ok(service.alterarStatus(id, request.ativo()));
    }

    @PostMapping("/{id}/reenviar-convite")
    public ResponseEntity<Void> reenviarConvite(@PathVariable String id) {
        service.reenviarConvite(id);
        return ResponseEntity.noContent().build();
    }

    private String currentCnpjId() {
        String cnpjId = SecurityUtils.currentCnpjTenant();
        if (cnpjId == null || cnpjId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Token sem cnpj_id — usuário não associado a um tenant");
        }
        return cnpjId;
    }
}
