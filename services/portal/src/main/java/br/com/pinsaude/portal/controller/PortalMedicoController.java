package br.com.pinsaude.portal.controller;

import br.com.pinsaude.portal.dto.DashboardResponse;
import br.com.pinsaude.portal.dto.EmpresaPortalResponse;
import br.com.pinsaude.portal.dto.ExtratoResponse;
import br.com.pinsaude.portal.dto.NotaPortalResponse;
import br.com.pinsaude.portal.dto.PerfilMedicoResponse;
import br.com.pinsaude.portal.dto.ProducaoPortalResponse;
import br.com.pinsaude.portal.dto.TomadorPortalResponse;
import br.com.pinsaude.portal.service.PortalService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

    @GetMapping("/notas/{id}/xml")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<byte[]> downloadXml(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID medicoId = service.resolveMedicoId(jwt.getClaimAsString("email"));
        String xml = service.getNotaXml(medicoId, id);
        if (xml == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header("Content-Disposition", "attachment; filename=\"nfse-" + id + ".xml\"")
                .body(xml.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/notas/{id}/pdf")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<byte[]> downloadPdf(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID medicoId = service.resolveMedicoId(jwt.getClaimAsString("email"));
        byte[] pdf = service.getNotaPdf(medicoId, id);
        if (pdf == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"nfse-" + id + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/extrato")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<ExtratoResponse> extrato(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String dtInicio,
            @RequestParam(required = false) String dtFim) {
        UUID medicoId = service.resolveMedicoId(jwt.getClaimAsString("email"));
        LocalDate inicio = dtInicio != null && !dtInicio.isBlank() ? LocalDate.parse(dtInicio) : null;
        LocalDate fim    = dtFim    != null && !dtFim.isBlank()    ? LocalDate.parse(dtFim)    : null;
        return ResponseEntity.ok(service.getExtrato(medicoId, inicio, fim));
    }

    @GetMapping("/perfil")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<PerfilMedicoResponse> perfil(@AuthenticationPrincipal Jwt jwt) {
        UUID medicoId = service.resolveMedicoId(jwt.getClaimAsString("email"));
        return ResponseEntity.ok(service.getPerfil(medicoId));
    }

    @GetMapping("/vinculos-empresa")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<List<EmpresaPortalResponse>> vinculosEmpresa(@AuthenticationPrincipal Jwt jwt) {
        UUID medicoId = service.resolveMedicoId(jwt.getClaimAsString("email"));
        return ResponseEntity.ok(service.getEmpresasDoMedico(medicoId));
    }

    @GetMapping("/tomadores")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<List<TomadorPortalResponse>> tomadores(@AuthenticationPrincipal Jwt jwt) {
        UUID medicoId = service.resolveMedicoId(jwt.getClaimAsString("email"));
        return ResponseEntity.ok(service.getTomadoresDoMedico(medicoId));
    }

    @GetMapping("/repasses")
    @PreAuthorize("hasRole('medico')")
    public ResponseEntity<List<Object>> repasses(@AuthenticationPrincipal Jwt jwt) {
        // Repasse service ainda não implementado — retorna lista vazia
        return ResponseEntity.ok(Collections.emptyList());
    }
}
