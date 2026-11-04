package br.com.pinsaude.faturamento.adapter;

import br.com.pinsaude.faturamento.port.MedicoLogadoPort;
import br.com.pinsaude.faturamento.port.NotaFiscalConsultaPort.ConsultaIndisponivelException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/** Chama GET /api/portal/perfil no serviço Portal com o token do médico para descobrir o id dele. */
@Component
public class PortalMedicoLogadoAdapter implements MedicoLogadoPort {

    private static final Logger log = LoggerFactory.getLogger(PortalMedicoLogadoAdapter.class);

    private final RestClient client;

    public PortalMedicoLogadoAdapter(RestClient.Builder builder,
                                     @Value("${pinsaude.servicos.portal-url:http://localhost:8087}") String baseUrl) {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(3000);
        rf.setReadTimeout(5000);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(rf).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Perfil(UUID id) {}

    @Override
    public UUID medicoIdAtual() {
        try {
            Perfil p = client.get()
                .uri("/api/portal/perfil")
                .header(HttpHeaders.AUTHORIZATION, BearerTokenAtual.header())
                .retrieve()
                .body(Perfil.class);
            if (p == null || p.id() == null) throw new IllegalStateException("Perfil sem id");
            return p.id();
        } catch (Exception e) {
            log.warn("Consulta do perfil do médico no Portal falhou: {}", e.getMessage());
            throw new ConsultaIndisponivelException("Portal indisponível", e);
        }
    }
}
