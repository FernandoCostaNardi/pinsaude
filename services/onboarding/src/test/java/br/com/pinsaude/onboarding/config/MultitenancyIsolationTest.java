package br.com.pinsaude.onboarding.config;

import br.com.pinsaude.onboarding.domain.RegimeTributario;
import br.com.pinsaude.onboarding.dto.EmpresaRequest;
import br.com.pinsaude.onboarding.repository.EmpresaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@AutoConfigureMockMvc
@Testcontainers
class MultitenancyIsolationTest {

    private static final String CNPJ_A = "11.222.333/0001-81";
    // 55.666.777/0001-81 é um CNPJ válido (dígitos verificadores confirmados)
    private static final String CNPJ_B = "55.666.777/0001-81";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EmpresaRepository empresaRepository;

    @BeforeEach
    void setupDados() throws Exception {
        empresaRepository.deleteAll();
        criarEmpresa(CNPJ_A, "Clinica Alpha");
        criarEmpresa(CNPJ_B, "Clinica Beta");

        // Cria usuário restrito (não superuser) para testar RLS via JDBC direto.
        // O usuário do Testcontainers é superuser — ele bypassa RLS mesmo com FORCE.
        // svc_rls_test não é superuser, portanto fica sujeito às policies.
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            executarSilencioso(conn,
                "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_rls_test') " +
                "THEN CREATE ROLE svc_rls_test LOGIN PASSWORD 'rls_test'; END IF; END $$");
            conn.createStatement().execute("GRANT USAGE ON SCHEMA onboarding TO svc_rls_test");
            conn.createStatement().execute(
                "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA onboarding TO svc_rls_test");
        }
    }

    // ─── Testes de policy RLS via conexão com usuário não-superuser ──────────

    @Test
    void rls_tenantA_naoVeDadosDeTenantB() throws Exception {
        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, CNPJ_A);
            List<String> cnpjs = listarCnpjsEmpresas(conn);
            assertThat(cnpjs).containsExactly(CNPJ_A);
            assertThat(cnpjs).doesNotContain(CNPJ_B);
        }
    }

    @Test
    void rls_tenantB_naoVeDadosDeTenantA() throws Exception {
        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, CNPJ_B);
            List<String> cnpjs = listarCnpjsEmpresas(conn);
            assertThat(cnpjs).containsExactly(CNPJ_B);
            assertThat(cnpjs).doesNotContain(CNPJ_A);
        }
    }

    @Test
    void rls_tenantVazio_veTodasAsEmpresas() throws Exception {
        // String vazia = bypass (equivalente ao role gestão)
        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, "");
            List<String> cnpjs = listarCnpjsEmpresas(conn);
            assertThat(cnpjs).containsExactlyInAnyOrder(CNPJ_A, CNPJ_B);
        }
    }

    @Test
    void rls_tenantInexistente_naoVeNenhumaEmpresa() throws Exception {
        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, "99.999.999/0001-99");
            List<String> cnpjs = listarCnpjsEmpresas(conn);
            assertThat(cnpjs).isEmpty();
        }
    }

    // ─── Teste de comportamento do filtro via HTTP ────────────────────────────

    @Test
    void gestao_veTodasAsEmpresas() throws Exception {
        mockMvc.perform(get("/api/empresas")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void tenantContextLimpoAposRequisicao() throws Exception {
        mockMvc.perform(get("/api/empresas")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk());

        // Após o request, TenantContext deve estar null (ThreadLocal removido)
        assertThat(TenantContext.get()).isNull();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void criarEmpresa(String cnpj, String razaoSocial) throws Exception {
        EmpresaRequest req = new EmpresaRequest(
            cnpj, razaoSocial, "IM-001", "Sao Paulo", "3550308",
            RegimeTributario.SIMPLES_NACIONAL, null, null, null, null, null, null);
        mockMvc.perform(post("/api/empresas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isCreated());
    }

    private Connection conectarComoRlsTest() throws SQLException {
        // Extrai host e porta do JDBC URL do container e substitui as credenciais
        String url = postgres.getJdbcUrl();
        return DriverManager.getConnection(url, "svc_rls_test", "rls_test");
    }

    private void definirTenant(Connection conn, String cnpj) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            ps.setString(1, cnpj);
            ps.execute();
        }
    }

    private List<String> listarCnpjsEmpresas(Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();
        try (ResultSet rs = conn.createStatement()
                .executeQuery("SELECT cnpj FROM onboarding.empresas ORDER BY cnpj")) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    private void executarSilencioso(Connection conn, String sql) {
        try { conn.createStatement().execute(sql); } catch (Exception ignored) {}
    }
}
