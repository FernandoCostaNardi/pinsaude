package br.com.pinsaude.faturamento.producao;

import br.com.pinsaude.faturamento.domain.ParticipacaoProducao;
import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.StatusProducao;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.port.MedicoLogadoPort;
import br.com.pinsaude.faturamento.port.NotaFiscalConsultaPort;
import br.com.pinsaude.faturamento.port.NotaFiscalConsultaPort.ConsultaIndisponivelException;
import br.com.pinsaude.faturamento.repository.ConciliacaoRepository;
import br.com.pinsaude.faturamento.repository.ParticipacaoRepository;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import br.com.pinsaude.faturamento.service.ExclusaoProducaoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExclusaoProducaoServiceTest {

    @Mock ProducaoRepository producaoRepo;
    @Mock ParticipacaoRepository participacaoRepo;
    @Mock ConciliacaoRepository conciliacaoRepo;
    @Mock NotaFiscalConsultaPort notaFiscal;
    @Mock MedicoLogadoPort medicoLogado;
    @Mock JdbcTemplate jdbc;

    ExclusaoProducaoService service;

    UUID producaoId;
    UUID medicoId;
    Producao producao;
    ParticipacaoProducao participacao;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new ExclusaoProducaoService(producaoRepo, participacaoRepo, conciliacaoRepo,
            notaFiscal, medicoLogado, jdbc, om);

        producaoId = UUID.randomUUID();
        medicoId = UUID.randomUUID();

        Tomador t = new Tomador();
        t.setId(UUID.randomUUID());
        producao = new Producao();
        producao.setId(producaoId);
        producao.setCnpjIdTenant("12345678000199");
        producao.setTomador(t);
        producao.setValorBruto(100000L);
        producao.setCompetencia("2026-10");
        producao.setStatus(StatusProducao.CONFIRMADA);

        participacao = new ParticipacaoProducao();
        participacao.setProducaoId(producaoId);
        participacao.setMedicoId(medicoId);
        participacao.setValorBruto(100000L);

        when(producaoRepo.findById(producaoId)).thenReturn(Optional.of(producao));
        when(participacaoRepo.findByProducaoId(producaoId)).thenReturn(List.of(participacao));
        when(conciliacaoRepo.existsByNotaId(producaoId)).thenReturn(false);
        when(notaFiscal.statusDaNota(producaoId)).thenReturn(Optional.empty());
        when(medicoLogado.medicoIdAtual()).thenReturn(medicoId);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void logarComo(String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
            .claim("email", "usuario@pin.com").subject("sub").build();
        var auths = java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, auths));
    }

    private void assertBloqueado(HttpStatus status, String trecho) {
        assertThatThrownBy(() -> service.excluir(producaoId, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var rse = (ResponseStatusException) e;
                assertThat(rse.getStatusCode()).isEqualTo(status);
                assertThat(rse.getReason()).contains(trecho);
            });
        verify(producaoRepo, never()).delete(any());
    }

    // ─── caminho feliz ───────────────────────────────────────────────────────

    @Test
    void backoffice_semNota_excluiEGravaAuditoria() {
        logarComo("operacao");
        service.excluir(producaoId, "  lançada em duplicidade ");

        verify(jdbc).update(anyString(), eq(producaoId), eq("12345678000199"), eq(producao.getTomador().getId()),
            eq("2026-10"), eq(100000L), eq("CONFIRMADA"), anyString(), eq("lançada em duplicidade"),
            eq("usuario@pin.com"), eq("BACKOFFICE"));
        verify(participacaoRepo).deleteAll(List.of(participacao));
        verify(producaoRepo).delete(producao);
        verify(medicoLogado, never()).medicoIdAtual();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELADA", "REJEITADA"})
    void notaCanceladaOuRejeitada_naoBloqueia(String statusNota) {
        logarComo("gestao");
        when(notaFiscal.statusDaNota(producaoId)).thenReturn(Optional.of(statusNota));
        service.excluir(producaoId, null);
        verify(producaoRepo).delete(producao);
    }

    @Test
    void medicoUnicoParticipante_exclui() {
        logarComo("medico");
        service.excluir(producaoId, null);
        verify(producaoRepo).delete(producao);
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("MEDICO"));
    }

    // ─── bloqueios ───────────────────────────────────────────────────────────

    @Test
    void producaoInexistente_404() {
        logarComo("operacao");
        UUID outro = UUID.randomUUID();
        when(producaoRepo.findById(outro)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.excluir(outro, null)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void emitida_bloqueia() {
        logarComo("operacao");
        producao.setStatus(StatusProducao.EMITIDA);
        assertBloqueado(HttpStatus.UNPROCESSABLE_ENTITY, "NFS-e emitida");
    }

    @Test
    void geradaPorFechamento_bloqueia() {
        logarComo("operacao");
        producao.setFechamentoId(UUID.randomUUID());
        assertBloqueado(HttpStatus.UNPROCESSABLE_ENTITY, "Fechamento por Grupo");
    }

    @Test
    void conciliada_bloqueia() {
        logarComo("operacao");
        when(conciliacaoRepo.existsByNotaId(producaoId)).thenReturn(true);
        assertBloqueado(HttpStatus.UNPROCESSABLE_ENTITY, "conciliada");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDENTE", "PROCESSANDO", "EMITIDA", "ERRO", "AGUARDANDO_VALIDACAO", "AGUARDANDO_EMISSAO_MANUAL"})
    void notaAtivaNoFiscal_bloqueia(String statusNota) {
        logarComo("operacao");
        when(notaFiscal.statusDaNota(producaoId)).thenReturn(Optional.of(statusNota));
        assertBloqueado(HttpStatus.UNPROCESSABLE_ENTITY, "já tem NFS-e");
    }

    @Test
    void fiscalIndisponivel_bloqueiaCom503() {
        logarComo("operacao");
        when(notaFiscal.statusDaNota(producaoId))
            .thenThrow(new ConsultaIndisponivelException("x", new RuntimeException()));
        assertBloqueado(HttpStatus.SERVICE_UNAVAILABLE, "nota fiscal");
    }

    @Test
    void medicoNaoParticipante_403() {
        logarComo("medico");
        when(medicoLogado.medicoIdAtual()).thenReturn(UUID.randomUUID());
        assertBloqueado(HttpStatus.FORBIDDEN, "apenas no seu nome");
    }

    @Test
    void medicoEmProducaoMultiMedico_403() {
        logarComo("medico");
        var outra = new ParticipacaoProducao();
        outra.setProducaoId(producaoId);
        outra.setMedicoId(UUID.randomUUID());
        when(participacaoRepo.findByProducaoId(producaoId)).thenReturn(List.of(participacao, outra));
        assertBloqueado(HttpStatus.FORBIDDEN, "apenas no seu nome");
    }

    @Test
    void portalIndisponivel_bloqueiaCom503() {
        logarComo("medico");
        when(medicoLogado.medicoIdAtual())
            .thenThrow(new ConsultaIndisponivelException("x", new RuntimeException()));
        assertBloqueado(HttpStatus.SERVICE_UNAVAILABLE, "médico logado");
    }
}
