package br.com.pinsaude.faturamento.conciliacao.messaging;

import br.com.pinsaude.faturamento.domain.StatusProducao;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

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
            boolean mudou = false;
            if (producao.getStatus() != StatusProducao.EMITIDA) {
                producao.setStatus(StatusProducao.EMITIDA);
                mudou = true;
            }
            // CBS-31: líquido esperado e data da nota para a conciliação. Uma nota substituída
            // (registro externo depois de erro/cancelamento) reenvia o evento e atualiza os dois.
            Long liquido = liquidoValido(message.valorLiquidoEsperado(), producao.getValorBruto());
            if (liquido != null && !liquido.equals(producao.getValorLiquidoEsperado())) {
                producao.setValorLiquidoEsperado(liquido);
                mudou = true;
            }
            LocalDate emitidaEm = data(message.emitidaEm());
            if (emitidaEm != null && !emitidaEm.equals(producao.getNotaEmitidaEm())) {
                producao.setNotaEmitidaEm(emitidaEm);
                mudou = true;
            }
            if (!mudou) {
                log.debug("Produção {} já está EMITIDA — ignorado (idempotência)", message.producaoId());
                return;
            }
            producaoRepo.save(producao);
            log.info("Produção {} EMITIDA após emissão de NFS-e (líquido esperado={}, emitida em={})",
                    message.producaoId(), producao.getValorLiquidoEsperado(), producao.getNotaEmitidaEm());
        }, () -> log.warn("Produção não encontrada: {}", message.producaoId()));
    }

    /** Descarta valores fora de 0..bruto (a coluna tem CHECK; um valor estranho não pode travar a fila). */
    private static Long liquidoValido(Long liquido, long bruto) {
        if (liquido == null || liquido < 0 || liquido > bruto) return null;
        return liquido;
    }

    private static LocalDate data(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
