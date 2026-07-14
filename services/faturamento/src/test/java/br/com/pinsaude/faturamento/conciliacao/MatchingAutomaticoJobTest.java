package br.com.pinsaude.faturamento.conciliacao;

import br.com.pinsaude.faturamento.conciliacao.matching.MatchingAutomaticoJob;
import br.com.pinsaude.faturamento.conciliacao.matching.MatchingService;
import br.com.pinsaude.faturamento.conciliacao.messaging.MatchingMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingAutomaticoJobTest {

    @Mock MatchingService matchingService;

    @InjectMocks MatchingAutomaticoJob job;

    @Test
    void consumir_mensagemValida_delegaAoMatchingService() {
        UUID extratoId = UUID.randomUUID();
        MatchingMessage msg = new MatchingMessage(extratoId, "12345678000190");

        job.consumir(msg);

        verify(matchingService).processarExtrato(extratoId, "12345678000190");
    }

    @Test
    void consumir_serviceLancaException_propagaParaDLQ() {
        MatchingMessage msg = new MatchingMessage(UUID.randomUUID(), "12345678000190");
        doThrow(new RuntimeException("DB error")).when(matchingService).processarExtrato(any(), any());

        assertThatThrownBy(() -> job.consumir(msg))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error");
    }
}
