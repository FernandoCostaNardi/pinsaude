package br.com.pinsaude.fiscal.adapter;

import br.com.pinsaude.fiscal.port.RecebimentosPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * APU-01 — chama GET /api/conciliacao/recebimentos no Faturamento com o token do próprio usuário
 * (mesmo padrão do Faturamento → Fiscal da exclusão de produção). O RLS do Faturamento limita à
 * empresa do token; a gestão recebe todas e a apuração filtra pelas notas da empresa.
 */
@Component
public class FaturamentoRecebimentosAdapter implements RecebimentosPort {

    private static final Logger log = LoggerFactory.getLogger(FaturamentoRecebimentosAdapter.class);

    private final RestClient client;

    public FaturamentoRecebimentosAdapter(RestClient.Builder builder,
                                          @Value("${pinsaude.servicos.faturamento-url:http://localhost:8082}") String baseUrl) {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(3000);
        rf.setReadTimeout(10000);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(rf).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecebimentoJson(UUID producaoId, String recebidoEm, long valorRecebido) {}

    @Override
    public List<Recebimento> recebidosEntre(LocalDate de, LocalDate ate) {
        try {
            RecebimentoJson[] resp = client.get()
                .uri(u -> u.path("/api/conciliacao/recebimentos")
                    .queryParam("de", de.toString())
                    .queryParam("ate", ate.toString())
                    .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(RecebimentoJson[].class);
            if (resp == null) return List.of();
            return Arrays.stream(resp)
                .filter(r -> r.producaoId() != null && r.recebidoEm() != null)
                .map(r -> new Recebimento(r.producaoId(), LocalDate.parse(r.recebidoEm()), r.valorRecebido()))
                .toList();
        } catch (Exception e) {
            log.warn("Consulta de recebimentos no Faturamento falhou ({} a {}): {}", de, ate, e.getMessage());
            throw new RecebimentosIndisponiveisException("Faturamento indisponível", e);
        }
    }

    private static String bearer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return "Bearer " + jwt.getToken().getTokenValue();
        }
        throw new IllegalStateException("Requisição sem token JWT");
    }
}
