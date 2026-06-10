package br.com.pinsaude.gestao.security;

import br.com.pinsaude.gestao.config.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAuth(String role, String subject, String cnpjId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("realm_access", Map.of("roles", List.of(role)))
                .claim("cnpj_id", cnpjId)
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void isGestao_retornaTrue_paraPerfilGestao() {
        setAuth("gestao", "user-1", "12345678000199");
        assertThat(SecurityUtils.isGestao()).isTrue();
    }

    @Test
    void isGestao_retornaFalse_paraOutroPerfil() {
        setAuth("medico", "user-1", "12345678000199");
        assertThat(SecurityUtils.isGestao()).isFalse();
    }

    @Test
    void isFinanceiro_retornaTrue_paraPerfilFinanceiro() {
        setAuth("financeiro", "user-1", "12345678000199");
        assertThat(SecurityUtils.isFinanceiro()).isTrue();
    }

    @Test
    void currentMedicoId_retornaSubject_paraPerfilMedico() {
        setAuth("medico", "medico-uuid-123", "12345678000199");
        assertThat(SecurityUtils.currentMedicoId()).isEqualTo("medico-uuid-123");
    }

    @Test
    void currentMedicoId_retornaNull_paraPerfilNaoMedico() {
        setAuth("financeiro", "user-1", "12345678000199");
        assertThat(SecurityUtils.currentMedicoId()).isNull();
    }

    @Test
    void currentMedicoId_retornaNull_paraPerfilGestao() {
        setAuth("gestao", "user-1", "12345678000199");
        assertThat(SecurityUtils.currentMedicoId()).isNull();
    }

    @Test
    void currentCnpjTenant_retornaCnpj_doToken() {
        setAuth("operacao", "user-1", "98765432000111");
        assertThat(SecurityUtils.currentCnpjTenant()).isEqualTo("98765432000111");
    }

    @Test
    void currentCnpjTenant_retornaNull_semAutenticacao() {
        assertThat(SecurityUtils.currentCnpjTenant()).isNull();
    }
}
