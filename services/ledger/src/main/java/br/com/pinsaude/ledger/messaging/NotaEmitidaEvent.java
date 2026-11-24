package br.com.pinsaude.ledger.messaging;

import java.util.UUID;

/**
 * Evento publicado quando uma NFS-e é emitida (origem: serviço fiscal, fila ledger.nota.emitida).
 * Gera o lançamento de reconhecimento: débito em "Honorários a Receber", crédito no repasse ao
 * médico, crédito de cada retenção de imposto, crédito de CBS e IBS a recolher (CBS-40) e o que
 * sobra como receita da Pin. Valores em centavos.
 *
 * <p>Campos da CBS-40 ({@code regime}, {@code valorCbsCentavos}, {@code valorIbsCentavos},
 * {@code cbsIbsPorFora}) são opcionais: mensagem antiga, sem eles, chega com regime nulo (LEGADO),
 * CBS/IBS 0 e "dentro do preço", e o lançamento sai igual ao de antes.
 */
public record NotaEmitidaEvent(
    UUID notaId,
    UUID medicoId,
    String cnpjTenant,
    String competencia,
    long valorBrutoCentavos,
    long valorLiquidoMedicoCentavos,
    long valorIssCentavos,
    long valorIrCentavos,
    long valorCsllCentavos,
    long valorPisCentavos,
    long valorCofinsCentavos,
    /** LEGADO | IBS_CBS (regime gravado na nota). Nulo = LEGADO. */
    String regime,
    long valorCbsCentavos,
    long valorIbsCentavos,
    /** true = CBS/IBS somados ao preço (tomador paga bruto + CBS + IBS). Nulo/false = dentro do preço. */
    Boolean cbsIbsPorFora
) {

    /** Formato anterior à CBS-40 (regime LEGADO, sem CBS/IBS). */
    public NotaEmitidaEvent(UUID notaId, UUID medicoId, String cnpjTenant, String competencia,
                            long valorBrutoCentavos, long valorLiquidoMedicoCentavos,
                            long valorIssCentavos, long valorIrCentavos, long valorCsllCentavos,
                            long valorPisCentavos, long valorCofinsCentavos) {
        this(notaId, medicoId, cnpjTenant, competencia, valorBrutoCentavos, valorLiquidoMedicoCentavos,
            valorIssCentavos, valorIrCentavos, valorCsllCentavos, valorPisCentavos, valorCofinsCentavos,
            null, 0L, 0L, null);
    }

    public String regimeOuLegado() {
        return regime == null || regime.isBlank() ? "LEGADO" : regime;
    }

    public boolean porFora() {
        return Boolean.TRUE.equals(cbsIbsPorFora);
    }
}
