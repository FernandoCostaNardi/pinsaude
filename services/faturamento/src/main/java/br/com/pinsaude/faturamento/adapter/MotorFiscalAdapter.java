package br.com.pinsaude.faturamento.adapter;

import br.com.pinsaude.faturamento.port.MotorFiscalPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Chama POST /api/motor-fiscal/calcular no serviço Fiscal com o token do próprio usuário — o mesmo
 * endpoint que as telas de emissão e prévia da nota usam (CBS-21). Assim existe um único cálculo de
 * tributo no sistema (CBS-30).
 */
@Component
public class MotorFiscalAdapter implements MotorFiscalPort {

    private static final Logger log = LoggerFactory.getLogger(MotorFiscalAdapter.class);

    private final RestClient client;

    public MotorFiscalAdapter(RestClient.Builder builder,
                              @Value("${pinsaude.servicos.fiscal-url:http://localhost:8081}") String baseUrl) {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(3000);
        rf.setReadTimeout(5000);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(rf).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErroFiscal(String message) {}

    @Override
    public ResultadoCalculo calcular(String cnpjEmpresa, PedidoCalculo pedido) {
        try {
            ResultadoCalculo r = client.post()
                .uri(b -> {
                    b.path("/api/motor-fiscal/calcular");
                    if (cnpjEmpresa != null && !cnpjEmpresa.isBlank()) b.queryParam("cnpj", cnpjEmpresa);
                    return b.build();
                })
                .header(HttpHeaders.AUTHORIZATION, BearerTokenAtual.header())
                .contentType(MediaType.APPLICATION_JSON)
                .body(pedido)
                .retrieve()
                .body(ResultadoCalculo.class);
            if (r == null) throw new IllegalStateException("Resposta vazia do motor fiscal");
            return r;
        } catch (HttpClientErrorException e) {
            // 4xx do Fiscal (ex.: 422 ano sem cronograma, 400 gestão sem empresa): repassa o motivo.
            String msg = mensagem(e);
            log.info("Motor fiscal recusou o cálculo ({}): {}", e.getStatusCode().value(), msg);
            throw new MotorFiscalException(e.getStatusCode().value(), msg, e);
        } catch (Exception e) {
            log.warn("Motor fiscal indisponível: {}", e.getMessage());
            throw new MotorFiscalException(503, "Serviço fiscal indisponível para calcular os tributos", e);
        }
    }

    private static String mensagem(HttpClientErrorException e) {
        try {
            ErroFiscal erro = e.getResponseBodyAs(ErroFiscal.class);
            if (erro != null && erro.message() != null && !erro.message().isBlank()) return erro.message();
        } catch (Exception ignored) {
            // corpo não é JSON
        }
        return "O serviço fiscal recusou o cálculo dos tributos";
    }
}
