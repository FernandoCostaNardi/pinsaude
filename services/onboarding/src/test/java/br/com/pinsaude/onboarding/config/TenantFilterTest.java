package br.com.pinsaude.onboarding.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantFilterTest {

    private final TenantFilter filter = new TenantFilter();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void gestaoRole_defineTenantComoVazio() throws Exception {
        setAuth("gestao", null);

        filter.doFilterInternal(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            (req, res) -> assertThat(TenantContext.get()).isEqualTo("")
        );
    }

    @Test
    void medicoRole_defineTenantComCnpjDoJwt() throws Exception {
        setAuth("medico", "11.222.333/0001-81");

        filter.doFilterInternal(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            (req, res) -> assertThat(TenantContext.get()).isEqualTo("11.222.333/0001-81")
        );
    }

    @Test
    void semAutenticacao_defineTenantComoVazio() throws Exception {
        SecurityContextHolder.clearContext();

        filter.doFilterInternal(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            (req, res) -> assertThat(TenantContext.get()).isEqualTo("")
        );
    }

    @Test
    void semCnpjIdNoClaim_defineTenantComoVazio() throws Exception {
        setAuth("operacao", null);

        filter.doFilterInternal(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            (req, res) -> assertThat(TenantContext.get()).isEqualTo("")
        );
    }

    @Test
    void tenantLimpoAposRequisicao() throws Exception {
        setAuth("medico", "11.222.333/0001-81");

        filter.doFilterInternal(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            new MockFilterChain()
        );

        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void tenantLimpoAposExcecaoNaCadeia() throws Exception {
        setAuth("medico", "11.222.333/0001-81");

        try {
            filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (req, res) -> { throw new RuntimeException("erro simulado"); }
            );
        } catch (RuntimeException ignored) {}

        assertThat(TenantContext.get()).isNull();
    }

    private void setAuth(String role, String cnpjId) {
        Map<String, Object> claims = cnpjId != null
            ? Map.of("sub", "user-id", "cnpj_id", cnpjId)
            : Map.of("sub", "user-id");

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claims(c -> c.putAll(claims))
            .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(
            jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
