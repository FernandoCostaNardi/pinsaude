package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.dto.EmitirLoteRequest;
import br.com.pinsaude.fiscal.dto.LoteProgressoResponse;
import br.com.pinsaude.fiscal.dto.NotaFiscalStatusResponse;
import br.com.pinsaude.fiscal.service.NfseBatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API de processamento em lote de NFS-e (EPIC-05.7).
 *
 * Permite disparar manualmente o job de emissão e acompanhar o progresso.
 * O job automático roda no dia 1 de cada mês às 02:00 via {@link br.com.pinsaude.fiscal.job.EmissaoLoteJob}.
 */
@RestController
@RequestMapping("/api/nfse/lote")
public class NfseBatchController {

    private final NfseBatchService batchService;

    public NfseBatchController(NfseBatchService batchService) {
        this.batchService = batchService;
    }

    /**
     * Dispara manualmente a emissão em lote para uma competência.
     * Útil para reprocessamento ou antecipação do job automático.
     * Retorna 202 Accepted com o progresso inicial do lote criado.
     * Retorna 409 Conflict se já existe lote em andamento para a competência.
     */
    @PostMapping("/emitir")
    @PreAuthorize("hasAnyRole('gestao', 'contabil', 'operacao')")
    public ResponseEntity<LoteProgressoResponse> emitirLote(@Valid @RequestBody EmitirLoteRequest req) {
        return ResponseEntity.accepted().body(batchService.emitirLote(req.competencia()));
    }

    /**
     * Retorna o progresso atual de um lote específico.
     * Finaliza o lote lazily se todas as notas foram processadas.
     */
    @GetMapping("/{loteId}/progresso")
    @PreAuthorize("hasAnyRole('gestao', 'contabil', 'operacao', 'financeiro')")
    public ResponseEntity<LoteProgressoResponse> getProgresso(@PathVariable UUID loteId) {
        return ResponseEntity.ok(batchService.getProgresso(loteId));
    }

    /**
     * Lista todos os lotes de emissão, do mais recente ao mais antigo.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('gestao', 'contabil', 'operacao', 'financeiro')")
    public ResponseEntity<List<LoteProgressoResponse>> listarLotes() {
        return ResponseEntity.ok(batchService.listarLotes());
    }

    /**
     * Lista notas ERRO e AGUARDANDO_EMISSAO_MANUAL da competência do lote.
     * Permite reprocessamento individual via POST /api/nfse/{notaId}/reprocessar.
     */
    @GetMapping("/{loteId}/erros")
    @PreAuthorize("hasAnyRole('gestao', 'contabil', 'operacao', 'financeiro')")
    public ResponseEntity<List<NotaFiscalStatusResponse>> listarErros(@PathVariable UUID loteId) {
        return ResponseEntity.ok(batchService.listarErros(loteId));
    }
}
