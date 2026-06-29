package br.com.pinsaude.fiscal.messaging;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmailNotificacaoProducer {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificacaoProducer.class);
    private static final String EMAIL_QUEUE = "email.envio";

    private final RabbitTemplate rabbitTemplate;
    private final String baseUrl;

    public EmailNotificacaoProducer(
            RabbitTemplate rabbitTemplate,
            @Value("${app.base-url:http://localhost:3000}") String baseUrl) {
        this.rabbitTemplate = rabbitTemplate;
        this.baseUrl        = baseUrl;
    }

    public void notificarNotaEmitida(NotaFiscal nota) {
        if (nota.getMedicoId() == null) return; // multi-médico: sem e-mail individual
        var dados = Map.<String, Object>of(
            "numeroNota",   nota.getNumeroNota() != null ? nota.getNumeroNota() : "—",
            "competencia",  nota.getCompetencia(),
            "valorBruto",   nota.getValorBruto(),
            "valorLiquido", nota.getValorLiquidoMedico(),
            "portalUrl",    baseUrl + "/portal/notas",
            "notaId",       nota.getId().toString()
        );
        publicar(new EmailEnvioMessage(
            "NOTA_FISCAL_EMITIDA",
            null,
            nota.getMedicoId().toString(),
            "Pin Saúde — Nota Fiscal Emitida" + (nota.getNumeroNota() != null ? " #" + nota.getNumeroNota() : ""),
            dados
        ));
    }

    public void notificarAlertaTeto(String cnpjId, String emailDestino, int percentualTeto) {
        var dados = Map.<String, Object>of(
            "cnpj",            cnpjId,
            "percentualTeto",  percentualTeto,
            "portalUrl",       baseUrl + "/fiscal/config"
        );
        publicar(new EmailEnvioMessage(
            "ALERTA_TETO_FISCAL",
            emailDestino,
            null,
            "Pin Saúde — Alerta: Teto Fiscal (" + percentualTeto + "% atingido)",
            dados
        ));
    }

    public void notificarRepasseEfetuado(String emailDestino, String nomeMedico, long valorCentavos, String chavePix) {
        var dados = Map.<String, Object>of(
            "nome",        nomeMedico,
            "valor",       valorCentavos,
            "chavePix",    chavePix,
            "portalUrl",   baseUrl + "/portal/extrato"
        );
        publicar(new EmailEnvioMessage(
            "REPASSE_EFETUADO",
            emailDestino,
            null,
            "Pin Saúde — Repasse Efetuado",
            dados
        ));
    }

    private void publicar(EmailEnvioMessage msg) {
        try {
            rabbitTemplate.convertAndSend(EMAIL_QUEUE, msg);
            log.info("Notificação de e-mail enfileirada: tipo={}", msg.tipo());
        } catch (Exception e) {
            log.warn("Falha ao enfileirar notificação tipo={}: {}", msg.tipo(), e.getMessage());
        }
    }
}
