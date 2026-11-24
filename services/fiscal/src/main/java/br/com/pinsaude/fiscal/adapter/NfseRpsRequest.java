package br.com.pinsaude.fiscal.adapter;

import br.com.pinsaude.fiscal.port.DadosNota;

import java.math.BigDecimal;

/**
 * Pedido de emissão enviado ao agregador fiscal. Valores em centavos, alíquotas em fração.
 *
 * CBS-41: a partir da virada da reforma a NFS-e precisa do grupo IBSCBS (NTs 004/005/007 da
 * NFS-e nacional); nota sem ele é rejeitada. O grupo vai em {@code ibsCbs} (nulo no LEGADO) e as
 * retenções federais vão separadas (NT 007: vRetCSLL e o tipo de retenção de PIS/COFINS).
 */
record NfseRpsRequest(
    String idempotencyKey,
    String prestadorCnpj,
    String tomadorId,
    String competencia,
    long valorServicosCentavos,
    long issRetidoCentavos,
    long irRetidoCentavos,
    long csllRetidoCentavos,
    long pisRetidoCentavos,
    long cofinsRetidoCentavos,
    TipoRetencaoPisCofins tipoRetencaoPisCofins,
    String descricao,
    GrupoIbsCbs ibsCbs
) {

    /** Retenção de PIS/COFINS/CSLL pelo tomador (NT 007, tpRetPisCofins). */
    enum TipoRetencaoPisCofins {
        /** Nada retido. */
        NAO_RETIDOS,
        /** PIS, COFINS e CSLL retidos (regime anterior, tomador com retenção federal). */
        PIS_COFINS_CSLL_RETIDOS,
        /** Só CSLL retida (regime CBS/IBS: PIS/COFINS deixam de existir). */
        CSLL_RETIDA,
        /** Só PIS/COFINS retidos. */
        PIS_COFINS_RETIDOS
    }

    /**
     * Grupo IBSCBS: CST e cClassTrib do serviço, base de cálculo, alíquotas e valores de CBS,
     * IBS estadual e IBS municipal. CBS/IBS são débito da Pin (não há retenção: sem tomador público).
     */
    record GrupoIbsCbs(
        String cst,
        String cClassTrib,
        long baseCalculoCentavos,
        BigDecimal percentualReducao,
        BigDecimal aliqCbs,
        long valorCbsCentavos,
        BigDecimal aliqIbsUf,
        long valorIbsUfCentavos,
        BigDecimal aliqIbsMun,
        long valorIbsMunCentavos,
        long valorIbsCentavos,
        /** true = CBS/IBS somados ao preço; false = já contidos no valor dos serviços. */
        boolean cbsIbsPorFora,
        /** Valor total da nota: serviços + CBS + IBS quando por fora; senão igual aos serviços. */
        long valorTotalNotaCentavos
    ) {
        static GrupoIbsCbs from(DadosNota.IbsCbs g) {
            if (g == null) return null;
            return new GrupoIbsCbs(
                g.cst(), g.cClassTrib(), g.baseCalculo(), g.reducao(),
                g.aliqCbs(), g.valorCbs(),
                g.aliqIbsUf(), g.valorIbsUf(),
                g.aliqIbsMun(), g.valorIbsMun(),
                g.valorIbs(), g.porFora(), g.valorTotalNota());
        }
    }

    static NfseRpsRequest from(DadosNota d) {
        String descricao = (d.discriminacao() != null && !d.discriminacao().isBlank())
            ? d.discriminacao()
            : "Serviços médicos — competência " + d.competencia();
        return new NfseRpsRequest(
            d.producaoId().toString(),
            d.cnpjIdTenant(),
            d.tomadorId().toString(),
            d.competencia(),
            d.valorBruto(),
            d.valorIss(),
            d.valorIr(),
            d.valorCsll(),
            d.valorPis(),
            d.valorCofins(),
            tipoRetencao(d.valorPis() + d.valorCofins() > 0, d.valorCsll() > 0),
            descricao,
            GrupoIbsCbs.from(d.ibsCbs())
        );
    }

    static TipoRetencaoPisCofins tipoRetencao(boolean pisCofins, boolean csll) {
        if (pisCofins && csll) return TipoRetencaoPisCofins.PIS_COFINS_CSLL_RETIDOS;
        if (csll) return TipoRetencaoPisCofins.CSLL_RETIDA;
        if (pisCofins) return TipoRetencaoPisCofins.PIS_COFINS_RETIDOS;
        return TipoRetencaoPisCofins.NAO_RETIDOS;
    }
}
