package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.messaging.EmailEnvioMessage;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;

@Service
public class EmailTemplateService {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateService.class);

    private static final Map<String, String> TIPO_TEMPLATE = Map.of(
        "CONVITE_CADASTRO",     "email/convite-cadastro",
        "DOCUMENTO_REPROVADO",  "email/documento-reprovado",
        "MEDICO_ATIVADO",       "email/medico-ativado",
        "NOTA_FISCAL_EMITIDA",  "email/nota-fiscal-emitida",
        "ALERTA_TETO_FISCAL",   "email/alerta-teto-fiscal",
        "REPASSE_EFETUADO",     "email/repasse-efetuado"
    );

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String emailFrom;

    public EmailTemplateService(
            JavaMailSender mailSender,
            SpringTemplateEngine templateEngine,
            @Value("${app.email-from:noreply@pinsaude.com.br}") String emailFrom) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
        this.emailFrom      = emailFrom;
    }

    public void enviar(EmailEnvioMessage message) {
        String destinatario = message.destinatario();
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("E-mail sem destinatário — tipo={}", message.tipo());
            return;
        }
        String template = TIPO_TEMPLATE.get(message.tipo());
        if (template == null) {
            log.warn("Template não encontrado para tipo={}", message.tipo());
            return;
        }
        try {
            Context ctx = new Context(Locale.forLanguageTag("pt-BR"));
            if (message.dados() != null) ctx.setVariables(message.dados());

            String body = templateEngine.process(template, ctx);

            MimeMessage mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(emailFrom);
            helper.setTo(destinatario);
            helper.setSubject(message.assunto());
            helper.setText(body, true);

            mailSender.send(mime);
            log.info("E-mail enviado: tipo={} destinatario={}", message.tipo(), destinatario);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail: tipo={} dest={}", message.tipo(), destinatario, e);
        }
    }
}
