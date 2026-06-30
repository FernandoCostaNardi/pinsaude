package br.com.pinsaude.onboarding.messaging;

import br.com.pinsaude.onboarding.config.EmailRabbitConfig;
import br.com.pinsaude.onboarding.repository.MedicoRepository;
import br.com.pinsaude.onboarding.service.EmailTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EmailEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailEventConsumer.class);

    private final EmailTemplateService emailTemplateService;
    private final MedicoRepository medicoRepo;

    public EmailEventConsumer(EmailTemplateService emailTemplateService, MedicoRepository medicoRepo) {
        this.emailTemplateService = emailTemplateService;
        this.medicoRepo = medicoRepo;
    }

    @RabbitListener(queues = EmailRabbitConfig.EMAIL_QUEUE)
    public void consumir(EmailEnvioMessage message) {
        log.debug("Evento de e-mail recebido: tipo={}", message.tipo());
        EmailEnvioMessage resolved = resolverDestinatario(message);
        emailTemplateService.enviar(resolved);
    }

    /**
     * Se destinatario for nulo mas medicoId estiver presente,
     * resolve o e-mail consultando MedicoRepository (mesmo serviço/banco).
     */
    private EmailEnvioMessage resolverDestinatario(EmailEnvioMessage message) {
        if (message.destinatario() != null && !message.destinatario().isBlank()) {
            return message;
        }
        if (message.medicoId() != null) {
            try {
                UUID id = UUID.fromString(message.medicoId());
                String email = medicoRepo.findById(id)
                    .map(m -> m.getEmail())
                    .orElse(null);
                if (email != null && !email.isBlank()) {
                    return new EmailEnvioMessage(
                        message.tipo(), email, message.medicoId(),
                        message.assunto(), message.dados()
                    );
                }
            } catch (Exception e) {
                log.warn("Falha ao resolver e-mail do médico {}: {}", message.medicoId(), e.getMessage());
            }
        }
        log.warn("Destinatário não resolvido para evento tipo={} medicoId={}", message.tipo(), message.medicoId());
        return message;
    }
}
