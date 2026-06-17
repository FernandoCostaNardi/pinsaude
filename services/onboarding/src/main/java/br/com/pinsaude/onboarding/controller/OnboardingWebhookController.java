package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.dto.ClicksignWebhookPayload;
import br.com.pinsaude.onboarding.service.MedicoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboarding/webhooks")
public class OnboardingWebhookController {

    private static final Logger log = LoggerFactory.getLogger(OnboardingWebhookController.class);

    private final MedicoService medicoService;

    public OnboardingWebhookController(MedicoService medicoService) {
        this.medicoService = medicoService;
    }

    @PostMapping("/clicksign")
    public ResponseEntity<Void> clicksignWebhook(@RequestBody ClicksignWebhookPayload payload) {
        try {
            if (payload == null || payload.event() == null || payload.event().data() == null) {
                log.warn("Webhook Clicksign recebido com payload inválido");
                return ResponseEntity.ok().build();
            }

            String eventoNome = payload.event().name();
            String documentoKey = payload.event().data().document() != null
                ? payload.event().data().document().key()
                : null;

            if (documentoKey == null || documentoKey.isBlank()) {
                log.warn("Webhook Clicksign sem documentoKey no payload");
                return ResponseEntity.ok().build();
            }

            log.info("Webhook Clicksign recebido: evento={} documentoKey={}", eventoNome, documentoKey);
            medicoService.processarWebhookClicksign(documentoKey, eventoNome);
        } catch (Exception e) {
            log.error("Erro ao processar webhook Clicksign: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
