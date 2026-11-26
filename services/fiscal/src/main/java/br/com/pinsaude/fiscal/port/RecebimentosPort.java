package br.com.pinsaude.fiscal.port;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * APU-01 — recebimentos das produções (crédito do extrato conciliado), que ficam no Faturamento.
 * É a fonte da receita por caixa da apuração.
 */
public interface RecebimentosPort {

    /** Uma produção recebida: data do depósito conciliado e valor creditado (centavos). */
    record Recebimento(UUID producaoId, LocalDate recebidoEm, long valorRecebido) {}

    /**
     * Produções recebidas com o depósito entre {@code de} e {@code ate} (inclusive).
     * @throws RecebimentosIndisponiveisException se o Faturamento não responder
     */
    List<Recebimento> recebidosEntre(LocalDate de, LocalDate ate);

    class RecebimentosIndisponiveisException extends RuntimeException {
        public RecebimentosIndisponiveisException(String msg, Throwable causa) {
            super(msg, causa);
        }
    }
}
