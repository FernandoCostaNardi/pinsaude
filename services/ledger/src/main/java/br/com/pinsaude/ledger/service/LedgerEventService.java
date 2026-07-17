package br.com.pinsaude.ledger.service;

import br.com.pinsaude.ledger.domain.TipoOrigem;
import br.com.pinsaude.ledger.domain.TipoPartida;
import br.com.pinsaude.ledger.dto.CriarLancamentoRequest;
import br.com.pinsaude.ledger.dto.PartidaRequest;
import br.com.pinsaude.ledger.messaging.AjusteManualEvent;
import br.com.pinsaude.ledger.messaging.NotaEmitidaEvent;
import br.com.pinsaude.ledger.messaging.RecebimentoConciliadoEvent;
import br.com.pinsaude.ledger.messaging.RepasseEfetuadoEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Traduz eventos de negócio em lançamentos contábeis balanceados (partidas dobradas) e
 * delega a persistência ao {@link LancamentoService} — que valida o equilíbrio e é
 * idempotente por correlation_id. Assim, eventos duplicados não geram lançamentos duplicados.
 */
@Service
public class LedgerEventService {

    // Plano de contas (V1 + V3)
    static final String CONTA_A_RECEBER     = "1.1.01"; // Honorários a Receber (ATIVO)
    static final String CONTA_CAIXA         = "1.1.02"; // Caixa e Bancos (ATIVO)
    static final String CONTA_RETENCOES     = "2.1.01"; // Retenções de Impostos a Recolher (PASSIVO)
    static final String CONTA_REPASSE_PAGAR = "2.1.02"; // Repasses a Médicos a Pagar (PASSIVO)
    static final String CONTA_RECEITA       = "3.1.01"; // Receita de Honorários Médicos (RECEITA)

    private final LancamentoService lancamentoService;

    public LedgerEventService(LancamentoService lancamentoService) {
        this.lancamentoService = lancamentoService;
    }

    /**
     * NotaEmitida: DR Honorários a Receber (bruto) / CR Repasses a Pagar (85% do médico) +
     * CR Receita da Pin (margem) + CR uma retenção por imposto.
     */
    public void processarNotaEmitida(NotaEmitidaEvent e) {
        long bruto   = e.valorBrutoCentavos();
        long liquido = e.valorLiquidoMedicoCentavos();
        long totalRetencoes = e.valorIssCentavos() + e.valorIrCentavos() + e.valorCsllCentavos()
                            + e.valorPisCentavos() + e.valorCofinsCentavos();
        long receitaPin = bruto - liquido - totalRetencoes; // margem da Pin após repasse e impostos

        List<PartidaRequest> partidas = new ArrayList<>();
        partidas.add(new PartidaRequest(CONTA_A_RECEBER, TipoPartida.DEBITO, bruto));
        partidas.add(new PartidaRequest(CONTA_REPASSE_PAGAR, TipoPartida.CREDITO, liquido));
        if (receitaPin > 0) {
            partidas.add(new PartidaRequest(CONTA_RECEITA, TipoPartida.CREDITO, receitaPin));
        }
        addRetencao(partidas, e.valorIssCentavos());
        addRetencao(partidas, e.valorIrCentavos());
        addRetencao(partidas, e.valorCsllCentavos());
        addRetencao(partidas, e.valorPisCentavos());
        addRetencao(partidas, e.valorCofinsCentavos());

        lancamentoService.criar(new CriarLancamentoRequest(
            e.cnpjTenant(), e.medicoId(), null, e.competencia(), TipoOrigem.NOTA, e.notaId(),
            "NFS-e emitida (nota " + e.notaId() + ")", "NOTA:" + e.notaId(), partidas));
    }

    /** RecebimentoConciliado: DR Caixa / CR Honorários a Receber (baixa do recebível). */
    public void processarRecebimentoConciliado(RecebimentoConciliadoEvent e) {
        long valor = e.valorRecebidoCentavos();
        List<PartidaRequest> partidas = List.of(
            new PartidaRequest(CONTA_CAIXA, TipoPartida.DEBITO, valor),
            new PartidaRequest(CONTA_A_RECEBER, TipoPartida.CREDITO, valor));

        lancamentoService.criar(new CriarLancamentoRequest(
            e.cnpjTenant(), e.medicoId(), null, e.competencia(), TipoOrigem.CONCILIACAO, e.conciliacaoId(),
            "Recebimento conciliado (produção " + e.producaoId() + ")",
            "CONCILIACAO:" + e.conciliacaoId(), partidas));
    }

    /** RepasseEfetuado: DR Repasses a Pagar (baixa da obrigação) / CR Caixa (saída). */
    public void processarRepasseEfetuado(RepasseEfetuadoEvent e) {
        long valor = e.valorCentavos();
        List<PartidaRequest> partidas = List.of(
            new PartidaRequest(CONTA_REPASSE_PAGAR, TipoPartida.DEBITO, valor),
            new PartidaRequest(CONTA_CAIXA, TipoPartida.CREDITO, valor));

        lancamentoService.criar(new CriarLancamentoRequest(
            e.cnpjTenant(), e.medicoId(), null, e.competencia(), TipoOrigem.REPASSE, e.repasseId(),
            "Repasse efetuado ao médico (repasse " + e.repasseId() + ")",
            "REPASSE:" + e.repasseId(), partidas));
    }

    /** AjusteManual: exige autorização dupla (dois aprovadores distintos); partidas informadas. */
    public void processarAjusteManual(AjusteManualEvent e) {
        exigirAutorizacaoDupla(e);
        List<PartidaRequest> partidas = e.partidas().stream()
            .map(p -> new PartidaRequest(p.contaCodigo(), p.tipo(), p.valorCentavos()))
            .toList();

        String descricao = e.descricao() + " [autorizado por " + e.autorizadoPor1()
            + " e " + e.autorizadoPor2() + "]";

        lancamentoService.criar(new CriarLancamentoRequest(
            e.cnpjTenant(), e.medicoId(), null, e.competencia(), TipoOrigem.AJUSTE, e.ajusteId(),
            descricao, "AJUSTE:" + e.ajusteId(), partidas));
    }

    private void exigirAutorizacaoDupla(AjusteManualEvent e) {
        if (e.autorizadoPor1() == null || e.autorizadoPor2() == null
                || Objects.equals(e.autorizadoPor1(), e.autorizadoPor2())) {
            throw new IllegalArgumentException(
                "Ajuste manual exige autorização dupla: dois aprovadores distintos e não nulos");
        }
    }

    private void addRetencao(List<PartidaRequest> partidas, long valor) {
        if (valor > 0) {
            partidas.add(new PartidaRequest(CONTA_RETENCOES, TipoPartida.CREDITO, valor));
        }
    }
}
