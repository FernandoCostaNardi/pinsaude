package br.com.pinsaude.faturamento.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            TenantContext.set(resolveTenant(auth));
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveTenant(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwtToken)) {
            return "";
        }
        boolean isGestao = jwtToken.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_gestao"::equals);

        if (isGestao) {
            return "";
        }
        String cnpj = jwtToken.getToken().getClaimAsString("cnpj_id");
        return cnpj != null ? cnpj.replaceAll("\\D", "") : "";
    }
}
