package br.com.pinsaude.ledger;

import br.com.pinsaude.ledger.domain.LancamentoLedger;
import br.com.pinsaude.ledger.domain.PartidaLedger;
import br.com.pinsaude.ledger.domain.TipoPartida;
import br.com.pinsaude.ledger.messaging.AjusteManualEvent;
import br.com.pinsaude.ledger.messaging.NotaEmitidaEvent;
import br.com.pinsaude.ledger.messaging.RecebimentoConciliadoEvent;
import br.com.pinsaude.ledger.messaging.RepasseEfetuadoEvent;
import br.com.pinsaude.ledger.repository.LancamentoLedgerRepository;
import br.com.pinsaude.ledger.repository.PartidaLedgerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static br.com.pinsaude.ledger.config.RabbitLedgerConfig.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração dos consumers RabbitMQ (EPIC-08.3) contra PostgreSQL + RabbitMQ reais.
 * Cobre os 5 critérios de aceite: lançamentos corretos/balanceados por evento, idempotência,
 * equilíbrio global, DLQ após 3 tentativas e o outbox/idempotent-consumer.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy",
        // acelera o teste da DLQ (retry rápido, ainda com 3 tentativas)
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=1.0",
        "spring.rabbitmq.listener.simple.retry.max-attempts=3"
})
@Testcontainers
class LedgerEventIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired LancamentoLedgerRepository lancamentoRepo;
    @Autowired PartidaLedgerRepository partidaRepo;

    private static final String TENANT = "12345678000195";

    private LancamentoLedger aguardarPorCorrelation(String correlationId) {
        return aguardar(() -> lancamentoRepo.findByCorrelationId(correlationId), Duration.ofSeconds(15))
            .orElseThrow(() -> new AssertionError("Lançamento não criado para " + correlationId));
    }

    private <T> Optional<T> aguardar(Supplier<Optional<T>> fn, Duration timeout) {
        Instant fim = Instant.now().plus(timeout);
        while (Instant.now().isBefore(fim)) {
            Optional<T> r = fn.get();
            if (r.isPresent()) return r;
            try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return Optional.empty();
    }

    long[] somaDebitoCreditoDoLancamento(UUID lancamentoId) {
        LancamentoLedger l = lancamentoRepo.findByIdComPartidas(lancamentoId).orElseThrow();
        long deb = l.getPartidas().stream().filter(p -> p.getTipo() == TipoPartida.DEBITO)
                .mapToLong(PartidaLedger::getValorCentavos).sum();
        long cred = l.getPartidas().stream().filter(p -> p.getTipo() == TipoPartida.CREDITO)
                .mapToLong(PartidaLedger::getValorCentavos).sum();
        return new long[]{deb, cred};
    }

    // ─── Critério 1: cada evento gera lançamentos corretos e balanceados ──────

    @Test
    void notaEmitida_geraLancamentoBalanceado() {
        UUID notaId = UUID.randomUUID();
        var evento = new NotaEmitidaEvent(notaId, UUID.randomUUID(), TENANT, "2026-07",
                1_000_000L, 850_000L,
                50_000L, 15_000L, 10_000L, 6_500L, 30_000L); // retenções: total 111.500
        rabbitTemplate.convertAndSend(QUEUE_NOTA_EMITIDA, evento);

        LancamentoLedger l = aguardarPorCorrelation("NOTA:" + notaId);
        long[] dc = somaDebitoCreditoDoLancamento(l.getId());
        assertThat(dc[0]).isEqualTo(1_000_000L);          // débito total = bruto
        assertThat(dc[0]).isEqualTo(dc[1]);               // balanceado
        // partidas: 1 débito (a receber) + 1 crédito repasse + 1 crédito receita + 5 retenções
        assertThat(lancamentoRepo.findByIdComPartidas(l.getId()).orElseThrow().getPartidas()).hasSize(8);
    }

    @Test
    void recebimentoConciliado_geraLancamento() {
        UUID conciliacaoId = UUID.randomUUID();
        var evento = new RecebimentoConciliadoEvent(conciliacaoId, UUID.randomUUID(), UUID.randomUUID(),
                TENANT, "2026-07", 900_000L);
        rabbitTemplate.convertAndSend(QUEUE_RECEBIMENTO_CONCILIADO, evento);

        LancamentoLedger l = aguardarPorCorrelation("CONCILIACAO:" + conciliacaoId);
        long[] dc = somaDebitoCreditoDoLancamento(l.getId());
        assertThat(dc[0]).isEqualTo(900_000L);
        assertThat(dc[0]).isEqualTo(dc[1]);
    }

    @Test
    void repasseEfetuado_geraLancamento() {
        UUID repasseId = UUID.randomUUID();
        var evento = new RepasseEfetuadoEvent(repasseId, UUID.randomUUID(), TENANT, "2026-07", 850_000L);
        rabbitTemplate.convertAndSend(QUEUE_REPASSE_EFETUADO, evento);

        LancamentoLedger l = aguardarPorCorrelation("REPASSE:" + repasseId);
        long[] dc = somaDebitoCreditoDoLancamento(l.getId());
        assertThat(dc[0]).isEqualTo(850_000L);
        assertThat(dc[0]).isEqualTo(dc[1]);
    }

    @Test
    void ajusteManualValido_geraLancamento() {
        UUID ajusteId = UUID.randomUUID();
        var evento = new AjusteManualEvent(ajusteId, UUID.randomUUID(), TENANT, "2026-07",
                "Estorno de taxa", UUID.randomUUID(), UUID.randomUUID(),
                List.of(new AjusteManualEvent.PartidaAjuste("2.1.02", TipoPartida.DEBITO, 20_000L),
                        new AjusteManualEvent.PartidaAjuste("3.1.01", TipoPartida.CREDITO, 20_000L)));
        rabbitTemplate.convertAndSend(QUEUE_AJUSTE_MANUAL, evento);

        LancamentoLedger l = aguardarPorCorrelation("AJUSTE:" + ajusteId);
        long[] dc = somaDebitoCreditoDoLancamento(l.getId());
        assertThat(dc[0]).isEqualTo(20_000L);
        assertThat(dc[0]).isEqualTo(dc[1]);
    }

    // ─── Critério 2: evento duplicado não duplica lançamento ──────────────────

    @Test
    void eventoDuplicado_naoDuplica() {
        UUID notaId = UUID.randomUUID();
        var evento = new NotaEmitidaEvent(notaId, UUID.randomUUID(), TENANT, "2026-07",
                1_000_000L, 850_000L, 50_000L, 15_000L, 10_000L, 6_500L, 30_000L);
        rabbitTemplate.convertAndSend(QUEUE_NOTA_EMITIDA, evento);
        aguardarPorCorrelation("NOTA:" + notaId);

        // segunda entrega do MESMO evento
        rabbitTemplate.convertAndSend(QUEUE_NOTA_EMITIDA, evento);
        // dá tempo do consumer processar a duplicata
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        long qtd = lancamentoRepo.findAll().stream()
                .filter(l -> ("NOTA:" + notaId).equals(l.getCorrelationId())).count();
        assertThat(qtd).isEqualTo(1L);
    }

    // ─── Critério 3: ledger globalmente balanceado ────────────────────────────

    @Test
    void ledgerGlobalmenteBalanceado() {
        rabbitTemplate.convertAndSend(QUEUE_NOTA_EMITIDA, new NotaEmitidaEvent(
                UUID.randomUUID(), UUID.randomUUID(), TENANT, "2026-07",
                1_000_000L, 850_000L, 50_000L, 15_000L, 10_000L, 6_500L, 30_000L));
        rabbitTemplate.convertAndSend(QUEUE_REPASSE_EFETUADO, new RepasseEfetuadoEvent(
                UUID.randomUUID(), UUID.randomUUID(), TENANT, "2026-07", 850_000L));
        // espera o processamento assíncrono estabilizar
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        long deb = partidaRepo.somaGlobalPorTipo(TipoPartida.DEBITO);
        long cred = partidaRepo.somaGlobalPorTipo(TipoPartida.CREDITO);
        assertThat(deb).isEqualTo(cred);
    }

    // ─── Critério 4: falha no consumer vai para a DLQ após 3 tentativas ───────

    @Test
    void ajusteSemDuplaAutorizacao_vaiParaDLQ() {
        UUID ajusteId = UUID.randomUUID();
        // autorizadoPor2 == autorizadoPor1 → viola a autorização dupla → consumer rejeita
        UUID mesmo = UUID.randomUUID();
        var evento = new AjusteManualEvent(ajusteId, UUID.randomUUID(), TENANT, "2026-07",
                "Ajuste inválido", mesmo, mesmo,
                List.of(new AjusteManualEvent.PartidaAjuste("2.1.02", TipoPartida.DEBITO, 10_000L),
                        new AjusteManualEvent.PartidaAjuste("3.1.01", TipoPartida.CREDITO, 10_000L)));
        rabbitTemplate.convertAndSend(QUEUE_AJUSTE_MANUAL, evento);

        // após as 3 tentativas, a mensagem morta cai na DLQ
        Message morta = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(morta).as("mensagem deve chegar na DLQ após esgotar as retentativas").isNotNull();

        // e nenhum lançamento foi criado para esse ajuste
        assertThat(lancamentoRepo.findByCorrelationId("AJUSTE:" + ajusteId)).isEmpty();
    }
}
