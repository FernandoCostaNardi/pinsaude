package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.config.EmailRabbitConfig;
import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.messaging.EmailEnvioMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private final RabbitTemplate rabbitTemplate;
    private final String baseUrl;

    public NotificacaoService(
            RabbitTemplate rabbitTemplate,
            @Value("${app.base-url:http://localhost:3000}") String baseUrl) {
        this.rabbitTemplate = rabbitTemplate;
        this.baseUrl = baseUrl;
    }

    public void notificarDocumentoReprovado(Medico medico, String tipoDocumento, String motivo) {
        if (medico.getEmail() == null || medico.getEmail().isBlank()) return;
        var dados = Map.<String, Object>of(
            "nome", medico.getNome(),
            "tipoDocumento", tipoDocumento,
            "motivo", motivo,
            "portalUrl", baseUrl
        );
        publicar(new EmailEnvioMessage(
            "DOCUMENTO_REPROVADO",
            medico.getEmail(),
            medico.getId().toString(),
            "Pin Saúde — Documento reprovado: " + tipoDocumento,
            dados
        ));
    }

    public void notificarCandidaturaRecebida(Medico medico) {
        if (medico.getEmail() == null || medico.getEmail().isBlank()) return;
        var dados = Map.<String, Object>of(
            "nome", medico.getNome()
        );
        publicar(new EmailEnvioMessage(
            "CANDIDATURA_RECEBIDA",
            medico.getEmail(),
            medico.getId().toString(),
            "Pin Saúde — Recebemos sua candidatura",
            dados
        ));
    }

    public void notificarMedicoAtivado(Medico medico) {
        if (medico.getEmail() == null || medico.getEmail().isBlank()) return;
        var dados = Map.<String, Object>of(
            "nome", medico.getNome(),
            "portalUrl", baseUrl
        );
        publicar(new EmailEnvioMessage(
            "MEDICO_ATIVADO",
            medico.getEmail(),
            medico.getId().toString(),
            "Pin Saúde — Cadastro aprovado! Bem-vindo(a)",
            dados
        ));
    }

    private void publicar(EmailEnvioMessage msg) {
        try {
            rabbitTemplate.convertAndSend(EmailRabbitConfig.EMAIL_QUEUE, msg);
            log.info("Notificação enfileirada: tipo={} dest={}", msg.tipo(), msg.destinatario());
        } catch (Exception e) {
            log.warn("Falha ao enfileirar notificação tipo={}: {}", msg.tipo(), e.getMessage());
        }
    }
}
