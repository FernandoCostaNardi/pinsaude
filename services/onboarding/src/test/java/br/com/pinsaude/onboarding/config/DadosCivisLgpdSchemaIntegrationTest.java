package br.com.pinsaude.onboarding.config;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a migration V18-V21 (EPIC-14.1): novas tabelas dados_civis_medico e
 * declaracoes_lgpd_medico, novos valores de TipoDocumentoMedico, e os novos
 * campos origem_cadastro/keycloak_user_id em medicos — incluindo o isolamento
 * RLS das duas tabelas novas (mesmo padrão de checklist_conduta/dados_bancarios_medico).
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@Testcontainers
class DadosCivisLgpdSchemaIntegrationTest {

    private static final String CNPJ_A = "11.222.333/0001-81";
    // 55.666.777/0001-81 é um CNPJ válido (dígitos verificadores confirmados)
    private static final String CNPJ_B = "55.666.777/0001-81";

    // withInitScript cria svc_onboarding/svc_portal (ver test-roles-init.sql) antes do
    // Flyway rodar — sem isso, V14/V15 falham com "role svc_onboarding does not exist"
    // porque esse container efêmero não tem as roles que tools/db/init.sql cria no Postgres real.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withInitScript("db/test-roles-init.sql");

    @Autowired EmpresaRepository empresaRepo;
    @Autowired MedicoRepository medicoRepo;
    @Autowired VinculoMedicoEmpresaRepository vinculoRepo;
    @Autowired DadosCivisMedicoRepository dadosCivisRepo;
    @Autowired DeclaracoesLgpdMedicoRepository declaracoesRepo;
    @Autowired DocumentoMedicoRepository documentoRepo;

    private UUID empresaAId;
    private UUID medicoId;

