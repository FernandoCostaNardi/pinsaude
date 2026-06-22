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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NfseServiceTest {

    @Mock NotaFiscalRepository notaRepo;
    @Mock EmissaoNfsePort emissaoPort;
    @Mock NfseEmissaoProducer producer;

    @InjectMocks NfseService nfseService;

    private static final String CNPJ_TENANT = "12345678000195";

    // --- Critério 6: idempotência — mesma producaoId não emite duas vezes ---

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
        verify(notaRepo, never()).save(any());
        verify(producer, never()).enviar(any());
    }

    // --- Outbox: nota criada PENDENTE + mensagem enfileirada na mesma transação ---

    @Test
    void emitir_producaoIdNova_criaNovaNota_publicaMensagem() throws Exception {
        UUID producaoId = UUID.randomUUID();
        UUID notaId = UUID.randomUUID();

        when(notaRepo.existsByProducaoId(producaoId)).thenReturn(false);
        when(notaRepo.save(any())).thenAnswer(inv -> {
            NotaFiscal n = inv.getArgument(0, NotaFiscal.class);
            setId(n, notaId);
            return n;
        });

        EmitirNfseResponse response = nfseService.emitir(requestFor(producaoId), CNPJ_TENANT);

        assertThat(response.notaId()).isEqualTo(notaId);
        assertThat(response.status()).isEqualTo(StatusNota.PENDENTE);

        ArgumentCaptor<NfseEmissaoMessage> msgCaptor = ArgumentCaptor.forClass(NfseEmissaoMessage.class);
        verify(producer).enviar(msgCaptor.capture());
        assertThat(msgCaptor.getValue().notaId()).isEqualTo(notaId);
    }

    // --- processarEmissao: sucesso — nota marcada EMITIDA com XML e PDF ---

    @Test
    void processarEmissao_sucesso_marcaNotaEmitida() throws Exception {
        UUID notaId = UUID.randomUUID();
        NotaFiscal nota = notaFiscalPendente(notaId);

        // Captura o status no momento exato de cada save (mesmo objeto, status muda entre saves)
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

    // --- Critério 7: falha definitiva → nota marcada ERRO ---

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

    private EmitirNfseRequest requestFor(UUID producaoId) {
        return new EmitirNfseRequest(
            producaoId, UUID.randomUUID(), UUID.randomUUID(),
            "2026-06", 1000000L, 150000L,
            50000L, 15000L, 10000L, 6500L, 30000L
        );
    }

    private NotaFiscal notaFiscalPendente(UUID id) throws Exception {
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
