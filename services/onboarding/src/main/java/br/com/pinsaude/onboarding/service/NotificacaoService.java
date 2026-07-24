package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.config.EmailRabbitConfig;
import br.com.pinsaude.onboarding.config.KeycloakAdminProperties;
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

    // Client público usado pelo frontend (apps/web/src/auth/keycloak.ts) — mesmo valor,
    // duplicado aqui pois o e-mail é montado no onboarding, sem acesso ao build do frontend.
    private static final String KEYCLOAK_CLIENT_ID = "pinsaude-web";

    private final RabbitTemplate rabbitTemplate;
    private final String baseUrl;
    private final KeycloakAdminProperties keycloakProps;

    public NotificacaoService(
            RabbitTemplate rabbitTemplate,
            @Value("${app.base-url:http://localhost:3000}") String baseUrl,
            KeycloakAdminProperties keycloakProps) {
        this.rabbitTemplate = rabbitTemplate;
        this.baseUrl = baseUrl;
        this.keycloakProps = keycloakProps;
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
            "portalUrl", baseUrl,
            "primeiroAcessoUrl", primeiroAcessoUrl()
        );
        publicar(new EmailEnvioMessage(
            "MEDICO_ATIVADO",
            medico.getEmail(),
            medico.getId().toString(),
            "Pin Saúde — Cadastro aprovado! Bem-vindo(a)",
            dados
        ));
    }

    // Link nativo do Keycloak para o médico definir a senha pela primeira vez — médicos de
    // auto-cadastro (EPIC-14) nascem com requiredActions=[UPDATE_PASSWORD, VERIFY_EMAIL] e
    // nenhuma senha (createUserDesabilitado não passa por sendInvitationEmail/execute-actions-
    // email, que só existe no KeycloakAdminService do gestao). Incluído sempre — inofensivo
    // para médicos que já têm senha (via convite manual do gestao), útil para quem não tem.
    private String primeiroAcessoUrl() {
        return keycloakProps.serverUrl() + "/realms/" + keycloakProps.realm()
            + "/login-actions/reset-credentials?client_id=" + KEYCLOAK_CLIENT_ID;
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
