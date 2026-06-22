package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.EmitirNfseRequest;
import br.com.pinsaude.fiscal.dto.EmitirNfseResponse;
import br.com.pinsaude.fiscal.messaging.NfseEmissaoMessage;
import br.com.pinsaude.fiscal.messaging.NfseEmissaoProducer;
import br.com.pinsaude.fiscal.port.EmissaoNfsePort;
import br.com.pinsaude.fiscal.port.ResultadoEmissao;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NfseServiceTest {

    @Mock NotaFiscalRepository notaRepo;
    @Mock EmissaoNfsePort emissaoPort;
    @Mock NfseEmissaoProducer producer;

    @InjectMocks NfseService nfseService;

    private static final String CNPJ_TENANT = "12345678000195";

    // --- Critério: idempotência — mesma producaoId não emite duas vezes ---

    @Test
    void emitir_producaoIdJaExiste_retornaNotaExistente() {
        UUID producaoId = UUID.randomUUID();
        UUID notaExistenteId = UUID.randomUUID();

        NotaFiscal existente = notaFiscalComId(notaExistenteId, StatusNota.EMITIDA, producaoId);
        when(notaRepo.existsByProducaoId(producaoId)).thenReturn(true);
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.of(existente));

        EmitirNfseResponse response = nfseService.emitir(requestFor(producaoId), CNPJ_TENANT);

        assertThat(response.notaId()).isEqualTo(notaExistenteId);
        assertThat(response.status()).isEqualTo(StatusNota.EMITIDA);
        assertThat(response.primeiraNotaMedico()).isFalse();
        verify(notaRepo, never()).save(any());
        verify(producer, never()).enviar(any());
    }

    // --- Outbox: médico com notas anteriores emitidas → PENDENTE + enfileirado ---

    @Test
    void emitir_medicoComNotasAnteriores_criaNovaNota_publicaMensagem() throws Exception {
        UUID producaoId = UUID.randomUUID();
        UUID notaId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();

        when(notaRepo.existsByProducaoId(producaoId)).thenReturn(false);
        when(notaRepo.existsByMedicoIdAndStatus(medicoId, StatusNota.EMITIDA)).thenReturn(true);
        when(notaRepo.save(any())).thenAnswer(inv -> {
            NotaFiscal n = inv.getArgument(0, NotaFiscal.class);
            setId(n, notaId);
            return n;
        });

        EmitirNfseResponse response = nfseService.emitir(requestFor(producaoId, medicoId), CNPJ_TENANT);

        assertThat(response.notaId()).isEqualTo(notaId);
        assertThat(response.status()).isEqualTo(StatusNota.PENDENTE);
        assertThat(response.primeiraNotaMedico()).isFalse();

        ArgumentCaptor<NfseEmissaoMessage> msgCaptor = ArgumentCaptor.forClass(NfseEmissaoMessage.class);
        verify(producer).enviar(msgCaptor.capture());
        assertThat(msgCaptor.getValue().notaId()).isEqualTo(notaId);
    }

    // --- Critério 4: 1ª nota de médico → AGUARDANDO_VALIDACAO, não enfileirada ---

    @Test
    void emitir_primeiraNota_ficaAguardandoValidacao_semEnfila() throws Exception {
        UUID producaoId = UUID.randomUUID();
        UUID notaId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();

        when(notaRepo.existsByProducaoId(producaoId)).thenReturn(false);
        when(notaRepo.existsByMedicoIdAndStatus(medicoId, StatusNota.EMITIDA)).thenReturn(false);
        when(notaRepo.save(any())).thenAnswer(inv -> {
            NotaFiscal n = inv.getArgument(0, NotaFiscal.class);
            setId(n, notaId);
            return n;
        });

        EmitirNfseResponse response = nfseService.emitir(requestFor(producaoId, medicoId), CNPJ_TENANT);

        assertThat(response.status()).isEqualTo(StatusNota.AGUARDANDO_VALIDACAO);
        assertThat(response.primeiraNotaMedico()).isTrue();
        verify(producer, never()).enviar(any());
    }

    // --- Critério: aprovação de 1ª nota muda para PENDENTE e enfileira ---

    @Test
    void aprovar_notaAguardandoValidacao_mudaParaPendenteEEnfileira() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalComId(notaId, StatusNota.AGUARDANDO_VALIDACAO, UUID.randomUUID());

        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));
        when(notaRepo.save(any())).thenReturn(nota);

        nfseService.aprovar(notaId);

        assertThat(nota.getStatus()).isEqualTo(StatusNota.PENDENTE);
        ArgumentCaptor<NfseEmissaoMessage> captor = ArgumentCaptor.forClass(NfseEmissaoMessage.class);
        verify(producer).enviar(captor.capture());
        assertThat(captor.getValue().notaId()).isEqualTo(notaId);
    }

    @Test
    void aprovar_notaEmStatusErrado_lanca409() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalComId(notaId, StatusNota.EMITIDA, UUID.randomUUID());

        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));

        assertThatThrownBy(() -> nfseService.aprovar(notaId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    // --- processarEmissao: sucesso — nota marcada EMITIDA com XML e PDF ---

    @Test
    void processarEmissao_sucesso_marcaNotaEmitida() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalPendente(notaId);

        List<StatusNota> statusNaSalva = new ArrayList<>();
        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));
        when(notaRepo.save(any())).thenAnswer(inv -> {
            NotaFiscal n = inv.getArgument(0, NotaFiscal.class);
            statusNaSalva.add(n.getStatus());
            return n;
        });
        when(emissaoPort.emitir(any())).thenReturn(
            ResultadoEmissao.sucesso("NF-001", "PROTO-001", "<xml/>", "PDF".getBytes()));

        nfseService.processarEmissao(notaId);

        assertThat(statusNaSalva).containsExactly(StatusNota.PROCESSANDO, StatusNota.EMITIDA);
        assertThat(nota.getNumeroNota()).isEqualTo("NF-001");
        assertThat(nota.getProtocoloEmissao()).isEqualTo("PROTO-001");
        assertThat(nota.getXmlNota()).isEqualTo("<xml/>");
        assertThat(nota.getPdfNota()).isEqualTo("PDF".getBytes());
        assertThat(nota.getEmitidaAt()).isNotNull();
    }

    // --- processarEmissao: circuit breaker aberto → AGUARDANDO_EMISSAO_MANUAL ---

    @Test
    void processarEmissao_resultadoAguardandoManual_marcaStatusCorreto() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalPendente(notaId);
        List<StatusNota> statusNaSalva = new ArrayList<>();

        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));
        when(notaRepo.save(any())).thenAnswer(inv -> {
            statusNaSalva.add(inv.getArgument(0, NotaFiscal.class).getStatus());
            return inv.getArgument(0);
        });
        when(emissaoPort.emitir(any())).thenReturn(
            ResultadoEmissao.aguardandoManual("Circuit breaker aberto"));

        nfseService.processarEmissao(notaId);

        assertThat(statusNaSalva).containsExactly(StatusNota.PROCESSANDO, StatusNota.AGUARDANDO_EMISSAO_MANUAL);
    }

    // --- Critério 5: falha definitiva → nota marcada ERRO com motivo ---

    @Test
    void processarEmissao_erroDefinitivo_marcaStatusErro() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalPendente(notaId);
        List<StatusNota> statusNaSalva = new ArrayList<>();

        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));
        when(notaRepo.save(any())).thenAnswer(inv -> {
            statusNaSalva.add(inv.getArgument(0, NotaFiscal.class).getStatus());
            return inv.getArgument(0);
        });
        when(emissaoPort.emitir(any())).thenReturn(ResultadoEmissao.erro("Falha definitiva após 3 tentativas"));

        nfseService.processarEmissao(notaId);

        assertThat(statusNaSalva).containsExactly(StatusNota.PROCESSANDO, StatusNota.ERRO);
        assertThat(nota.getObservacoes()).isEqualTo("Falha definitiva após 3 tentativas");
    }

    // --- Idempotência no consumer: nota não-PENDENTE é ignorada silenciosamente ---

    @Test
    void processarEmissao_notaJaProcessando_ignoraSilenciosamente() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalComId(notaId, StatusNota.PROCESSANDO, UUID.randomUUID());

        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));

        nfseService.processarEmissao(notaId);

        verify(emissaoPort, never()).emitir(any());
        verify(notaRepo, never()).save(any());
    }

    // --- helpers ---

    // --- cancelar: EMITIDA → CANCELADA com motivo ---

    @Test
    void cancelar_notaEmitida_mudaParaCancelada() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalComId(notaId, StatusNota.EMITIDA, UUID.randomUUID());
        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));
        when(notaRepo.save(any())).thenReturn(nota);

        nfseService.cancelar(notaId, "Duplicidade detectada");

        assertThat(nota.getStatus()).isEqualTo(StatusNota.CANCELADA);
        assertThat(nota.getObservacoes()).contains("Duplicidade detectada");
        verify(notaRepo).save(nota);
    }

    @Test
    void cancelar_notaNaoEmitida_lanca409() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalComId(notaId, StatusNota.PENDENTE, UUID.randomUUID());
        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));

        assertThatThrownBy(() -> nfseService.cancelar(notaId, "motivo"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    // --- rejeitar: AGUARDANDO_VALIDACAO → CANCELADA com motivo ---

    @Test
    void rejeitar_notaAguardandoValidacao_mudaParaCancelada() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalComId(notaId, StatusNota.AGUARDANDO_VALIDACAO, UUID.randomUUID());
        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));
        when(notaRepo.save(any())).thenReturn(nota);

        nfseService.rejeitar(notaId, "Médico sem vínculo ativo");

        assertThat(nota.getStatus()).isEqualTo(StatusNota.CANCELADA);
        assertThat(nota.getObservacoes()).contains("Médico sem vínculo ativo");
        verify(notaRepo).save(nota);
    }

    @Test
    void rejeitar_notaEmStatusErrado_lanca409() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalComId(notaId, StatusNota.PENDENTE, UUID.randomUUID());
        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));

        assertThatThrownBy(() -> nfseService.rejeitar(notaId, "motivo"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    // --- listarExcecoes: retorna apenas AGUARDANDO_VALIDACAO ---

    @Test
    void listarExcecoes_retornaApenasAguardandoValidacao() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        NotaFiscal n1 = notaFiscalComId(id1, StatusNota.AGUARDANDO_VALIDACAO, UUID.randomUUID());
        NotaFiscal n2 = notaFiscalComId(id2, StatusNota.AGUARDANDO_VALIDACAO, UUID.randomUUID());
        when(notaRepo.findAllByStatus(StatusNota.AGUARDANDO_VALIDACAO)).thenReturn(List.of(n1, n2));

        var result = nfseService.listarExcecoes();

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.status() == StatusNota.AGUARDANDO_VALIDACAO);
    }

    // --- helpers ---

    private EmitirNfseRequest requestFor(UUID producaoId) {
        return requestFor(producaoId, UUID.randomUUID());
    }

    private EmitirNfseRequest requestFor(UUID producaoId, UUID medicoId) {
        return new EmitirNfseRequest(
            producaoId, medicoId, UUID.randomUUID(),
            "2026-06", 1000000L, 150000L,
            50000L, 15000L, 10000L, 6500L, 30000L, "Clínica Exemplo"
        );
    }

    private NotaFiscal notaFiscalPendente(UUID id) {
        return notaFiscalComId(id, StatusNota.PENDENTE, UUID.randomUUID());
    }

    private NotaFiscal notaFiscalComId(UUID id, StatusNota status, UUID producaoId) {
        try {
            NotaFiscal nota = new NotaFiscal();
            setId(nota, id);
            nota.setStatus(status);
            nota.setProducaoId(producaoId);
            nota.setMedicoId(UUID.randomUUID());
            nota.setTomadorId(UUID.randomUUID());
            nota.setCnpjIdTenant(CNPJ_TENANT);
            nota.setCompetencia("2026-06");
            nota.setValorBruto(1000000L);
            nota.setTaxaPin(150000L);
            nota.setValorLiquidoMedico(850000L);
            return nota;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setId(NotaFiscal nota, UUID id) throws Exception {
        Field f = NotaFiscal.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(nota, id);
    }
}
