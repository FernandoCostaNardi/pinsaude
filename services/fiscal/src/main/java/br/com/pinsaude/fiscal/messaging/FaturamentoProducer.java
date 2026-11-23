package br.com.pinsaude.fiscal.messaging;

import br.com.pinsaude.fiscal.config.RabbitConfig;
import br.com.pinsaude.fiscal.domain.NotaFiscal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class FaturamentoProducer {

    private static final Logger log = LoggerFactory.getLogger(FaturamentoProducer.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final RabbitTemplate rabbitTemplate;

    public FaturamentoProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void notificarProducaoEmitida(NotaFiscal nota) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.PRODUCAO_EMITIDA_QUEUE, mensagem(nota));
            log.info("Evento ProducaoEmitida publicado: producaoId={}", nota.getProducaoId());
        } catch (Exception e) {
            log.warn("Falha ao publicar ProducaoEmitida producaoId={}: {}", nota.getProducaoId(), e.getMessage());
        }
    }

    /**
     * Os valores de ISS/IR/CSLL/PIS/COFINS gravados na nota são os retidos pelo tomador (desde a CBS-21
     * a emissão envia as retenções do motor), então o depósito esperado é o bruto menos a soma deles.
     * CBS/IBS não entram: são débito próprio da Pin, sem retenção na fonte (split payment ainda não vale).
     */
    static ProducaoEmitidaMessage mensagem(NotaFiscal nota) {
        long retencoes = valor(nota.getValorIss()) + valor(nota.getValorIr()) + valor(nota.getValorCsll())
            + valor(nota.getValorPis()) + valor(nota.getValorCofins());
        Long liquido = nota.getValorBruto() != null
            ? Math.max(0L, nota.getValorBruto() - retencoes) : null;
        String emitidaEm = nota.getEmitidaAt() != null
            ? nota.getEmitidaAt().atZoneSameInstant(FUSO).toLocalDate().toString() : null;
        return new ProducaoEmitidaMessage(nota.getProducaoId(), nota.getCnpjIdTenant(), liquido, emitidaEm);
    }

    private static long valor(Long v) {
        return v != null ? v : 0L;
    }
}
