package br.com.pinsaude.ledger.service;

import br.com.pinsaude.ledger.domain.TipoPartida;
import br.com.pinsaude.ledger.dto.ExtratoItemResponse;
import br.com.pinsaude.ledger.dto.ExtratoLinha;
import br.com.pinsaude.ledger.repository.PartidaLedgerRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cálculo de saldo e extrato do médico. O saldo é a posição da conta "Repasses a Médicos a
 * Pagar" (quanto a Pin ainda deve ao médico): SUM(créditos) - SUM(débitos) nessa conta.
 * O saldo é cacheado por médico ({@code ledgerSaldo}) e invalidado a cada novo lançamento.
 */
@Component
public class SaldoCalculator {

    /** Conta de repasse do médico no plano de contas (V1 do schema). */
    public static final String CONTA_REPASSE_MEDICO = "2.1.02";

    private final PartidaLedgerRepository partidaRepo;

    public SaldoCalculator(PartidaLedgerRepository partidaRepo) {
        this.partidaRepo = partidaRepo;
    }

    @Cacheable(value = "ledgerSaldo", key = "#medicoId")
    @Transactional(readOnly = true)
    public long saldoCentavos(UUID medicoId) {
        return partidaRepo.calcularSaldoCentavos(medicoId, CONTA_REPASSE_MEDICO, TipoPartida.CREDITO);
    }

    @Transactional(readOnly = true)
    public List<ExtratoItemResponse> extrato(UUID medicoId) {
        List<ExtratoLinha> linhas = partidaRepo.extratoBruto(medicoId, CONTA_REPASSE_MEDICO, TipoPartida.CREDITO);
        List<ExtratoItemResponse> itens = new ArrayList<>(linhas.size());
        long saldoRunning = 0L;
        for (ExtratoLinha linha : linhas) {
            saldoRunning += linha.netCentavos();
            itens.add(ExtratoItemResponse.of(linha, saldoRunning));
        }
        return itens;
    }

    /** Invalida o saldo cacheado do médico (chamado ao criar um novo lançamento). */
    @CacheEvict(value = "ledgerSaldo", key = "#medicoId")
    public void invalidarSaldo(UUID medicoId) {
        // corpo vazio — a anotação faz a evicção via proxy
    }
}
