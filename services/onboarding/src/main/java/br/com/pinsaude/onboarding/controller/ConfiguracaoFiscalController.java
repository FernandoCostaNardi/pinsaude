package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.dto.ConfiguracaoFiscalRequest;
import br.com.pinsaude.onboarding.dto.ConfiguracaoFiscalResponse;
import br.com.pinsaude.onboarding.service.ConfiguracaoFiscalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/empresas/{empresaId}/configuracao-fiscal")
@PreAuthorize("hasRole('gestao')")
public class ConfiguracaoFiscalController {

    private final ConfiguracaoFiscalService service;

    public ConfiguracaoFiscalController(ConfiguracaoFiscalService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ConfiguracaoFiscalResponse> buscar(@PathVariable UUID empresaId) {
        return ResponseEntity.ok(service.buscar(empresaId));
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoFiscalResponse> salvar(
            @PathVariable UUID empresaId,
            @Valid @RequestBody ConfiguracaoFiscalRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.salvar(empresaId, request, authentication.getName()));
    }
}
