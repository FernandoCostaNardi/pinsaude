package br.com.pinsaude.faturamento.adapter;

import br.com.pinsaude.faturamento.port.NotaFiscalConsultaPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * Chama GET /api/nfse/status/{producaoId} no serviço Fiscal com o token do próprio usuário
 * (mesmo endpoint que a tela de emissão usa para polling). 404 = produção sem nota.
 * Fiscal roda no mesmo host que o faturamento (o gateway também aponta para localhost:8081).
 */
@Component
public class FiscalNotaConsultaAdapter implements NotaFiscalConsultaPort {

    private static final Logger log = LoggerFactory.getLogger(FiscalNotaConsultaAdapter.class);

    private final RestClient client;

    public FiscalNotaConsultaAdapter(RestClient.Builder builder,
                                     @Value("${pinsaude.servicos.fiscal-url:http://localhost:8081}") String baseUrl) {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(3000);
        rf.setReadTimeout(5000);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(rf).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatusNota(String status) {}

    @Override
    public Optional<String> statusDaNota(UUID producaoId) {
        try {
            StatusNota resp = client.get()
                .uri("/api/nfse/status/{id}", producaoId)
                .header(HttpHeaders.AUTHORIZATION, BearerTokenAtual.header())
                .retrieve()
                .body(StatusNota.class);
            return Optional.ofNullable(resp).map(StatusNota::status);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Consulta de nota no Fiscal falhou para producaoId={}: {}", producaoId, e.getMessage());
            throw new ConsultaIndisponivelException("Fiscal indisponível", e);
        }
    }
}
