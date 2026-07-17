package br.com.pinsaude.ledger.service;

import br.com.pinsaude.ledger.domain.ContaLedger;
import br.com.pinsaude.ledger.domain.LancamentoLedger;
import br.com.pinsaude.ledger.domain.PartidaLedger;
import br.com.pinsaude.ledger.domain.TipoOrigem;
import br.com.pinsaude.ledger.domain.TipoPartida;
import br.com.pinsaude.ledger.dto.CriarLancamentoRequest;
import br.com.pinsaude.ledger.dto.LancamentoResponse;
import br.com.pinsaude.ledger.dto.PartidaRequest;
import br.com.pinsaude.ledger.repository.ContaLedgerRepository;
import br.com.pinsaude.ledger.repository.LancamentoLedgerRepository;
import br.com.pinsaude.ledger.repository.PartidaLedgerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LancamentoService {

    private final LancamentoLedgerRepository lancamentoRepo;
    private final PartidaLedgerRepository partidaRepo;
    private final ContaLedgerRepository contaRepo;
    private final SaldoCalculator saldoCalculator;

    public LancamentoService(LancamentoLedgerRepository lancamentoRepo,
                             PartidaLedgerRepository partidaRepo,
                             ContaLedgerRepository contaRepo,
                             SaldoCalculator saldoCalculator) {
        this.lancamentoRepo = lancamentoRepo;
        this.partidaRepo = partidaRepo;
        this.contaRepo = contaRepo;
        this.saldoCalculator = saldoCalculator;
    }

    /**
     * Cria um lançamento validando o equilíbrio (partidas dobradas) ANTES de persistir.
     * Idempotente por correlation_id: repetir o mesmo evento retorna o lançamento existente.
     */
    @Transactional
    public LancamentoResponse criar(CriarLancamentoRequest req) {
        // Idempotência: mesmo correlation_id → devolve o existente (não duplica).
        var existente = lancamentoRepo.findByCorrelationId(req.correlationId());
        if (existente.isPresent()) {
            return detalhe(existente.get().getId());
        }

        validarEquilibrio(req.partidas());

        String tenant = req.cnpjIdTenant().replaceAll("\\D", "");
        LocalDate data = req.dataLancamento() != null ? req.dataLancamento() : LocalDate.now();

        LancamentoLedger lanc = new LancamentoLedger(
            tenant, req.medicoId(), data, req.competencia(),
            req.tipoOrigem(), req.origemId(), req.descricao(), req.correlationId());

        for (PartidaRequest pr : req.partidas()) {
            ContaLedger conta = contaRepo.findByCodigo(pr.contaCodigo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Conta não encontrada no plano de contas: " + pr.contaCodigo()));
            lanc.addPartida(new PartidaLedger(conta, pr.tipo(), pr.valorCentavos()));
        }

        UUID novoId;
        try {
            lancamentoRepo.save(lanc);      // cascade PERSIST nas partidas
            lancamentoRepo.flush();         // antecipa os INSERTs (constraint de equilíbrio valida no commit)
            novoId = lanc.getId();
        } catch (DataIntegrityViolationException e) {
            // Corrida concorrente com o mesmo correlation_id
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe lançamento para correlation_id: " + req.correlationId());
        }

        if (req.medicoId() != null) {
            saldoCalculator.invalidarSaldo(req.medicoId());
        }

        return detalhe(novoId);
    }

    @Transactional(readOnly = true)
    public Page<LancamentoResponse> listar(UUID medicoId, TipoOrigem tipoOrigem,
                                           LocalDate dataInicio, LocalDate dataFim, Pageable pageable) {
        Page<LancamentoLedger> page = lancamentoRepo.buscar(medicoId, tipoOrigem, dataInicio, dataFim, pageable);
        List<UUID> ids = page.getContent().stream().map(LancamentoLedger::getId).toList();

        Map<UUID, Long> totais = ids.isEmpty()
            ? Map.of()
            : partidaRepo.totalPorLancamento(ids, TipoPartida.CREDITO).stream()
                .collect(Collectors.toMap(
                    row -> (UUID) row[0],
                    row -> ((Number) row[1]).longValue()));

        return page.map(l -> LancamentoResponse.resumo(l, totais.getOrDefault(l.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public LancamentoResponse detalhe(UUID id) {
        LancamentoLedger lanc = lancamentoRepo.findByIdComPartidas(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Lançamento não encontrado: " + id));
        return LancamentoResponse.detalhe(lanc);
    }

    /** Valida partidas dobradas: SUM(débitos) = SUM(créditos) e valor total > 0. HTTP 422 se falhar. */
    private void validarEquilibrio(List<PartidaRequest> partidas) {
        long debitos = partidas.stream()
            .filter(p -> p.tipo() == TipoPartida.DEBITO)
            .mapToLong(PartidaRequest::valorCentavos).sum();
        long creditos = partidas.stream()
            .filter(p -> p.tipo() == TipoPartida.CREDITO)
            .mapToLong(PartidaRequest::valorCentavos).sum();

        if (debitos != creditos) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Lançamento desequilibrado: débitos=" + debitos + " créditos=" + creditos
                + " (partidas dobradas exige igualdade)");
        }
        if (debitos == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Lançamento sem valor: débitos e créditos zerados");
        }
    }
}
