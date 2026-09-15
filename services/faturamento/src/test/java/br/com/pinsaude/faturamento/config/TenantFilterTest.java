package br.com.pinsaude.faturamento.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// TenantFilter.resolveTenant é private — testado via reflection, mesmo espírito de outros
// testes do projeto que usam reflection pra exercitar comportamento sem expor API interna só
// por causa do teste.
class TenantFilterTest {

    private String resolveTenant(Authentication auth) throws Exception {
        TenantFilter filter = new TenantFilter();
        Method m = TenantFilter.class.getDeclaredMethod("resolveTenant", Authentication.class);
        m.setAccessible(true);
        return (String) m.invoke(filter, auth);
    }

    private JwtAuthenticationToken jwtAuth(String role, String cnpjId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("realm_access", Map.of("roles", List.of(role)))
                .claim("cnpj_id", cnpjId)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void resolveTenant_gestao_retornaVazio_bypassaRls() throws Exception {
        assertThat(resolveTenant(jwtAuth("gestao", "12.345.678/0001-99"))).isEmpty();
    }

    // Bug real: médico pode ter cnpj_id no JWT (sincronizado ao ganhar vínculo com uma Empresa
    // no onboarding) — mas esse CNPJ é da empresa faturadora do médico, sem nenhuma relação com
    // o tomador (hospital/clínica). Sem o bypass, GET /api/tomadores escondia TODOS os
    // tomadores do médico via RLS, mesmo ele estando corretamente alocado via medico_tomadores.
    @Test
    void resolveTenant_medico_retornaVazio_bypassaRls_mesmoComCnpjIdNoToken() throws Exception {
        assertThat(resolveTenant(jwtAuth("medico", "00.394.460/0058-87"))).isEmpty();
    }

    @Test
    void resolveTenant_medico_retornaVazio_semCnpjIdNoToken() throws Exception {
        assertThat(resolveTenant(jwtAuth("medico", null))).isEmpty();
    }

    @Test
    void resolveTenant_operacao_retornaCnpjSoDigitos() throws Exception {
        assertThat(resolveTenant(jwtAuth("operacao", "12.345.678/0001-99"))).isEqualTo("12345678000199");
    }

    @Test
    void resolveTenant_financeiro_retornaCnpjSoDigitos() throws Exception {
        assertThat(resolveTenant(jwtAuth("financeiro", "98.765.432/0001-11"))).isEqualTo("98765432000111");
    }

    @Test
    void resolveTenant_semCnpjIdNoToken_naoGestaoNaoMedico_retornaVazio() throws Exception {
        assertThat(resolveTenant(jwtAuth("operacao", null))).isEmpty();
    }

    @Test
    void resolveTenant_authNaoJwt_retornaVazio() throws Exception {
        Authentication auth = new TestingAuthenticationToken("user", "pwd", "ROLE_operacao");
        assertThat(resolveTenant(auth)).isEmpty();
    }
}
