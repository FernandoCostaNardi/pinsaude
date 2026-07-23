package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.dto.CandidaturaPublicaRequest;
import br.com.pinsaude.onboarding.dto.CandidaturaPublicaResponse;
import br.com.pinsaude.onboarding.service.CadastroPublicoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * Endpoints públicos (sem autenticação) do auto-cadastro de médico (EPIC-14).
 * Liberados em SecurityConfig via permitAll — nunca reaproveitar MedicoController
 * para esse fluxo, pois lá tudo exige role de operação/gestão.
 */
@RestController
@RequestMapping("/api/onboarding/publico/candidaturas")
public class CadastroPublicoController {

    private final CadastroPublicoService service;

    public CadastroPublicoController(CadastroPublicoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CandidaturaPublicaResponse> criar(@Valid @RequestBody CandidaturaPublicaRequest request) {
        CandidaturaPublicaResponse created = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CandidaturaPublicaResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody CandidaturaPublicaRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CandidaturaPublicaResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscar(id));
    }
}
