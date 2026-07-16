package br.com.pinsaude.faturamento.conciliacao.messaging;

import br.com.pinsaude.faturamento.domain.StatusProducao;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProducaoEmitidaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProducaoEmitidaConsumer.class);

    private final ProducaoRepository producaoRepo;

    public ProducaoEmitidaConsumer(ProducaoRepository producaoRepo) {
        this.producaoRepo = producaoRepo;
    }

    @RabbitListener(queues = RabbitConciliacaoConfig.PRODUCAO_EMITIDA_QUEUE)
    @Transactional
    public void consumir(ProducaoEmitidaMessage message) {
        producaoRepo.findById(message.producaoId()).ifPresentOrElse(producao -> {
            if (producao.getStatus() == StatusProducao.EMITIDA) {
                log.debug("Produção {} já está EMITIDA — ignorado (idempotência)", message.producaoId());
                return;
            }
            producao.setStatus(StatusProducao.EMITIDA);
            producaoRepo.save(producao);
            log.info("Produção {} atualizada para EMITIDA após emissão de NFS-e", message.producaoId());
        }, () -> log.warn("Produção não encontrada: {}", message.producaoId()));
    }
}
