package br.com.pinsaude.repasse.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public static boolean isGestao() {
        return hasRole("gestao");
    }

    public static boolean isFinanceiro() {
        return hasRole("financeiro");
    }

    public static boolean isOperacao() {
        return hasRole("operacao");
    }

    public static boolean isContabil() {
        return hasRole("contabil");
    }

    public static boolean isMedico() {
        return hasRole("medico");
    }

    public static String currentMedicoId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        if (!hasRole("medico")) return null;
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken().getSubject();
        }
        return null;
    }

    public static String currentCnpjTenant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken().getClaimAsString("cnpj_id");
        }
        return null;
    }

    private static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
