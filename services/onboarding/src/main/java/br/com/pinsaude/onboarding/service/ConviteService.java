package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.ConviteMedico;
import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.repository.ConviteMedicoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ConviteService {

    private static final Logger log = LoggerFactory.getLogger(ConviteService.class);

    private final ConviteMedicoRepository conviteRepo;
    private final JavaMailSender mailSender;
    private final String emailFrom;
    private final String baseUrl;
    private final long expiracaoHoras;

    public ConviteService(
            ConviteMedicoRepository conviteRepo,
            JavaMailSender mailSender,
            @Value("${app.email-from:noreply@pinsaude.com.br}") String emailFrom,
            @Value("${app.base-url:http://localhost:3000}") String baseUrl,
            @Value("${app.convite.expiracao-horas:168}") long expiracaoHoras) {
        this.conviteRepo = conviteRepo;
        this.mailSender = mailSender;
        this.emailFrom = emailFrom;
        this.baseUrl = baseUrl;
        this.expiracaoHoras = expiracaoHoras;
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

        try {
            String link = baseUrl + "/onboarding?token=" + token + "&medicoId=" + medico.getId();
            var msg = new SimpleMailMessage();
            msg.setFrom(emailFrom);
            msg.setTo(emailDestino);
            msg.setSubject("Pin Saúde — Convite para completar seu cadastro");
            msg.setText(
                "Olá, " + medico.getNome() + "!\n\n" +
                "Você foi convidado(a) a completar seu cadastro na plataforma Pin Saúde.\n\n" +
                "Clique no link abaixo para acessar:\n" + link + "\n\n" +
                "Este link expira em " + expiracaoHoras + " horas.\n\n" +
                "Atenciosamente,\nEquipe Pin Saúde"
            );
            mailSender.send(msg);

            convite.setStatus("ENVIADO");
            convite.setEnviadoEm(OffsetDateTime.now());
            convite = conviteRepo.save(convite);
            log.info("Convite enviado para {} (medico={})", emailDestino, medico.getId());
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de convite para medico={}: {}", medico.getId(), e.getMessage());
            convite.setStatus("PENDENTE");
            convite = conviteRepo.save(convite);
        }

        return convite;
    }
}
