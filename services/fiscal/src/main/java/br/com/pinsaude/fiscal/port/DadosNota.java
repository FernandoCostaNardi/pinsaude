package br.com.pinsaude.fiscal.port;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dados da nota para o agregador fiscal. ISS/IR/CSLL/PIS/COFINS são os retidos pelo tomador
 * (desde a CBS-21). {@code ibsCbs} é nulo no regime LEGADO (CBS-41).
 */
public record DadosNota(
    UUID producaoId,
    UUID medicoId,
    UUID tomadorId,
    String cnpjIdTenant,
    String competencia,
    long valorBruto,
    long taxaPin,
    long valorIss,
    long valorIr,
    long valorCsll,
    long valorPis,
    long valorCofins,
    long valorLiquidoMedico,
    String discriminacao,       // Descrição/discriminação (grupo de faturamento), nullable
    IbsCbs ibsCbs               // Grupo IBSCBS da NFS-e; null no regime LEGADO
) {

    /** Nota do regime anterior, sem grupo IBSCBS. */
    public DadosNota(UUID producaoId, UUID medicoId, UUID tomadorId, String cnpjIdTenant,
                     String competencia, long valorBruto, long taxaPin, long valorIss, long valorIr,
                     long valorCsll, long valorPis, long valorCofins, long valorLiquidoMedico,
                     String discriminacao) {
        this(producaoId, medicoId, tomadorId, cnpjIdTenant, competencia, valorBruto, taxaPin,
             valorIss, valorIr, valorCsll, valorPis, valorCofins, valorLiquidoMedico, discriminacao, null);
    }

    /**
     * CBS/IBS da nota (CBS-41), lidos do snapshot gravado na emissão. Valores em centavos,
     * alíquotas em fração já com a redução do serviço.
     */
    public record IbsCbs(
        String cst,
        String cClassTrib,
        long baseCalculo,
        boolean porFora,
        BigDecimal reducao,
        BigDecimal aliqCbs,
        BigDecimal aliqIbsUf,
        BigDecimal aliqIbsMun,
        long valorCbs,
        long valorIbsUf,
        long valorIbsMun,
        long valorIbs,
        long valorTotalNota
    ) {}
}
