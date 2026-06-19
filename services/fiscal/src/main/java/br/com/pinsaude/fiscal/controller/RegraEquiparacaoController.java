package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.dto.RegraEquiparacaoRequest;
import br.com.pinsaude.fiscal.dto.RegraEquiparacaoResponse;
import br.com.pinsaude.fiscal.service.RegraEquiparacaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** CRUD de regras de equiparação hospitalar (CNAE + LC 116). */
@RestController
@RequestMapping("/api/fiscal/regras-equiparacao")
@PreAuthorize("hasRole('contabil') or hasRole('gestao')")
public class RegraEquiparacaoController {

    private final RegraEquiparacaoService service;

    public RegraEquiparacaoController(RegraEquiparacaoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<RegraEquiparacaoResponse>> listar() {
        return ResponseEntity.ok(service.listar(requireCnpj()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegraEquiparacaoResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscarComHistorico(requireCnpj(), id));
    }

    @PostMapping
    public ResponseEntity<RegraEquiparacaoResponse> criar(@Valid @RequestBody RegraEquiparacaoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(requireCnpj(), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RegraEquiparacaoResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody RegraEquiparacaoRequest req) {
        return ResponseEntity.ok(service.atualizar(requireCnpj(), id, req));
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private String requireCnpj() {
        String cnpj = SecurityUtils.currentCnpjTenant();
        if (cnpj != null) cnpj = cnpj.replaceAll("\\D", "");
        if (cnpj == null || cnpj.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "CNPJ do tenant não encontrado no token");
        }
        return cnpj;
    }
}
