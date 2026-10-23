package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityConfig;
import br.com.pinsaude.fiscal.dto.NotaFiscalStatusResponse;
import br.com.pinsaude.fiscal.service.NfseEmissaoExternaService;
import br.com.pinsaude.fiscal.service.NfseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERFIL-22 — regressão do catálogo perm_* (ADR-004) em NfseController.
 * Um endpoint representativo por cada um dos 6 grupos distintos de @PreAuthorize (PERFIL-18).
 */
@WebMvcTest(NfseController.class)
@Import(SecurityConfig.class)
class NfseControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    NfseService service;

    @MockBean
    NfseEmissaoExternaService emissaoExternaService;

    private static final SimpleGrantedAuthority PERM_NOTAS = new SimpleGrantedAuthority("ROLE_perm_notas");
    private static final SimpleGrantedAuthority PERM_OUTRO_DOMINIO = new SimpleGrantedAuthority("ROLE_perm_notas_lote");

    // ─── Grupo 1: contabil,gestao,operacao,financeiro — GET / (listar) ─────────

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "operacao", "financeiro"})
    void listar_papelLegado_retorna200(String role) throws Exception {
        when(service.listar()).thenReturn(List.of());
        mockMvc.perform(get("/api/nfse")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listar_permNotas_retorna200() throws Exception {
        when(service.listar()).thenReturn(List.of());
        mockMvc.perform(get("/api/nfse").with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isOk());
    }

    @Test
    void listar_medico_retorna403() throws Exception {
        mockMvc.perform(get("/api/nfse")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 2: +medico — GET /status/{producaoId} ───────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "operacao", "financeiro", "medico"})
    void getStatus_papelLegado_retorna200(String role) throws Exception {
        UUID producaoId = UUID.randomUUID();
        when(service.getStatus(producaoId)).thenReturn(mock(NotaFiscalStatusResponse.class));
        mockMvc.perform(get("/api/nfse/status/{producaoId}", producaoId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void getStatus_permNotas_retorna200() throws Exception {
        UUID producaoId = UUID.randomUUID();
        when(service.getStatus(producaoId)).thenReturn(mock(NotaFiscalStatusResponse.class));
        mockMvc.perform(get("/api/nfse/status/{producaoId}", producaoId).with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isOk());
    }

    @Test
    void getStatus_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(get("/api/nfse/status/{producaoId}", UUID.randomUUID()).with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 3: gestao,contabil — POST /{notaId}/aprovar ─────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "contabil"})
    void aprovar_papelLegado_retorna204(String role) throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).aprovar(notaId);
        mockMvc.perform(post("/api/nfse/{notaId}/aprovar", notaId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isNoContent());
    }

    @Test
    void aprovar_permNotas_retorna204() throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).aprovar(notaId);
        mockMvc.perform(post("/api/nfse/{notaId}/aprovar", notaId).with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isNoContent());
    }

    @Test
    void aprovar_operacao_retorna403() throws Exception {
        // Diferente dos grupos 1/2, operacao/financeiro nunca tiveram acesso a aprovar.
        mockMvc.perform(post("/api/nfse/{notaId}/aprovar", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 4: gestao,contabil,financeiro — PUT /{notaId}/cancelar ──────────

    private static final String MOTIVO_BODY = "{\"motivo\":\"Nota emitida com valor incorreto\"}";

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "contabil", "financeiro"})
    void cancelar_papelLegado_retorna204(String role) throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).cancelar(any(), any());
        mockMvc.perform(put("/api/nfse/{notaId}/cancelar", notaId)
                        .contentType(MediaType.APPLICATION_JSON).content(MOTIVO_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelar_permNotas_retorna204() throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).cancelar(any(), any());
        mockMvc.perform(put("/api/nfse/{notaId}/cancelar", notaId)
                        .contentType(MediaType.APPLICATION_JSON).content(MOTIVO_BODY)
                        .with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelar_operacao_retorna403() throws Exception {
        mockMvc.perform(put("/api/nfse/{notaId}/cancelar", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(MOTIVO_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 5: gestao,contabil,operacao — POST /{notaId}/reprocessar ────────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "contabil", "operacao"})
    void reprocessar_papelLegado_retorna204(String role) throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).reprocessarNota(notaId);
        mockMvc.perform(post("/api/nfse/{notaId}/reprocessar", notaId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isNoContent());
    }

    @Test
    void reprocessar_permNotas_retorna204() throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).reprocessarNota(notaId);
        mockMvc.perform(post("/api/nfse/{notaId}/reprocessar", notaId).with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isNoContent());
    }

    @Test
    void reprocessar_financeiro_retorna403() throws Exception {
        // Diferente do grupo 4 (cancelar), financeiro nunca teve acesso a reprocessar.
        mockMvc.perform(post("/api/nfse/{notaId}/reprocessar", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 6: gestao,contabil,operacao,financeiro — GET /excecoes ──────────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "contabil", "operacao", "financeiro"})
    void listarExcecoes_papelLegado_retorna200(String role) throws Exception {
        when(service.listarExcecoes()).thenReturn(List.of());
        mockMvc.perform(get("/api/nfse/excecoes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listarExcecoes_permNotas_retorna200() throws Exception {
        when(service.listarExcecoes()).thenReturn(List.of());
        mockMvc.perform(get("/api/nfse/excecoes").with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isOk());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/nfse"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Emissão externa: contabil,gestao,operacao,financeiro (mesmo grupo de /emitir) ──

    private static final String DADOS_EMISSAO_EXTERNA = """
        {"nota":{"producaoId":"%s","tomadorId":"%s","competencia":"2026-10","valorBruto":100000,"taxaPin":15000,
                 "cnpjEmpresaEmissora":"11.222.333/0001-81"},
         "numeroNota":"2026123","dataEmissao":"2026-10-20"}
        """.formatted(UUID.randomUUID(), UUID.randomUUID());

    private static MockMultipartFile dadosPart(String json) {
        return new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
    }

    private static MockMultipartFile pdfPart() {
        return new MockMultipartFile("pdf", "nota.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4".getBytes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "operacao", "financeiro"})
    void emissaoExterna_papelLegado_retorna201(String role) throws Exception {
        when(emissaoExternaService.registrar(any(), any(), any(), any())).thenReturn(mock(NotaFiscalStatusResponse.class));
        mockMvc.perform(multipart("/api/nfse/emissao-externa")
                        .file(dadosPart(DADOS_EMISSAO_EXTERNA)).file(pdfPart())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isCreated());
    }

    @Test
    void emissaoExterna_medico_retorna403() throws Exception {
        mockMvc.perform(multipart("/api/nfse/emissao-externa")
                        .file(dadosPart(DADOS_EMISSAO_EXTERNA)).file(pdfPart())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void emissaoExterna_semNumero_retorna400() throws Exception {
        String semNumero = DADOS_EMISSAO_EXTERNA.replace("\"numeroNota\":\"2026123\",", "");
        mockMvc.perform(multipart("/api/nfse/emissao-externa")
                        .file(dadosPart(semNumero)).file(pdfPart())
                        .with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anexarArquivos_permNotas_retorna200() throws Exception {
        UUID notaId = UUID.randomUUID();
        when(emissaoExternaService.anexarArquivos(any(), any(), any())).thenReturn(mock(NotaFiscalStatusResponse.class));
        mockMvc.perform(multipart("/api/nfse/{notaId}/arquivos", notaId).file(pdfPart())
                        .with(r -> { r.setMethod("PUT"); return r; })
                        .with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isOk());
    }

    // ─── Tenant da nota: JWT cnpj_id ou, sem ele, o CNPJ da empresa emissora ──

    @Test
    void emissaoExterna_semCnpjNoJwt_usaCnpjDaEmpresaEmissora() throws Exception {
        when(emissaoExternaService.registrar(any(), any(), any(), any())).thenReturn(mock(NotaFiscalStatusResponse.class));
        mockMvc.perform(multipart("/api/nfse/emissao-externa")
                        .file(dadosPart(DADOS_EMISSAO_EXTERNA)).file(pdfPart())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
                .andExpect(status().isCreated());
        verify(emissaoExternaService).registrar(any(), any(), any(), eq("11222333000181"));
    }

    @Test
    void emissaoExterna_cnpjNoJwt_temPrioridade() throws Exception {
        when(emissaoExternaService.registrar(any(), any(), any(), any())).thenReturn(mock(NotaFiscalStatusResponse.class));
        mockMvc.perform(multipart("/api/nfse/emissao-externa")
                        .file(dadosPart(DADOS_EMISSAO_EXTERNA)).file(pdfPart())
                        .with(jwt().jwt(j -> j.claim("cnpj_id", "99.888.777/0001-66"))
                                .authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isCreated());
        verify(emissaoExternaService).registrar(any(), any(), any(), eq("99888777000166"));
    }

    @Test
    void emissaoExterna_semCnpjNoJwtNemEmpresa_retorna422() throws Exception {
        String semEmpresa = DADOS_EMISSAO_EXTERNA.replaceAll(",\\s*\"cnpjEmpresaEmissora\":\"[^\"]*\"", "");
        mockMvc.perform(multipart("/api/nfse/emissao-externa")
                        .file(dadosPart(semEmpresa)).file(pdfPart())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emitir_semCnpjNoJwt_usaCnpjDaEmpresaEmissora() throws Exception {
        String body = """
            {"producaoId":"%s","tomadorId":"%s","competencia":"2026-10","valorBruto":100000,"taxaPin":15000,
             "cnpjEmpresaEmissora":"11222333000181"}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/nfse/emitir").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
                .andExpect(status().isAccepted());
        verify(service).emitir(any(), eq("11222333000181"));
    }
}