    @BeforeEach
    void setupDados() throws Exception {
        declaracoesRepo.deleteAll();
        dadosCivisRepo.deleteAll();
        documentoRepo.deleteAll();
        vinculoRepo.deleteAll();
        medicoRepo.deleteAll();
        empresaRepo.deleteAll();

        Empresa empresaA = new Empresa();
        empresaA.setCnpj(CNPJ_A);
        empresaA.setRazaoSocial("Clinica Alpha");
        empresaA.setRegimeTributario(RegimeTributario.SIMPLES_NACIONAL);
        empresaA = empresaRepo.save(empresaA);
        empresaAId = empresaA.getId();

        Empresa empresaB = new Empresa();
        empresaB.setCnpj(CNPJ_B);
        empresaB.setRazaoSocial("Clinica Beta");
        empresaB.setRegimeTributario(RegimeTributario.SIMPLES_NACIONAL);
        empresaRepo.save(empresaB);

        Medico medico = new Medico();
        medico.setCpfCriptografado(new byte[]{1, 2, 3});
        medico.setNome("Dra. Maria Teste");
        medico.setCrm("54321");
        medico.setCrmUf("SP");
        medico.setOrigemCadastro("AUTO_CADASTRO");
        medico = medicoRepo.save(medico);
        medicoId = medico.getId();

        VinculoMedicoEmpresa vinculo = new VinculoMedicoEmpresa(
            new VinculoMedicoEmpresaId(medicoId, empresaAId), StatusSocietario.ATIVO);
        vinculoRepo.save(vinculo);

        // Cria usuário restrito (não superuser) para testar RLS via JDBC direto.
        // O usuário do Testcontainers é superuser — ele bypassa RLS mesmo com FORCE.
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

    // ─── Medico — origem_cadastro / keycloak_user_id (V21) ───────────────────

    @Test
    void medico_persisteOrigemCadastroEKeycloakUserId() {
        Medico m = medicoRepo.findById(medicoId).orElseThrow();
        assertThat(m.getOrigemCadastro()).isEqualTo("AUTO_CADASTRO");
        assertThat(m.getKeycloakUserId()).isNull();

        m.setKeycloakUserId("kc-user-123");
        medicoRepo.save(m);

        Medico reloaded = medicoRepo.findById(medicoId).orElseThrow();
        assertThat(reloaded.getKeycloakUserId()).isEqualTo("kc-user-123");
    }

    @Test
    void medico_origemCadastroPadraoManual() {
        Medico outro = new Medico();
        outro.setCpfCriptografado(new byte[]{9, 9, 9});
        outro.setNome("Dr. Outro");
        outro.setCrm("11111");
        outro.setCrmUf("RJ");
        outro = medicoRepo.save(outro);

        assertThat(medicoRepo.findById(outro.getId()).orElseThrow().getOrigemCadastro())
            .isEqualTo("MANUAL");
    }

    // ─── dados_civis_medico (V18) ─────────────────────────────────────────────

    @Test
    void dadosCivisMedico_roundTripCompletoComEnderecoArrayEEnum() {
        DadosCivisMedico dc = new DadosCivisMedico(medicoId);
        dc.setDataNascimento(LocalDate.of(1985, 3, 20));
        dc.setNacionalidade("Brasileira");
        dc.setNaturalidade("Recife/PE");
        dc.setEstadoCivil(EstadoCivil.CASADO_COMUNHAO_PARCIAL);
        dc.setNomeMae("Ana Teste");
        dc.setNomePai("Jose Teste");
        dc.setLogradouro("Rua das Flores");
        dc.setNumero("123");
        dc.setBairro("Boa Viagem");
        dc.setCidade("Recife");
        dc.setUf("PE");
        dc.setCep("51020-000");
        dc.setRgNumero("1234567");
        dc.setRgOrgaoExpedidor("SSP");
        dc.setRgUf("PE");
        dc.setRqe("9876");
        dc.setCanalOrigem("Instagram");
        dc.setSituacaoFormacao(new String[]{"GRADUACAO", "RESIDENCIA", "TITULO_ESPECIALISTA"});
        dc.setAreasAtuacao("Cardiologia, Clinica Geral");
        dc.setProcedimentosRealiza("Consultas, ECG");
        dadosCivisRepo.save(dc);

        DadosCivisMedico salvo = dadosCivisRepo.findById(medicoId).orElseThrow();
        assertThat(salvo.getEstadoCivil()).isEqualTo(EstadoCivil.CASADO_COMUNHAO_PARCIAL);
        assertThat(salvo.getSituacaoFormacao())
            .containsExactly("GRADUACAO", "RESIDENCIA", "TITULO_ESPECIALISTA");
        assertThat(salvo.getCidade()).isEqualTo("Recife");
        assertThat(salvo.getRqe()).isEqualTo("9876");
        assertThat(salvo.getNomeIndicador()).isNull();
    }

    @Test
    void dadosCivisMedico_canalIndicacaoComNomeIndicador() {
        DadosCivisMedico dc = new DadosCivisMedico(medicoId);
        dc.setCanalOrigem("Indicação");
        dc.setNomeIndicador("Dr. Fulano de Tal");
        dadosCivisRepo.save(dc);

        DadosCivisMedico salvo = dadosCivisRepo.findById(medicoId).orElseThrow();
        assertThat(salvo.getCanalOrigem()).isEqualTo("Indicação");
        assertThat(salvo.getNomeIndicador()).isEqualTo("Dr. Fulano de Tal");
    }

    // ─── declaracoes_lgpd_medico (V19) ───────────────────────────────────────

    @Test
    void declaracoesLgpdMedico_roundTripEIsCompleto() {
        DeclaracoesLgpdMedico d = new DeclaracoesLgpdMedico(medicoId);
        assertThat(d.isCompleto()).isFalse();

        d.setAceiteDeclaracaoVeracidade(true);
        d.setAutorizacaoUsoDados(true);
        d.setAutorizacaoCompartilhamento(true);
        d.setAvisoPrivacidadeLido(true);
        d.setAssinaturaNome("Maria Teste");
        d.setAssinadoEm(OffsetDateTime.now());
        d.setIpOrigem("200.100.50.25");
        declaracoesRepo.save(d);

        DeclaracoesLgpdMedico salvo = declaracoesRepo.findById(medicoId).orElseThrow();
        assertThat(salvo.isCompleto()).isTrue();
        assertThat(salvo.getAssinaturaNome()).isEqualTo("Maria Teste");
        assertThat(salvo.getIpOrigem()).isEqualTo("200.100.50.25");
    }

    // ─── TipoDocumentoMedico — novos valores (V20) ───────────────────────────

    @Test
    void documentoMedico_aceitaNovosTiposDeCertidaoComprovanteERqe() {
        for (TipoDocumentoMedico tipo : List.of(
                TipoDocumentoMedico.CERTIDAO_CASAMENTO,
                TipoDocumentoMedico.COMPROVANTE_ENDERECO,
                TipoDocumentoMedico.RQE)) {
            DocumentoMedico doc = new DocumentoMedico();
            doc.setMedicoId(medicoId);
            doc.setTipo(tipo);
            doc.setNomeArquivo("arquivo-" + tipo + ".pdf");
            doc.setCaminhoStorage("documentos/" + medicoId + "/" + tipo);
            documentoRepo.save(doc);
        }

        List<DocumentoMedico> docs = documentoRepo.findByMedicoId(medicoId);
        assertThat(docs).extracting(DocumentoMedico::getTipo)
            .containsExactlyInAnyOrder(
                TipoDocumentoMedico.CERTIDAO_CASAMENTO,
                TipoDocumentoMedico.COMPROVANTE_ENDERECO,
                TipoDocumentoMedico.RQE);
    }

    // ─── RLS — dados_civis_medico / declaracoes_lgpd_medico ──────────────────

    @Test
    void rls_dadosCivis_tenantA_veSeuMedico_tenantB_naoVe() throws Exception {
        DadosCivisMedico dc = new DadosCivisMedico(medicoId);
        dc.setNacionalidade("Brasileira");
        dadosCivisRepo.save(dc);

        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, CNPJ_A);
            assertThat(contar(conn, "dados_civis_medico", medicoId)).isEqualTo(1);
        }
        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, CNPJ_B);
            assertThat(contar(conn, "dados_civis_medico", medicoId)).isEqualTo(0);
        }
        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, "");
            assertThat(contar(conn, "dados_civis_medico", medicoId)).isEqualTo(1);
        }
    }

    @Test
    void rls_declaracoesLgpd_seguemMesmoIsolamentoDeDadosCivis() throws Exception {
        DeclaracoesLgpdMedico d = new DeclaracoesLgpdMedico(medicoId);
        declaracoesRepo.save(d);

        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, CNPJ_A);
            assertThat(contar(conn, "declaracoes_lgpd_medico", medicoId)).isEqualTo(1);
        }
        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, CNPJ_B);
            assertThat(contar(conn, "declaracoes_lgpd_medico", medicoId)).isEqualTo(0);
        }
    }

    @Test
    void rls_insertPermiteAntesDoVinculoExistir() throws Exception {
        // Médico auto-cadastrado ainda sem vínculo — o INSERT em dados_civis_medico
        // não pode ser bloqueado pela policy (WITH CHECK true), mesmo com um tenant
        // qualquer definido na sessão.
        Medico semVinculo = new Medico();
        semVinculo.setCpfCriptografado(new byte[]{7, 7, 7});
        semVinculo.setNome("Dr. Sem Vinculo");
        semVinculo.setCrm("22222");
        semVinculo.setCrmUf("BA");
        semVinculo.setOrigemCadastro("AUTO_CADASTRO");
        semVinculo = medicoRepo.save(semVinculo);
        UUID semVinculoId = semVinculo.getId();

        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, CNPJ_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO onboarding.dados_civis_medico (medico_id, nacionalidade) VALUES (?, ?)")) {
                ps.setObject(1, semVinculoId);
                ps.setString(2, "Brasileira");
                ps.executeUpdate();
            }
        }

        // Sem vínculo, o registro só é visível para quem tem tenant vazio (gestão) —
        // comportamento esperado e documentado no plano, não é bug.
        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, CNPJ_A);
            assertThat(contar(conn, "dados_civis_medico", semVinculoId)).isEqualTo(0);
        }
        try (Connection conn = conectarComoRlsTest()) {
            definirTenant(conn, "");
            assertThat(contar(conn, "dados_civis_medico", semVinculoId)).isEqualTo(1);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Connection conectarComoRlsTest() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), "svc_rls_test", "rls_test");
    }

    private void definirTenant(Connection conn, String cnpj) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            ps.setString(1, cnpj);
            ps.execute();
        }
    }

    private int contar(Connection conn, String tabela, UUID medicoId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM onboarding." + tabela + " WHERE medico_id = ?")) {
            ps.setObject(1, medicoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void executarSilencioso(Connection conn, String sql) {
        try { conn.createStatement().execute(sql); } catch (Exception ignored) {}
    }
}
