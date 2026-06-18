package br.com.pinsaude.faturamento.adapter;

import br.com.pinsaude.faturamento.dto.ReceitaFederalResponse;
import br.com.pinsaude.faturamento.port.ConsultaCnpjPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class ReceitaFederalAdapter implements ConsultaCnpjPort {

    private static final Logger log = LoggerFactory.getLogger(ReceitaFederalAdapter.class);

    private final RestClient client;

    public ReceitaFederalAdapter(
            RestClient.Builder builder,
            @Value("${receita-federal.base-url:https://brasilapi.com.br}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Optional<ReceitaFederalResponse> consultar(String cnpj) {
        String digits = cnpj.replaceAll("\\D", "");
        try {
            ReceitaFederalResponse response = client.get()
                .uri("/api/cnpj/v1/{cnpj}", digits)
                .retrieve()
                .body(ReceitaFederalResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Consulta Receita Federal falhou para CNPJ {}: {}", digits, e.getMessage());
            return Optional.empty();
        }
    }
}
