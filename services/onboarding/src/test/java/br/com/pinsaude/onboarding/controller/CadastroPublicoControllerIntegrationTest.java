package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.domain.StatusMedico;
import br.com.pinsaude.onboarding.domain.TipoDocumentoMedico;
import br.com.pinsaude.onboarding.dto.CandidaturaDadosBancariosRequest;
import br.com.pinsaude.onboarding.dto.CandidaturaPublicaRequest;
import br.com.pinsaude.onboarding.dto.DeclaracaoLgpdRequest;
import br.com.pinsaude.onboarding.repository.ChecklistCondutaRepository;
import br.com.pinsaude.onboarding.repository.DadosCivisMedicoRepository;
import br.com.pinsaude.onboarding.repository.DadosBancariosMedicoRepository;
import br.com.pinsaude.onboarding.repository.DeclaracoesLgpdMedicoRepository;
import br.com.pinsaude.onboarding.repository.DocumentoMedicoRepository;
import br.com.pinsaude.onboarding.repository.MedicoRepository;
import br.com.pinsaude.onboarding.service.CryptoService;
import br.com.pinsaude.onboarding.service.KeycloakAdminService;
import br.com.pinsaude.onboarding.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cobre o endpoint público de auto-cadastro (EPIC-14.2). Nenhuma requisição aqui usa
 * jwt() — o objetivo é provar que /api/onboarding/publico/** é acessível SEM token,
 * exatamente como será acessado pelo formulário público real.
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@AutoConfigureMockMvc
@Testcontainers
class CadastroPublicoControllerIntegrationTest {

    // withInitScript cria svc_onboarding/svc_portal antes do Flyway rodar — sem isso,
    // V14/V15 falham com "role svc_onboarding does not exist" (ver EPIC-14.1).
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withInitScript("db/test-roles-init.sql");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MedicoRepository medicoRepo;
    @Autowired DadosCivisMedicoRepository dadosCivisRepo;
    @Autowired DocumentoMedicoRepository documentoRepo;
    @Autowired DadosBancariosMedicoRepository dadosBancariosRepo;
    @Autowired DeclaracoesLgpdMedicoRepository declaracoesLgpdRepo;
    @Autowired ChecklistCondutaRepository checklistRepo;
    @Autowired CryptoService cryptoService;

    // MinIO não faz parte da infra deste teste (só Postgres via Testcontainers) — mockar
    // StorageService evita depender de um MinIO real rodando para testar o round-trip
    // HTTP completo do upload (multipart, permitAll, persistência), sem tornar o teste
    // dependente de infraestrutura externa (rodaria diferente localmente vs CI).
    @MockBean StorageService storageService;

    // Idem para o Keycloak Admin API — sem um Keycloak real no ar neste teste, finalizar()
    // chamaria um servidor inexistente e falharia com 502 antes mesmo de chegar na lógica
    // que este teste quer validar.
    @MockBean KeycloakAdminService keycloakAdminService;

    @BeforeEach
    void limparBanco() {
        declaracoesLgpdRepo.deleteAll();
        dadosBancariosRepo.deleteAll();
        documentoRepo.deleteAll();
        dadosCivisRepo.deleteAll();
        medicoRepo.deleteAll();
    }

    private CandidaturaPublicaRequest requestValido(String cpf, String crm) {
        return new CandidaturaPublicaRequest(
            "Dra. Maria Teste", cpf, crm, "SP", "maria@exemplo.com", "11999998888",
            LocalDate.of(1985, 3, 20), "Brasileira", "Recife/PE", br.com.pinsaude.onboarding.domain.EstadoCivil.SOLTEIRO,
            "Ana Teste", "Jose Teste",
            "Rua das Flores", "123", null, "Boa Viagem", "Recife", "PE", "51020-000",
            "1234567", "SSP", "PE", "9876",
            "Instagram", null,
            List.of("GRADUACAO", "RESIDENCIA"), "Cardiologia", "Consultas, ECG"
        );
    }

    @Test
    void criar_semAutenticacao_retorna201EPersisteComOrigemAutoCadastro() throws Exception {
        String resposta = mockMvc.perform(post("/api/onboarding/publico/candidaturas")
                // SEM .with(jwt(...)) — prova que a rota é realmente pública
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido(cpfValido("111444777"), "54321"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("RASCUNHO"))
            .andExpect(jsonPath("$.estadoCivil").value("SOLTEIRO"))
            .andExpect(jsonPath("$.situacaoFormacao", contains("GRADUACAO", "RESIDENCIA")))
            .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
        Medico salvo = medicoRepo.findById(id).orElseThrow();
        assertThatOrigemEhAutoCadastro(salvo);
        assertThat(dadosCivisRepo.findById(id)).isPresent();
        // Achado no roteiro de teste manual E2E (EPIC-14.9): sem o checklist seedado aqui,
        // a tela de Aprovação nunca exibiria o ChecklistEditor (só renderiza quando
        // medico.checklist != null) — bloqueando a ativação de QUALQUER auto-cadastro.
        assertThat(checklistRepo.findById(id)).isPresent();
    }

    @Test
    void criar_cpfDuplicado_retorna409() throws Exception {
        mockMvc.perform(post("/api/onboarding/publico/candidaturas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido(cpfValido("111444777"), "11111"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/onboarding/publico/candidaturas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido(cpfValido("111444777"), "22222"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.mensagem", containsStringIgnoringCase("falecom@pinsaude.com.br")));
    }

    @Test
    void criar_crmDuplicado_retorna409() throws Exception {
        mockMvc.perform(post("/api/onboarding/publico/candidaturas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido(cpfValido("111444777"), "33333"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/onboarding/publico/candidaturas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido(cpfValido("987654321"), "33333"))))
            .andExpect(status().isConflict());
    }

    @Test
    void criar_camposObrigatoriosFaltando_retorna400() throws Exception {
        String jsonInvalido = "{\"nome\":\"\",\"cpf\":\"\",\"crm\":\"\",\"crmUf\":\"\",\"email\":\"\"}";
        mockMvc.perform(post("/api/onboarding/publico/candidaturas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonInvalido))
            .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_semAutenticacao_atualizaDadosDaCandidatura() throws Exception {
        String resposta = mockMvc.perform(post("/api/onboarding/publico/candidaturas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido(cpfValido("111444777"), "44444"))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        var atualizado = new CandidaturaPublicaRequest(
            "Dra. Maria Atualizada", cpfValido("111444777"), "44444", "SP", "novo@exemplo.com", "11888887777",
            LocalDate.of(1985, 3, 20), "Brasileira", "Recife/PE", br.com.pinsaude.onboarding.domain.EstadoCivil.CASADO_COMUNHAO_PARCIAL,
            "Ana Teste", "Jose Teste",
            "Rua Nova", "456", "Apto 2", "Centro", "Recife", "PE", "51020-000",
            "1234567", "SSP", "PE", "9876",
            "Google", null,
            List.of("ESPECIALIZACAO"), "Cardiologia", "Consultas"
        );

        mockMvc.perform(put("/api/onboarding/publico/candidaturas/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(atualizado)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Dra. Maria Atualizada"))
            .andExpect(jsonPath("$.cidade").value("Recife"))
            .andExpect(jsonPath("$.logradouro").value("Rua Nova"))
            .andExpect(jsonPath("$.estadoCivil").value("CASADO_COMUNHAO_PARCIAL"));
    }

    @Test
    void atualizar_candidaturaJaAtivada_retorna422() throws Exception {
        String resposta = mockMvc.perform(post("/api/onboarding/publico/candidaturas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido(cpfValido("111444777"), "55555"))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        Medico medico = medicoRepo.findById(id).orElseThrow();
        medico.setStatus(StatusMedico.ATIVO);
        medicoRepo.save(medico);

        mockMvc.perform(put("/api/onboarding/publico/candidaturas/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido(cpfValido("111444777"), "55555"))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void buscar_semAutenticacao_retornaDadosDaCandidatura() throws Exception {
        String resposta = mockMvc.perform(post("/api/onboarding/publico/candidaturas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido(cpfValido("111444777"), "66666"))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        mockMvc.perform(get("/api/onboarding/publico/candidaturas/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Dra. Maria Teste"))
            .andExpect(jsonPath("$.rqe").value("9876"));
    }

    @Test
    void buscar_idInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/api/onboarding/publico/candidaturas/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void medicoCriadoManualmente_naoEhVisivelViaEndpointPublico() throws Exception {
        // Simula um médico criado por operação/gestão (fora do fluxo público) — não deve
        // aparecer nem ser editável pelo endpoint público, mesmo sabendo o UUID.
        Medico manual = new Medico();
        manual.setCpfCriptografado(cryptoService.encrypt(cpfValido("111444777")));
        manual.setCpfHash(sha256(cpfValido("111444777")));
        manual.setNome("Dr. Cadastrado Manualmente");
        manual.setCrm("77777");
        manual.setCrmUf("RJ");
        manual.setStatus(StatusMedico.RASCUNHO);
        manual.setOrigemCadastro("MANUAL");
        manual = medicoRepo.save(manual);

        mockMvc.perform(get("/api/onboarding/publico/candidaturas/{id}", manual.getId()))
            .andExpect(status().isNotFound());
    }

    // ── documentos ───────────────────────────────────────────────────────────

    @Test
    void uploadDocumento_semAutenticacao_persisteSemLimiteDeQuantidade() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70001");
        when(storageService.upload(any(), any(), any())).thenReturn("documentos/fake/path.pdf");

        for (int i = 0; i < 3; i++) {
            var arquivo = new MockMultipartFile("arquivo", "titulo" + i + ".pdf",
                "application/pdf", ("conteudo-" + i).getBytes());
            mockMvc.perform(multipart("/api/onboarding/publico/candidaturas/{id}/documentos", id)
                    .file(arquivo)
                    .param("tipo", "ESPECIALIDADES"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ESPECIALIDADES"));
        }

        long total = documentoRepo.findByMedicoId(id).stream()
            .filter(d -> d.getTipo() == TipoDocumentoMedico.ESPECIALIDADES)
            .count();
        assertThat(total).isEqualTo(3);
    }

    @Test
    void uploadDocumento_novosTiposDeDocumento_saoAceitos() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70002");
        when(storageService.upload(any(), any(), any())).thenReturn("documentos/fake/path.pdf");

        for (TipoDocumentoMedico tipo : List.of(
                TipoDocumentoMedico.CERTIDAO_CASAMENTO,
                TipoDocumentoMedico.COMPROVANTE_ENDERECO,
                TipoDocumentoMedico.RQE)) {
            var arquivo = new MockMultipartFile("arquivo", "doc.pdf", "application/pdf", new byte[]{1});
            mockMvc.perform(multipart("/api/onboarding/publico/candidaturas/{id}/documentos", id)
                    .file(arquivo)
                    .param("tipo", tipo.name()))
                .andExpect(status().isCreated());
        }
    }

    @Test
    void uploadDocumento_candidaturaJaAtivada_retorna422() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70003");
        Medico medico = medicoRepo.findById(id).orElseThrow();
        medico.setStatus(StatusMedico.ATIVO);
        medicoRepo.save(medico);

        var arquivo = new MockMultipartFile("arquivo", "crm.pdf", "application/pdf", new byte[]{1});
        mockMvc.perform(multipart("/api/onboarding/publico/candidaturas/{id}/documentos", id)
                .file(arquivo)
                .param("tipo", "CRM"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listarDocumentos_retornaDocumentosEnviados() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70010");
        when(storageService.upload(any(), any(), any())).thenReturn("documentos/fake/path.pdf");
        var arquivo = new MockMultipartFile("arquivo", "crm.pdf", "application/pdf", new byte[]{1});
        mockMvc.perform(multipart("/api/onboarding/publico/candidaturas/{id}/documentos", id)
                .file(arquivo)
                .param("tipo", "CRM"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/onboarding/publico/candidaturas/{id}/documentos", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].tipo").value("CRM"));
    }

    @Test
    void deletarDocumento_documentoDaCandidatura_removeERenderiaDropzone() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70011");
        when(storageService.upload(any(), any(), any())).thenReturn("documentos/fake/path.pdf");
        var arquivo = new MockMultipartFile("arquivo", "crm-errado.pdf", "application/pdf", new byte[]{1});
        String location = mockMvc.perform(multipart("/api/onboarding/publico/candidaturas/{id}/documentos", id)
                .file(arquivo)
                .param("tipo", "CRM"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");
        String docId = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(delete("/api/onboarding/publico/candidaturas/{id}/documentos/{docId}", id, docId))
            .andExpect(status().isNoContent());

        assertThat(documentoRepo.findByMedicoId(id)).isEmpty();
    }

    @Test
    void deletarDocumento_candidaturaJaAtivada_retorna422() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70012");
        when(storageService.upload(any(), any(), any())).thenReturn("documentos/fake/path.pdf");
        var arquivo = new MockMultipartFile("arquivo", "crm.pdf", "application/pdf", new byte[]{1});
        String location = mockMvc.perform(multipart("/api/onboarding/publico/candidaturas/{id}/documentos", id)
                .file(arquivo)
                .param("tipo", "CRM"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");
        String docId = location.substring(location.lastIndexOf('/') + 1);

        Medico medico = medicoRepo.findById(id).orElseThrow();
        medico.setStatus(StatusMedico.ATIVO);
        medicoRepo.save(medico);

        mockMvc.perform(delete("/api/onboarding/publico/candidaturas/{id}/documentos/{docId}", id, docId))
            .andExpect(status().isUnprocessableEntity());
    }

    // ── dados bancários ──────────────────────────────────────────────────────

    @Test
    void atualizarDadosBancarios_semCampoConfirmarAlteracao_persistePix() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70004");
        var req = new CandidaturaDadosBancariosRequest(
            "PIX", br.com.pinsaude.onboarding.domain.TipoPix.EMAIL, "maria@exemplo.com",
            null, null, null, null, null, null);

        mockMvc.perform(put("/api/onboarding/publico/candidaturas/{id}/dados-bancarios", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tipoRecebimento").value("PIX"))
            .andExpect(jsonPath("$.chavePix").value("maria@exemplo.com"));

        assertThat(dadosBancariosRepo.findByMedicoId(id)).isPresent();
    }

    // ── declarações LGPD ─────────────────────────────────────────────────────

    @Test
    void registrarDeclaracaoLgpd_todosAceites_persisteAssinaturaEIp() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70005");
        var req = new DeclaracaoLgpdRequest(true, true, true, true, "Maria Teste");

        mockMvc.perform(post("/api/onboarding/publico/candidaturas/{id}/declaracoes-lgpd", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assinaturaNome").value("Maria Teste"));

        var salvo = declaracoesLgpdRepo.findById(id).orElseThrow();
        assertThat(salvo.isCompleto()).isTrue();
        assertThat(salvo.getIpOrigem()).isNotBlank();
    }

    @Test
    void registrarDeclaracaoLgpd_aceiteFaltando_retorna400() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70006");
        var req = new DeclaracaoLgpdRequest(true, true, false, true, "Maria Teste");

        mockMvc.perform(post("/api/onboarding/publico/candidaturas/{id}/declaracoes-lgpd", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    // ── finalizar ────────────────────────────────────────────────────────────

    @Test
    void finalizar_semDocumentosObrigatorios_retorna422ComPendenciasNaMensagem() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70007");

        mockMvc.perform(post("/api/onboarding/publico/candidaturas/{id}/finalizar", id))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.mensagem", containsString("CRM")))
            .andExpect(jsonPath("$.mensagem", containsString("LGPD")));
    }

    @Test
    void finalizar_completo_retorna200ECriaUsuarioKeycloakDesabilitado() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70008");
        when(storageService.upload(any(), any(), any())).thenReturn("documentos/fake/path.pdf");
        when(keycloakAdminService.createUserDesabilitado(any(), any(), any())).thenReturn("kc-user-abc");

        for (TipoDocumentoMedico tipo : List.of(TipoDocumentoMedico.CRM, TipoDocumentoMedico.COMPROVANTE_ENDERECO)) {
            var arquivo = new MockMultipartFile("arquivo", "doc.pdf", "application/pdf", new byte[]{1});
            mockMvc.perform(multipart("/api/onboarding/publico/candidaturas/{id}/documentos", id)
                    .file(arquivo)
                    .param("tipo", tipo.name()))
                .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/onboarding/publico/candidaturas/{id}/declaracoes-lgpd", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new DeclaracaoLgpdRequest(true, true, true, true, "Maria Teste"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/onboarding/publico/candidaturas/{id}/finalizar", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RASCUNHO"))
            .andExpect(jsonPath("$.mensagem", containsStringIgnoringCase("recebida")));

        Medico medico = medicoRepo.findById(id).orElseThrow();
        assertThat(medico.getKeycloakUserId()).isEqualTo("kc-user-abc");
    }

    @Test
    void finalizar_falhaAoCriarUsuarioKeycloak_retorna502ENaoTravaFinalizacao() throws Exception {
        UUID id = criarCandidatura(cpfValido("111444777"), "70009");
        when(storageService.upload(any(), any(), any())).thenReturn("documentos/fake/path.pdf");
        when(keycloakAdminService.createUserDesabilitado(any(), any(), any()))
            .thenThrow(new RuntimeException("Keycloak indisponível"));

        for (TipoDocumentoMedico tipo : List.of(TipoDocumentoMedico.CRM, TipoDocumentoMedico.COMPROVANTE_ENDERECO)) {
            var arquivo = new MockMultipartFile("arquivo", "doc.pdf", "application/pdf", new byte[]{1});
            mockMvc.perform(multipart("/api/onboarding/publico/candidaturas/{id}/documentos", id)
                    .file(arquivo)
                    .param("tipo", tipo.name()))
                .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/onboarding/publico/candidaturas/{id}/declaracoes-lgpd", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new DeclaracaoLgpdRequest(true, true, true, true, "Maria Teste"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/onboarding/publico/candidaturas/{id}/finalizar", id))
            .andExpect(status().isBadGateway());

        // Estado consistente: sem keycloakUserId, a candidatura continua RASCUNHO e
        // editável — pode tentar finalizar() de novo depois.
        Medico medico = medicoRepo.findById(id).orElseThrow();
        assertThat(medico.getKeycloakUserId()).isNull();
        assertThat(medico.getStatus()).isEqualTo(StatusMedico.RASCUNHO);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID criarCandidatura(String cpf, String crm) throws Exception {
        String resposta = mockMvc.perform(post("/api/onboarding/publico/candidaturas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido(cpf, crm))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private void assertThatOrigemEhAutoCadastro(Medico medico) {
        assertThat(medico.getOrigemCadastro()).isEqualTo("AUTO_CADASTRO");
        assertThat(medico.getStatus()).isEqualTo(StatusMedico.RASCUNHO);
    }

    private static String cpfValido(String base9) {
        int[] firstWeights  = {10, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] secondWeights = {11, 10, 9, 8, 7, 6, 5, 4, 3, 2};
        int d1 = calcDigit(base9, firstWeights);
        int d2 = calcDigit(base9 + d1, secondWeights);
        return base9 + "" + d1 + d2;
    }

    private static int calcDigit(String cpfPart, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (cpfPart.charAt(i) - '0') * weights[i];
        }
        int rem = (sum * 10) % 11;
        return rem == 10 || rem == 11 ? 0 : rem;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
