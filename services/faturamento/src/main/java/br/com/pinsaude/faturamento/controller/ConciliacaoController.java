package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.conciliacao.matching.MatchingService;
import br.com.pinsaude.faturamento.domain.BancoEnum;
import br.com.pinsaude.faturamento.dto.CandidatoMatchResponse;
import br.com.pinsaude.faturamento.dto.ConciliarRequest;
import br.com.pinsaude.faturamento.dto.ExtratoResponse;
import br.com.pinsaude.faturamento.dto.LancamentoExtratoResponse;
import br.com.pinsaude.faturamento.dto.PosicaoCaixaResponse;
import br.com.pinsaude.faturamento.dto.ProducaoCandidataResponse;
import br.com.pinsaude.faturamento.service.ExtratoService;
import br.com.pinsaude.faturamento.service.PosicaoCaixaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conciliacao")
public class ConciliacaoController {

    private final ExtratoService    extratoService;
    private final MatchingService   matchingService;
    private final PosicaoCaixaService posicaoCaixaService;

    public ConciliacaoController(ExtratoService extratoService,
                                 MatchingService matchingService,
                                 PosicaoCaixaService posicaoCaixaService) {
        this.extratoService     = extratoService;
        this.matchingService    = matchingService;
        this.posicaoCaixaService = posicaoCaixaService;
    }

    @PostMapping(value = "/extratos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<ExtratoResponse> upload(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam("banco") BancoEnum banco,
            @RequestParam("data_inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam("data_fim")    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {

        if (arquivo.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ExtratoResponse response = extratoService.upload(arquivo, banco, dataInicio, dataFim);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/extratos")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<List<ExtratoResponse>> listar() {
        return ResponseEntity.ok(extratoService.listarExtratos());
    }

    @GetMapping("/extratos/{id}/lancamentos")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<List<LancamentoExtratoResponse>> listarLancamentos(
            @PathVariable UUID id,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(extratoService.listarLancamentos(id, status));
    }

    @GetMapping("/producoes/candidatas")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<List<ProducaoCandidataResponse>> listarCandidatas() {
        return ResponseEntity.ok(extratoService.listarCandidatas());
    }

    @GetMapping("/lancamentos/{id}/sugestoes")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<List<CandidatoMatchResponse>> getSugestoes(@PathVariable UUID id) {
        return ResponseEntity.ok(matchingService.getSugestoes(id));
    }

    @PostMapping("/lancamentos/{id}/conciliar")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<Void> conciliar(@PathVariable UUID id,
                                          @RequestBody @Valid ConciliarRequest req,
                                          @AuthenticationPrincipal Jwt jwt) {
        String email = jwt != null ? jwt.getClaimAsString("email") : null;
        extratoService.conciliarManual(id, req.producaoId(), email, req.observacao());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/lancamentos/{id}/ignorar")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<Void> ignorar(@PathVariable UUID id) {
        extratoService.ignorar(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/lancamentos/{id}/conciliacao")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<Void> desfazer(@PathVariable UUID id) {
        extratoService.desfazerConciliacao(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posicao-caixa")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<PosicaoCaixaResponse> getPosicaoCaixa() {
        return ResponseEntity.ok(posicaoCaixaService.calcular());
    }
}
