package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.domain.StatusMedico;
import br.com.pinsaude.onboarding.dto.CandidaturaPublicaRequest;
import br.com.pinsaude.onboarding.repository.DadosCivisMedicoRepository;
import br.com.pinsaude.onboarding.repository.MedicoRepository;
import br.com.pinsaude.onboarding.service.CryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
    @Autowired CryptoService cryptoService;

    @BeforeEach
    void limparBanco() {
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

    // ── helpers ───────────────────────────────────────────────────────────────

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
