package br.com.pinsaude.fiscal.messaging;

import br.com.pinsaude.fiscal.config.RabbitConfig;
import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.RegimeTributario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica a NFS-e emitida para o ledger (CBS-40): o ledger lança o recebível, o repasse ao médico,
 * as retenções e, no regime IBS_CBS, a CBS e o IBS a recolher. Falha ao publicar não derruba a
 * emissão (mesmo padrão do {@link FaturamentoProducer}); o ledger é idempotente por nota.
 */
@Component
public class LedgerProducer {

    private static final Logger log = LoggerFactory.getLogger(LedgerProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public LedgerProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void notificarNotaEmitida(NotaFiscal nota) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.LEDGER_NOTA_EMITIDA_QUEUE, mensagem(nota));
            log.info("Evento NotaEmitida publicado para o ledger: notaId={} regime={}",
                nota.getId(), nota.getRegimeTributario());
        } catch (Exception e) {
            log.warn("Falha ao publicar NotaEmitida para o ledger notaId={}: {}", nota.getId(), e.getMessage());
        }
    }

    /**
     * ISS/IR/CSLL/PIS/COFINS da nota são os retidos pelo tomador (desde a CBS-21). CBS/IBS são débito
     * da Pin. A nota ainda não grava se CBS/IBS foram somados ao preço, então vai nulo (dentro do preço).
     */
    static LedgerNotaEmitidaMessage mensagem(NotaFiscal nota) {
        long bruto = valor(nota.getValorBruto());
        long liquido = nota.getValorLiquidoMedico() != null
            ? nota.getValorLiquidoMedico() : bruto - valor(nota.getTaxaPin());
        RegimeTributario regime = nota.getRegimeTributario() != null
            ? nota.getRegimeTributario() : RegimeTributario.LEGADO;
        return new LedgerNotaEmitidaMessage(
            nota.getId(), nota.getMedicoId(), nota.getCnpjIdTenant(), nota.getCompetencia(),
            bruto, liquido,
            valor(nota.getValorIss()), valor(nota.getValorIr()), valor(nota.getValorCsll()),
            valor(nota.getValorPis()), valor(nota.getValorCofins()),
            regime.name(), valor(nota.getValorCbs()), valor(nota.getValorIbs()), null);
    }

    private static long valor(Long v) {
        return v != null ? v : 0L;
    }
}
