package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.config.EmailRabbitConfig;
import br.com.pinsaude.onboarding.domain.ConviteMedico;
import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.messaging.EmailEnvioMessage;
import br.com.pinsaude.onboarding.repository.ConviteMedicoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ConviteService {

    private static final Logger log = LoggerFactory.getLogger(ConviteService.class);

    private final ConviteMedicoRepository conviteRepo;
    private final RabbitTemplate rabbitTemplate;
    private final String emailFrom;
    private final String baseUrl;
    private final long expiracaoHoras;

    public ConviteService(
            ConviteMedicoRepository conviteRepo,
            RabbitTemplate rabbitTemplate,
            @Value("${app.email-from:noreply@pinsaude.com.br}") String emailFrom,
            @Value("${app.base-url:http://localhost:3000}") String baseUrl,
            @Value("${app.convite.expiracao-horas:168}") long expiracaoHoras) {
        this.conviteRepo      = conviteRepo;
        this.rabbitTemplate   = rabbitTemplate;
        this.emailFrom        = emailFrom;
        this.baseUrl          = baseUrl;
        this.expiracaoHoras   = expiracaoHoras;
    }

    public ConviteMedico enviarConvite(Medico medico) {
        String emailDestino = medico.getEmail();
        if (emailDestino == null || emailDestino.isBlank()) {
            throw new IllegalStateException("Médico não possui e-mail cadastrado para envio do convite");
        }

        String token = UUID.randomUUID().toString().replace("-", "") +
                       UUID.randomUUID().toString().replace("-", "");

        var convite = new ConviteMedico();
        convite.setMedicoId(medico.getId());
        convite.setToken(token);
        convite.setEmailDestino(emailDestino);
        convite.setStatus("PENDENTE");
        convite.setExpiraEm(OffsetDateTime.now().plusHours(expiracaoHoras));
        convite = conviteRepo.save(convite);

        String link = baseUrl + "/onboarding?token=" + token + "&medicoId=" + medico.getId();

        var dados = Map.<String, Object>of(
            "nome", medico.getNome(),
            "link", link,
            "expiracaoHoras", expiracaoHoras
        );
        var msg = new EmailEnvioMessage(
            "CONVITE_CADASTRO",
            emailDestino,
            medico.getId().toString(),
            "Pin Saúde — Convite para completar seu cadastro",
            dados
        );

        try {
            rabbitTemplate.convertAndSend(EmailRabbitConfig.EMAIL_QUEUE, msg);
            convite.setStatus("ENVIADO");
            convite.setEnviadoEm(OffsetDateTime.now());
            convite = conviteRepo.save(convite);
            log.info("Convite enfileirado para {} (medico={})", emailDestino, medico.getId());
        } catch (Exception e) {
            log.error("Falha ao enfileirar convite para medico={}: {}", medico.getId(), e.getMessage());
        }

        return convite;
    }
}
