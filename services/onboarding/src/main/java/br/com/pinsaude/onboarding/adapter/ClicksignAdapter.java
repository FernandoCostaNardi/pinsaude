package br.com.pinsaude.onboarding.adapter;

import br.com.pinsaude.onboarding.domain.ContratoAssinatura;
import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.port.ContratoAssinaturaPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class ClicksignAdapter implements ContratoAssinaturaPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String accessToken;
    private final String baseUrl;
    private final String templateKey;
    private final boolean enabled;

    public ClicksignAdapter(
            @Value("${clicksign.base-url:https://sandbox.clicksign.com}") String baseUrl,
            @Value("${clicksign.access-token:}") String accessToken,
            @Value("${clicksign.template-key:}") String templateKey,
            @Value("${clicksign.enabled:false}") boolean enabled) {
        this.baseUrl = baseUrl;
        this.accessToken = accessToken;
        this.templateKey = templateKey;
        this.enabled = enabled;
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    @Override
    public ContratoAssinatura enviar(Medico medico, String emailMedico) throws Exception {
        if (!enabled || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Integração Clicksign não está configurada. " +
                "Defina CLICKSIGN_ENABLED=true, CLICKSIGN_ACCESS_TOKEN e CLICKSIGN_TEMPLATE_KEY.");
        }

        String documentoKey = criarDocumento(medico);
        String[] signatarioInfo = adicionarSignatario(documentoKey, medico.getNome(), emailMedico);
        String linkAssinatura = signatarioInfo[1];

        var contrato = new ContratoAssinatura();
        contrato.setMedicoId(medico.getId());
        contrato.setDocumentoKey(documentoKey);
        contrato.setSignatarioKey(signatarioInfo[0]);
        contrato.setLinkAssinatura(linkAssinatura);
        contrato.setStatus("ENVIADO");
        contrato.setEnviadoEm(OffsetDateTime.now());
        return contrato;
    }

    private String criarDocumento(Medico medico) throws Exception {
        String path = "/contratos/medico-" + medico.getId() + ".docx";
        Map<String, Object> body = Map.of(
            "document", Map.of("path", path)
        );
        String json = objectMapper.writeValueAsString(body);

        String response = restClient.post()
            .uri("/api/v2/templates/{key}/documents?access_token={token}", templateKey, accessToken)
            .header("Content-Type", "application/json")
            .body(json)
            .retrieve()
            .body(String.class);

        JsonNode node = objectMapper.readTree(response);
        return node.path("document").path("key").asText();
    }

    private String[] adicionarSignatario(String documentoKey, String nome, String email) throws Exception {
        Map<String, Object> signer = Map.of(
            "name", nome,
            "email", email,
            "auths", new String[]{"email"},
            "sign_as", "contractee"
        );
        Map<String, Object> body = Map.of("signer", signer);
        String json = objectMapper.writeValueAsString(body);

        String response = restClient.post()
            .uri("/api/v2/documents/{key}/signers?access_token={token}", documentoKey, accessToken)
            .header("Content-Type", "application/json")
            .body(json)
            .retrieve()
            .body(String.class);

        JsonNode node = objectMapper.readTree(response);
        String signatarioKey = node.path("signer").path("key").asText();
        String shortLink = node.path("signer").path("short_link").asText();
        return new String[]{signatarioKey, shortLink};
    }
}
