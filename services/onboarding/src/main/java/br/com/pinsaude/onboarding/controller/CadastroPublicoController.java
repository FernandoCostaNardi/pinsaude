package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.domain.TipoDocumentoMedico;
import br.com.pinsaude.onboarding.dto.*;
import br.com.pinsaude.onboarding.service.CadastroPublicoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

    @PostMapping(value = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentoMedicoResponse> uploadDocumento(
            @PathVariable UUID id,
            @RequestParam TipoDocumentoMedico tipo,
            @RequestParam("arquivo") MultipartFile arquivo) {
        DocumentoMedicoResponse response = service.uploadDocumento(id, tipo, arquivo);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{docId}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}/dados-bancarios")
    public ResponseEntity<DadosBancariosMedicoResponse> atualizarDadosBancarios(
            @PathVariable UUID id,
            @Valid @RequestBody CandidaturaDadosBancariosRequest request) {
        return ResponseEntity.ok(service.atualizarDadosBancarios(id, request));
    }

    @PostMapping("/{id}/declaracoes-lgpd")
    public ResponseEntity<DeclaracaoLgpdResponse> registrarDeclaracaoLgpd(
            @PathVariable UUID id,
            @Valid @RequestBody DeclaracaoLgpdRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.registrarDeclaracaoLgpd(id, request, resolverIpOrigem(servletRequest)));
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<FinalizarCandidaturaResponse> finalizar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.finalizar(id));
    }

    // Atrás do gateway (Spring Cloud Gateway já injeta X-Forwarded-For por padrão), o IP
    // direto da conexão é o do próprio gateway — priorizar o header quando presente.
    private String resolverIpOrigem(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
