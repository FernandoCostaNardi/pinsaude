package br.com.pinsaude.onboarding.adapter;

import br.com.pinsaude.onboarding.domain.ContratoAssinatura;
import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.port.ContratoAssinaturaPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Simula o envio do contrato ao Clicksign enquanto a integração real não está configurada
 * (CLICKSIGN_ENABLED=false). Mesmo padrão de MockEmissaoNfseAdapter (fiscal): @Primary +
 * @ConditionalOnProperty sobrepõe o adapter real sem exigir nenhuma mudança de código.
 * Cria o registro de contrato direto com status ENVIADO, permitindo que o operador use o botão
 * "Marcar como Assinado" (assinarContratoManual) já existente na tela de aprovação.
 */
@Component
@Primary
@ConditionalOnProperty(name = "clicksign.mock.enabled", havingValue = "true")
public class MockClicksignAdapter implements ContratoAssinaturaPort {

    private static final Logger log = LoggerFactory.getLogger(MockClicksignAdapter.class);

    @Override
    public ContratoAssinatura enviar(Medico medico, String emailMedico) {
        String documentoKey = "MOCK-DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String signatarioKey = "MOCK-SIGNER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        var contrato = new ContratoAssinatura();
        contrato.setMedicoId(medico.getId());
        contrato.setDocumentoKey(documentoKey);
        contrato.setSignatarioKey(signatarioKey);
        contrato.setStatus("ENVIADO");
        contrato.setEnviadoEm(OffsetDateTime.now());

        log.info("[MOCK Clicksign] Envio de contrato simulado — medicoId={} documentoKey={}",
            medico.getId(), documentoKey);
        return contrato;
    }
}
