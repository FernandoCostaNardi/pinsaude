package br.com.pinsaude.faturamento.adapter;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Token do usuário da requisição atual, repassado nas chamadas a outros serviços Pin. */
final class BearerTokenAtual {

    private BearerTokenAtual() {}

    static String header() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return "Bearer " + jwt.getToken().getTokenValue();
        }
        throw new IllegalStateException("Requisição sem token JWT");
    }
}
