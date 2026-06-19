package br.com.pinsaude.onboarding.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class JwtClaimsExtractor {

    public String extractCnpjId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            String cnpj = token.getToken().getClaimAsString("cnpj_id");
            return cnpj != null ? cnpj.replaceAll("\\D", "") : null;
        }
        return null;
    }

    public List<String> extractRoles(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            Map<String, Object> realmAccess = token.getToken().getClaimAsMap("realm_access");
            if (realmAccess == null) return Collections.emptyList();
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.getOrDefault("roles", Collections.emptyList());
            return roles;
        }
        return Collections.emptyList();
    }

    public String extractSubject(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken().getSubject();
        }
        return null;
    }
}
