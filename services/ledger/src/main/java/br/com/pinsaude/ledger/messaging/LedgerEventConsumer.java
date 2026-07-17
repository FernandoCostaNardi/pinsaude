package br.com.pinsaude.ledger.messaging;

import br.com.pinsaude.ledger.config.RabbitLedgerConfig;
import br.com.pinsaude.ledger.service.LedgerEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumers que geram lançamentos automáticos a partir de eventos de negócio.
 *
 * <p>Idempotência: cada evento vira um lançamento com correlation_id determinístico
 * (ex.: "NOTA:&lt;id&gt;"); reprocessar o mesmo evento não duplica (índice único).
 *
 * <p>Outbox / idempotent consumer: o método é {@code @Transactional} — a persistência do
 * lançamento acontece na transação do processamento da mensagem; se algo falhar, a mensagem
 * é rejeitada e, após 3 tentativas (retry do application.yml), roteada para a DLQ
 * ({@code ledger.dlq}) via dead-letter exchange. Uma redelivery após commit é neutralizada
 * pela idempotência do correlation_id.
 */
@Component
public class LedgerEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LedgerEventConsumer.class);

    private final LedgerEventService eventService;

    public LedgerEventConsumer(LedgerEventService eventService) {
        this.eventService = eventService;
    }

    @RabbitListener(queues = RabbitLedgerConfig.QUEUE_NOTA_EMITIDA)
    @Transactional
    public void onNotaEmitida(NotaEmitidaEvent event) {
        log.info("Evento NotaEmitida recebido: nota={}", event.notaId());
        eventService.processarNotaEmitida(event);
    }

    @RabbitListener(queues = RabbitLedgerConfig.QUEUE_RECEBIMENTO_CONCILIADO)
    @Transactional
    public void onRecebimentoConciliado(RecebimentoConciliadoEvent event) {
        log.info("Evento RecebimentoConciliado recebido: conciliacao={}", event.conciliacaoId());
        eventService.processarRecebimentoConciliado(event);
    }

    @RabbitListener(queues = RabbitLedgerConfig.QUEUE_REPASSE_EFETUADO)
    @Transactional
    public void onRepasseEfetuado(RepasseEfetuadoEvent event) {
        log.info("Evento RepasseEfetuado recebido: repasse={}", event.repasseId());
        eventService.processarRepasseEfetuado(event);
    }

    @RabbitListener(queues = RabbitLedgerConfig.QUEUE_AJUSTE_MANUAL)
    @Transactional
    public void onAjusteManual(AjusteManualEvent event) {
        log.info("Evento AjusteManual recebido: ajuste={}", event.ajusteId());
        eventService.processarAjusteManual(event);
    }
}
