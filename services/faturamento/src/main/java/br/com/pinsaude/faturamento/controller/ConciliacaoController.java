package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.domain.BancoEnum;
import br.com.pinsaude.faturamento.dto.ExtratoResponse;
import br.com.pinsaude.faturamento.dto.LancamentoExtratoResponse;
import br.com.pinsaude.faturamento.service.ExtratoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conciliacao")
public class ConciliacaoController {

    private final ExtratoService service;

    public ConciliacaoController(ExtratoService service) {
        this.service = service;
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
        ExtratoResponse response = service.upload(arquivo, banco, dataInicio, dataFim);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/extratos")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<List<ExtratoResponse>> listar() {
        return ResponseEntity.ok(service.listarExtratos());
    }

    @GetMapping("/extratos/{id}/lancamentos")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<List<LancamentoExtratoResponse>> listarLancamentos(
            @PathVariable UUID id,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.listarLancamentos(id, status));
    }
}
