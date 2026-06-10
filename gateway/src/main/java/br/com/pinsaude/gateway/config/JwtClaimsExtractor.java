package br.com.pinsaude.gateway.config;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class JwtClaimsExtractor {

    public Mono<String> extractCnpjId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken().getClaimAsString("cnpj_id"));
    }

    public Mono<List<String>> extractRoles() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> {
                    Map<String, Object> realmAccess =
                            ((JwtAuthenticationToken) auth).getToken().getClaimAsMap("realm_access");
                    if (realmAccess == null) return Collections.<String>emptyList();
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) realmAccess.getOrDefault("roles", Collections.emptyList());
                    return roles;
                });
    }

    public Mono<String> extractSubject() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken().getSubject());
    }
}
