package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.dto.ReceitaFederalResponse;
import br.com.pinsaude.faturamento.dto.TomadorAliquotaRequest;
import br.com.pinsaude.faturamento.dto.TomadorAliquotaResponse;
import br.com.pinsaude.faturamento.dto.TomadorCnaeRequest;
import br.com.pinsaude.faturamento.dto.TomadorCnaeResponse;
import br.com.pinsaude.faturamento.dto.TomadorRequest;
import br.com.pinsaude.faturamento.dto.TomadorResponse;
import br.com.pinsaude.faturamento.service.TomadorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/tomadores")
public class TomadorController {

    private final TomadorService service;

    public TomadorController(TomadorService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorResponse>> buscar(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.buscar(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<TomadorResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorResponse> criar(@Valid @RequestBody TomadorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorRequest req) {
        return ResponseEntity.ok(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/receita/{cnpj}")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<ReceitaFederalResponse> consultarReceita(@PathVariable String cnpj) {
        Optional<ReceitaFederalResponse> resultado = service.consultarReceita(cnpj);
        return resultado.map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── Alíquotas por tomador ────────────────────────────────────────────────

    @GetMapping("/{id}/aliquotas")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<List<TomadorAliquotaResponse>> listarAliquotas(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarAliquotas(id));
    }

    @PostMapping("/{id}/aliquotas")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorAliquotaResponse> salvarAliquota(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorAliquotaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.salvarAliquota(id, req));
    }

    @DeleteMapping("/{id}/aliquotas/{aliquotaId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerAliquota(
            @PathVariable UUID id,
            @PathVariable UUID aliquotaId) {
        service.removerAliquota(id, aliquotaId);
        return ResponseEntity.noContent().build();
    }

    // ─── CNAEs por tomador ────────────────────────────────────────────────────

    @GetMapping("/{id}/cnaes")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorCnaeResponse>> listarCnaes(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarCnaes(id));
    }

    @PostMapping("/{id}/cnaes")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorCnaeResponse> adicionarCnae(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorCnaeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarCnae(id, req));
    }

    @DeleteMapping("/{id}/cnaes/{cnaeId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerCnae(
            @PathVariable UUID id,
            @PathVariable UUID cnaeId) {
        service.removerCnae(id, cnaeId);
        return ResponseEntity.noContent().build();
    }
}
