package br.com.pinsaude.ledger.controller;

import br.com.pinsaude.ledger.domain.TipoOrigem;
import br.com.pinsaude.ledger.dto.CriarLancamentoRequest;
import br.com.pinsaude.ledger.dto.ExtratoItemResponse;
import br.com.pinsaude.ledger.dto.LancamentoResponse;
import br.com.pinsaude.ledger.dto.SaldoResponse;
import br.com.pinsaude.ledger.service.LancamentoService;
import br.com.pinsaude.ledger.service.SaldoCalculator;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LancamentoService lancamentoService;
    private final SaldoCalculator saldoCalculator;

    public LedgerController(LancamentoService lancamentoService, SaldoCalculator saldoCalculator) {
        this.lancamentoService = lancamentoService;
        this.saldoCalculator = saldoCalculator;
    }

    /** Listagem paginada com filtros opcionais (médico, tipo de origem, intervalo de datas). */
    @GetMapping("/lancamentos")
    @PreAuthorize("hasAnyRole('financeiro','gestao','contabil')")
    public Page<LancamentoResponse> listar(
            @RequestParam(required = false) UUID medicoId,
            @RequestParam(required = false) TipoOrigem tipoOrigem,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @PageableDefault(size = 20, sort = {"dataLancamento", "createdAt"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return lancamentoService.listar(medicoId, tipoOrigem, dataInicio, dataFim, pageable);
    }

    /** Detalhe de um lançamento com todas as partidas. */
    @GetMapping("/lancamentos/{id}")
    @PreAuthorize("hasAnyRole('financeiro','gestao','contabil')")
    public LancamentoResponse detalhe(@PathVariable UUID id) {
        return lancamentoService.detalhe(id);
    }

    /** Saldo do médico: SUM(créditos) - SUM(débitos) na conta de repasse. */
    @GetMapping("/saldo/{medicoId}")
    @PreAuthorize("hasAnyRole('financeiro','gestao','contabil')")
    public SaldoResponse saldo(@PathVariable UUID medicoId) {
        return SaldoResponse.of(medicoId, saldoCalculator.saldoCentavos(medicoId));
    }

    /** Extrato do médico com saldo running após cada lançamento, em ordem cronológica. */
    @GetMapping("/extrato/{medicoId}")
    @PreAuthorize("hasAnyRole('financeiro','gestao','contabil')")
    public List<ExtratoItemResponse> extrato(@PathVariable UUID medicoId) {
        return saldoCalculator.extrato(medicoId);
    }

    /** Cria um lançamento — apenas chamadas internas (service token com role 'service'). */
    @PostMapping("/lancamentos")
    @PreAuthorize("hasRole('service')")
    public ResponseEntity<LancamentoResponse> criar(@Valid @RequestBody CriarLancamentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lancamentoService.criar(req));
    }
}
