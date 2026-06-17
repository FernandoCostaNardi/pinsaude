package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.Medico;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private final JavaMailSender mailSender;
    private final String emailFrom;
    private final String baseUrl;

    public NotificacaoService(
            JavaMailSender mailSender,
            @Value("${app.email-from:noreply@pinsaude.com.br}") String emailFrom,
            @Value("${app.base-url:http://localhost:3000}") String baseUrl) {
        this.mailSender = mailSender;
        this.emailFrom = emailFrom;
        this.baseUrl = baseUrl;
    }

    public void notificarDocumentoReprovado(Medico medico, String tipoDocumento, String motivo) {
        if (medico.getEmail() == null || medico.getEmail().isBlank()) return;
        try {
            var msg = new SimpleMailMessage();
            msg.setFrom(emailFrom);
            msg.setTo(medico.getEmail());
            msg.setSubject("Pin Saúde — Documento reprovado: " + tipoDocumento);
            msg.setText(
                "Olá, " + medico.getNome() + ",\n\n" +
                "Seu documento " + tipoDocumento + " foi reprovado pela equipe Pin Saúde.\n\n" +
                "Motivo: " + motivo + "\n\n" +
                "Por favor, acesse o portal e reenvie o documento correto.\n\n" +
                "Portal: " + baseUrl + "\n\n" +
                "Equipe Pin Saúde"
            );
            mailSender.send(msg);
            log.info("Notificação de reprovação enviada para médico={} doc={}", medico.getId(), tipoDocumento);
        } catch (Exception e) {
            log.error("Falha ao notificar reprovação de documento para médico={}", medico.getId(), e);
        }
    }

    public void notificarMedicoAtivado(Medico medico) {
        if (medico.getEmail() == null || medico.getEmail().isBlank()) return;
        try {
            var msg = new SimpleMailMessage();
            msg.setFrom(emailFrom);
            msg.setTo(medico.getEmail());
            msg.setSubject("Pin Saúde — Cadastro aprovado! Bem-vindo(a)");
            msg.setText(
                "Olá, " + medico.getNome() + ",\n\n" +
                "Seu cadastro foi aprovado e você agora é um médico ativo na plataforma Pin Saúde!\n\n" +
                "Acesse o portal para começar: " + baseUrl + "\n\n" +
                "Equipe Pin Saúde"
            );
            mailSender.send(msg);
            log.info("Notificação de ativação enviada para médico={}", medico.getId());
        } catch (Exception e) {
            log.error("Falha ao notificar ativação do médico={}", medico.getId(), e);
        }
    }
}
