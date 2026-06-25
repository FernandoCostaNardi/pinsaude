package br.com.pinsaude.portal.controller;

import br.com.pinsaude.portal.dto.DashboardResponse;
import br.com.pinsaude.portal.dto.NotaPortalResponse;
import br.com.pinsaude.portal.dto.ProducaoPortalResponse;
import br.com.pinsaude.portal.service.PortalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/portal")
public class PortalMedicoController {

    private final PortalService service;

    public PortalMedicoController(PortalService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<DashboardResponse> dashboard(@AuthenticationPrincipal Jwt jwt) {
        UUID medicoId = service.resolveMedicoId(jwt.getClaimAsString("email"));
        return ResponseEntity.ok(service.getDashboard(medicoId));
    }

    @GetMapping("/notas")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<List<NotaPortalResponse>> notas(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String competencia,
            @RequestParam(required = false) String status) {
        UUID medicoId = service.resolveMedicoId(jwt.getClaimAsString("email"));
        return ResponseEntity.ok(service.getNotas(medicoId, competencia, status));
    }

    @GetMapping("/producao")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<List<ProducaoPortalResponse>> producao(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String competencia) {
        UUID medicoId = service.resolveMedicoId(jwt.getClaimAsString("email"));
        return ResponseEntity.ok(service.getProducoes(medicoId, competencia));
    }

    @GetMapping("/extrato")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<List<Object>> extrato(@AuthenticationPrincipal Jwt jwt) {
        // Ledger service ainda não implementado — retorna lista vazia
        return ResponseEntity.ok(Collections.emptyList());
    }

    @GetMapping("/repasses")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<List<Object>> repasses(@AuthenticationPrincipal Jwt jwt) {
        // Repasse service ainda não implementado — retorna lista vazia
        return ResponseEntity.ok(Collections.emptyList());
    }
}
