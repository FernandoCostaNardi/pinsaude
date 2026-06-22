package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.dto.ParametrosFiscaisRequest;
import br.com.pinsaude.fiscal.dto.ParametrosFiscaisResponse;
import br.com.pinsaude.fiscal.service.ParametrosFiscaisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** CRUD de alíquotas por tributo + competência (tabela parametros_fiscais V2). */
@RestController
@RequestMapping("/api/fiscal/parametros")
@PreAuthorize("hasRole('contabil') or hasRole('gestao')")
public class FiscalParametrosController {

    private final ParametrosFiscaisService service;

    public FiscalParametrosController(ParametrosFiscaisService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ParametrosFiscaisResponse>> listar() {
        String cnpj = resolveCnpj();
        if (cnpj == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(service.listar(cnpj));
    }

    @GetMapping("/verificar-proxima-competencia")
    public ResponseEntity<Map<String, Boolean>> verificarProxima() {
        String cnpj = resolveCnpj();
        if (cnpj == null) return ResponseEntity.ok(Map.of("proximaCompetenciaConfigurada", false));
        boolean temAliquotas = service.proximaCompetenciaTemAliquotas(cnpj);
        return ResponseEntity.ok(Map.of("proximaCompetenciaConfigurada", temAliquotas));
    }

    @PostMapping
    public ResponseEntity<ParametrosFiscaisResponse> criar(@Valid @RequestBody ParametrosFiscaisRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(requireCnpj(), req));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Returns stripped CNPJ or null when user is cross-tenant (gestao superuser). */
    private String resolveCnpj() {
        String cnpj = SecurityUtils.currentCnpjTenant();
        if (cnpj != null) cnpj = cnpj.replaceAll("\\D", "");
        return (cnpj == null || cnpj.isBlank()) ? null : cnpj;
    }

    private String requireCnpj() {
        String cnpj = resolveCnpj();
        if (cnpj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "CNPJ do tenant não encontrado no token");
        }
        return cnpj;
    }
}
