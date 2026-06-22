package br.com.pinsaude.fiscal.messaging;

import br.com.pinsaude.fiscal.config.RabbitConfig;
import br.com.pinsaude.fiscal.service.NfseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome a fila nfse.emissao e delega o processamento ao NfseService.
 * Spring AMQP está configurado com retry=3 + defaultRequeueRejected=false:
 * após 3 falhas consecutivas a mensagem é roteada para nfse.emissao.dlq.
 */
@Component
public class NfseEmissaoConsumer {

    private static final Logger log = LoggerFactory.getLogger(NfseEmissaoConsumer.class);

    private final NfseService nfseService;

    public NfseEmissaoConsumer(NfseService nfseService) {
        this.nfseService = nfseService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void consumir(NfseEmissaoMessage mensagem) {
        log.info("Processando emissão NFS-e para nota {}", mensagem.notaId());
        nfseService.processarEmissao(mensagem.notaId());
    }
}
