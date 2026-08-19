package br.com.pinsaude.portal.controller;

import br.com.pinsaude.portal.config.SecurityConfig;
import br.com.pinsaude.portal.dto.DashboardResponse;
import br.com.pinsaude.portal.dto.NotaPortalResponse;
import br.com.pinsaude.portal.service.PortalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PortalMedicoController.class)
@Import(SecurityConfig.class)
class PortalMedicoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortalService service;

    @Test
    void dashboard_comRoleMedico_retorna200() throws Exception {
        UUID medicoId = UUID.randomUUID();
        when(service.resolveMedicoId("medico@test.com")).thenReturn(medicoId);
        when(service.getDashboard(medicoId)).thenReturn(
                new DashboardResponse(850000L, 170000L, 1000000L, 1L, 1L, List.of(), List.of()));

        mockMvc.perform(get("/api/portal/dashboard")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_medico"))
                        .jwt(j -> j.claim("email", "medico@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoDisponivelCentavos").value(850000));
    }

    @Test
    void dashboard_semRoleMedico_retorna403() throws Exception {
        mockMvc.perform(get("/api/portal/dashboard")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_gestao"))
                        .jwt(j -> j.claim("email", "gestao@test.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void notas_comRoleMedico_retornaLista() throws Exception {
        UUID medicoId = UUID.randomUUID();
        when(service.resolveMedicoId("medico@test.com")).thenReturn(medicoId);
        when(service.getNotas(any(), any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/portal/notas")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_medico"))
                        .jwt(j -> j.claim("email", "medico@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void producao_comRoleMedico_retornaLista() throws Exception {
        UUID medicoId = UUID.randomUUID();
        when(service.resolveMedicoId("medico@test.com")).thenReturn(medicoId);
        when(service.getProducoes(any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/portal/producao")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_medico"))
                        .jwt(j -> j.claim("email", "medico@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void extrato_semImplementacao_retornaListaVazia() throws Exception {
        mockMvc.perform(get("/api/portal/extrato")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_medico"))
                        .jwt(j -> j.claim("email", "medico@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void repasses_semImplementacao_retornaListaVazia() throws Exception {
        mockMvc.perform(get("/api/portal/repasses")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_medico"))
                        .jwt(j -> j.claim("email", "medico@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void notas_semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/portal/notas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tomadores_comRoleMedico_retornaLista() throws Exception {
        UUID medicoId = UUID.randomUUID();
        when(service.resolveMedicoId("medico@test.com")).thenReturn(medicoId);
        when(service.getTomadoresDoMedico(medicoId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/portal/tomadores")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_medico"))
                        .jwt(j -> j.claim("email", "medico@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void tomadores_semRoleMedico_retorna403() throws Exception {
        mockMvc.perform(get("/api/portal/tomadores")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_gestao"))
                        .jwt(j -> j.claim("email", "gestao@test.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void tomadores_semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/portal/tomadores"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setoresDoTomador_comRoleMedico_retornaLista() throws Exception {
        UUID medicoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        when(service.resolveMedicoId("medico@test.com")).thenReturn(medicoId);
        when(service.getSetoresDoMedicoNoTomador(medicoId, tomadorId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/portal/tomadores/{tomadorId}/setores", tomadorId)
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_medico"))
                        .jwt(j -> j.claim("email", "medico@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void setoresDoTomador_semRoleMedico_retorna403() throws Exception {
        mockMvc.perform(get("/api/portal/tomadores/{tomadorId}/setores", UUID.randomUUID())
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_gestao"))
                        .jwt(j -> j.claim("email", "gestao@test.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void setoresDoTomador_semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/portal/tomadores/{tomadorId}/setores", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
