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

        // Bug real encontrado em produção: um médico pode ter `cnpj_id` no JWT (sincronizado
        // quando ele ganha seu primeiro vínculo com uma Empresa no onboarding — ver
        // "Sincronização de cnpj_id no Keycloak ao Atribuir Vínculo" no CLAUDE.md) — mas esse
        // CNPJ é da empresa faturadora do médico, sem nenhuma relação com o CNPJ do Tomador
        // (hospital/clínica). Sem esse bypass, o RLS de `tomadores` filtrava por
        // `cnpj = <cnpj_id da empresa do médico>` e escondia TODOS os tomadores do médico em
        // `GET /api/tomadores` — mesmo ele estando corretamente alocado via
        // faturamento.medico_tomadores. Médico nunca foi pensado como tenant de uma única
        // empresa aqui: seu isolamento de dados já é garantido na camada de aplicação (filtro
        // explícito por medicoId/medico_tomadores em Producao/Frequencia — ver EPIC-15), nunca
        // por RLS de tenant — mesmo princípio já usado no serviço portal (sem TenantFilter).
        boolean isMedico = jwtToken.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_medico"::equals);

        if (isGestao || isMedico) {
            return "";
        }
        String cnpj = jwtToken.getToken().getClaimAsString("cnpj_id");
        return cnpj != null ? cnpj.replaceAll("\\D", "") : "";
    }
}
