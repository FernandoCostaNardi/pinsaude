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
    static final String CONTA_CBS_RECOLHER  = "2.1.03"; // CBS a Recolher (PASSIVO, V6, CBS-40)
    static final String CONTA_IBS_RECOLHER  = "2.1.04"; // IBS a Recolher (PASSIVO, V6, CBS-40)
    static final String CONTA_RECEITA       = "3.1.01"; // Receita de Honorários Médicos (RECEITA)

    private final LancamentoService lancamentoService;

    public LedgerEventService(LancamentoService lancamentoService) {
        this.lancamentoService = lancamentoService;
    }

    /**
     * NotaEmitida: DR Honorários a Receber / CR Repasses a Pagar (líquido do médico) +
     * CR uma retenção por imposto + CR CBS e IBS a recolher (CBS-40) + CR Receita da Pin (o que sobra).
     *
     * <p>CBS/IBS são débito próprio da Pin, não retenção: vão para 2.1.03/2.1.04, nunca para 2.1.01.
     * Dentro do preço (padrão), saem da margem da Pin: receita = bruto − líquido − retenções − CBS − IBS.
     * Por fora, o tomador paga bruto + CBS + IBS: o recebível sobe nesse valor e a receita não muda.
     * Se a margem ficar negativa (taxa Pin menor que os tributos), a diferença é debitada na receita,
     * para o lançamento continuar balanceado.
     */
    public void processarNotaEmitida(NotaEmitidaEvent e) {
        long bruto   = e.valorBrutoCentavos();
        long liquido = e.valorLiquidoMedicoCentavos();
        long cbs = Math.max(0L, e.valorCbsCentavos());
        long ibs = Math.max(0L, e.valorIbsCentavos());
        long totalRetencoes = e.valorIssCentavos() + e.valorIrCentavos() + e.valorCsllCentavos()
                            + e.valorPisCentavos() + e.valorCofinsCentavos();
        boolean porFora = e.porFora();
        long aReceber   = porFora ? bruto + cbs + ibs : bruto;
        long receitaPin = aReceber - liquido - totalRetencoes - cbs - ibs; // margem da Pin

        List<PartidaRequest> partidas = new ArrayList<>();
        partidas.add(new PartidaRequest(CONTA_A_RECEBER, TipoPartida.DEBITO, aReceber));
        partidas.add(new PartidaRequest(CONTA_REPASSE_PAGAR, TipoPartida.CREDITO, liquido));
        if (receitaPin > 0) {
            partidas.add(new PartidaRequest(CONTA_RECEITA, TipoPartida.CREDITO, receitaPin));
        } else if (receitaPin < 0) {
            partidas.add(new PartidaRequest(CONTA_RECEITA, TipoPartida.DEBITO, -receitaPin));
        }
        addRetencao(partidas, e.valorIssCentavos());
        addRetencao(partidas, e.valorIrCentavos());
        addRetencao(partidas, e.valorCsllCentavos());
        addRetencao(partidas, e.valorPisCentavos());
        addRetencao(partidas, e.valorCofinsCentavos());
        addCredito(partidas, CONTA_CBS_RECOLHER, cbs);
        addCredito(partidas, CONTA_IBS_RECOLHER, ibs);

        String descricao = "IBS_CBS".equals(e.regimeOuLegado())
            ? "NFS-e emitida com CBS/IBS (nota " + e.notaId() + ")"
            : "NFS-e emitida (nota " + e.notaId() + ")";

        lancamentoService.criar(new CriarLancamentoRequest(
            somenteDigitos(e.cnpjTenant()), e.medicoId(), null, e.competencia(), TipoOrigem.NOTA, e.notaId(),
            descricao, "NOTA:" + e.notaId(), partidas));
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
        addCredito(partidas, CONTA_RETENCOES, valor);
    }

    private void addCredito(List<PartidaRequest> partidas, String conta, long valor) {
        if (valor > 0) {
            partidas.add(new PartidaRequest(conta, TipoPartida.CREDITO, valor));
        }
    }

    /** O tenant do ledger é VARCHAR(14): aceita o CNPJ formatado vindo do evento. */
    private static String somenteDigitos(String cnpj) {
        return cnpj != null ? cnpj.replaceAll("\\D", "") : null;
    }
}
