package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.LoteEmissao;
import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.LoteProgressoResponse;
import br.com.pinsaude.fiscal.messaging.NfseEmissaoMessage;
import br.com.pinsaude.fiscal.messaging.NfseEmissaoProducer;
import br.com.pinsaude.fiscal.repository.LoteEmissaoRepository;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NfseBatchServiceTest {

    @Mock LoteEmissaoRepository loteRepo;
    @Mock NotaFiscalRepository  notaRepo;
    @Mock NfseEmissaoProducer   producer;

    @InjectMocks NfseBatchService batchService;

    private static final String COMPETENCIA = "2026-05";

    // --- Critério: lock distribuído — 409 se lote já em andamento ---

    @Test
    void emitirLote_loteJaEmAndamento_lanca409() {
        when(loteRepo.existsByCompetenciaAndStatus(COMPETENCIA, "EM_ANDAMENTO")).thenReturn(true);

        assertThatThrownBy(() -> batchService.emitirLote(COMPETENCIA))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");

        verify(loteRepo, never()).save(any());
        verify(producer, never()).enviar(any());
    }

    // --- Critério: sem notas pendentes → cria lote vazio ---

    @Test
    void emitirLote_semNotasPendentes_criLoteVazioComTotal0() throws Exception {
        when(loteRepo.existsByCompetenciaAndStatus(COMPETENCIA, "EM_ANDAMENTO")).thenReturn(false);
        when(notaRepo.findByCompetenciaAndStatusIn(eq(COMPETENCIA), anyList())).thenReturn(List.of());
        when(loteRepo.save(any())).thenAnswer(inv -> {
            LoteEmissao l = inv.getArgument(0, LoteEmissao.class);
            setId(l, UUID.randomUUID());
            return l;
        });

        LoteProgressoResponse resp = batchService.emitirLote(COMPETENCIA);

        assertThat(resp.total()).isZero();
        assertThat(resp.competencia()).isEqualTo(COMPETENCIA);
        assertThat(resp.status()).isEqualTo("EM_ANDAMENTO");
        verify(producer, never()).enviar(any());
    }

    // --- Critério: idempotência — notas PENDENTE são enfileiradas sem alterar status ---

    @Test
    void emitirLote_comNotasPendentes_publicaTodasSemAlterarStatus() throws Exception {
        UUID notaId1 = UUID.randomUUID();
        UUID notaId2 = UUID.randomUUID();
        NotaFiscal n1 = notaComStatus(notaId1, StatusNota.PENDENTE);
        NotaFiscal n2 = notaComStatus(notaId2, StatusNota.PENDENTE);

        when(loteRepo.existsByCompetenciaAndStatus(COMPETENCIA, "EM_ANDAMENTO")).thenReturn(false);
        when(notaRepo.findByCompetenciaAndStatusIn(eq(COMPETENCIA), anyList())).thenReturn(List.of(n1, n2));
        when(loteRepo.save(any())).thenAnswer(inv -> {
            LoteEmissao l = inv.getArgument(0, LoteEmissao.class);
            setId(l, UUID.randomUUID());
            return l;
        });

        LoteProgressoResponse resp = batchService.emitirLote(COMPETENCIA);

        assertThat(resp.total()).isEqualTo(2);
        assertThat(resp.emProcessamento()).isEqualTo(2);

        ArgumentCaptor<NfseEmissaoMessage> captor = ArgumentCaptor.forClass(NfseEmissaoMessage.class);
        verify(producer, times(2)).enviar(captor.capture());

        List<UUID> notaIdsPublicados = captor.getAllValues().stream()
            .map(NfseEmissaoMessage::notaId).toList();
        assertThat(notaIdsPublicados).containsExactlyInAnyOrder(notaId1, notaId2);

        // Notas PENDENTE não devem ter status alterado
        verify(notaRepo, never()).save(any());
    }

    // --- Critério: notas ERRO são resetadas para PENDENTE antes de enfileirar ---

    @Test
    void emitirLote_comNotasErro_resetaParaPendenteEPublica() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal notaErro = notaComStatus(notaId, StatusNota.ERRO);

        when(loteRepo.existsByCompetenciaAndStatus(COMPETENCIA, "EM_ANDAMENTO")).thenReturn(false);
        when(notaRepo.findByCompetenciaAndStatusIn(eq(COMPETENCIA), anyList())).thenReturn(List.of(notaErro));
        when(loteRepo.save(any())).thenAnswer(inv -> {
            LoteEmissao l = inv.getArgument(0, LoteEmissao.class);
            setId(l, UUID.randomUUID());
            return l;
        });

        batchService.emitirLote(COMPETENCIA);

        // ERRO → PENDENTE antes de publicar
        assertThat(notaErro.getStatus()).isEqualTo(StatusNota.PENDENTE);
        verify(notaRepo).save(notaErro);
        verify(producer).enviar(any());
    }

    // --- Critério: progresso calculado corretamente ---

    @Test
    void getProgresso_loteEmAndamento_retornaContadoresAtuais() throws Exception {
        UUID loteId = UUID.randomUUID();
        LoteEmissao lote = loteComId(loteId, "EM_ANDAMENTO", 10, 6, 2, 2);

        when(loteRepo.findById(loteId)).thenReturn(Optional.of(lote));

        LoteProgressoResponse resp = batchService.getProgresso(loteId);

        assertThat(resp.loteId()).isEqualTo(loteId);
        assertThat(resp.total()).isEqualTo(10);
        assertThat(resp.emitidas()).isEqualTo(6);
        assertThat(resp.falhas()).isEqualTo(2);
        assertThat(resp.emProcessamento()).isEqualTo(2);
        assertThat(resp.percentualConcluido()).isEqualTo(80.0);
        assertThat(resp.status()).isEqualTo("EM_ANDAMENTO");
    }

    // --- Critério: lote é finalizado lazily quando emitidas + falhas == total ---

    @Test
    void getProgresso_todasProcessadas_finalizaLoteComoConcluido() throws Exception {
        UUID loteId = UUID.randomUUID();
        // emitidas(9) + falhas(1) == total(10) → CONCLUIDO (falhas > 0 → FALHA)
        LoteEmissao lote = loteComId(loteId, "EM_ANDAMENTO", 10, 9, 0, 1);

        when(loteRepo.findById(loteId)).thenReturn(Optional.of(lote));
        when(loteRepo.save(any())).thenAnswer(inv -> inv.getArgument(0, LoteEmissao.class));

        // 9 emitidas + 0 falhas < 10 ainda? Não — let me fix: 9+0=9 < 10. Use 10+0=10
        // Resetting: emitidas=10, falhas=0, em_proc=0
        lote.setEmitidas(10);
        lote.setFalhas(0);
        lote.setEmProcessamento(0);

        LoteProgressoResponse resp = batchService.getProgresso(loteId);

        assertThat(resp.status()).isEqualTo("CONCLUIDO");
        assertThat(resp.percentualConcluido()).isEqualTo(100.0);
        verify(loteRepo).save(any());
    }

    @Test
    void getProgresso_todasProcessadasComFalhas_finalizaLoteComoFalha() throws Exception {
        UUID loteId = UUID.randomUUID();
        LoteEmissao lote = loteComId(loteId, "EM_ANDAMENTO", 5, 3, 2, 0);

        when(loteRepo.findById(loteId)).thenReturn(Optional.of(lote));
        when(loteRepo.save(any())).thenAnswer(inv -> inv.getArgument(0, LoteEmissao.class));

        LoteProgressoResponse resp = batchService.getProgresso(loteId);

        assertThat(resp.status()).isEqualTo("FALHA");
        verify(loteRepo).save(any());
    }

    // --- Critério: lote não encontrado → 404 ---

    @Test
    void getProgresso_loteNaoEncontrado_lanca404() {
        when(loteRepo.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchService.getProgresso(UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- listarLotes ---

    @Test
    void listarLotes_retornaOrdenado() throws Exception {
        LoteEmissao l1 = loteComId(UUID.randomUUID(), "CONCLUIDO", 5, 5, 0, 0);
        LoteEmissao l2 = loteComId(UUID.randomUUID(), "EM_ANDAMENTO", 3, 1, 0, 2);
        when(loteRepo.findAllByOrderByIniciadoEmDesc()).thenReturn(List.of(l1, l2));

        List<LoteProgressoResponse> lista = batchService.listarLotes();

        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).status()).isEqualTo("CONCLUIDO");
        assertThat(lista.get(1).status()).isEqualTo("EM_ANDAMENTO");
    }

    // --- helpers ---

    private NotaFiscal notaComStatus(UUID id, StatusNota status) throws Exception {
        NotaFiscal n = new NotaFiscal();
        Field f = NotaFiscal.class.getDeclaredField("id");
        f.setAccessible(true); f.set(n, id);
        n.setStatus(status);
        n.setCompetencia(COMPETENCIA);
        return n;
    }

    private LoteEmissao loteComId(UUID id, String status, int total, int emitidas, int falhas, int emProc) throws Exception {
        LoteEmissao l = new LoteEmissao();
        setId(l, id);
        l.setCompetencia(COMPETENCIA);
        l.setStatus(status);
        l.setTotal(total);
        l.setEmitidas(emitidas);
        l.setFalhas(falhas);
        l.setEmProcessamento(emProc);
        return l;
    }

    private void setId(LoteEmissao lote, UUID id) throws Exception {
        Field f = LoteEmissao.class.getDeclaredField("id");
        f.setAccessible(true); f.set(lote, id);
    }
}
