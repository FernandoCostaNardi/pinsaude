package br.com.pinsaude.ledger.messaging;

import br.com.pinsaude.ledger.domain.TipoPartida;

import java.util.List;
import java.util.UUID;

/**
 * Evento de ajuste manual do ledger. Exige autorização dupla: {@code autorizadoPor1}
 * e {@code autorizadoPor2} devem estar presentes e ser distintos, caso contrário o
 * consumer rejeita a mensagem (que vai para a DLQ após as retentativas).
 * As partidas são informadas explicitamente e devem estar balanceadas. Valores em centavos.
 */
public record AjusteManualEvent(
    UUID ajusteId,
    UUID medicoId,
    String cnpjTenant,
    String competencia,
    String descricao,
    UUID autorizadoPor1,
    UUID autorizadoPor2,
    List<PartidaAjuste> partidas
) {
    public record PartidaAjuste(String contaCodigo, TipoPartida tipo, long valorCentavos) {}
}
