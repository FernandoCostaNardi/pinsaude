package br.com.pinsaude.fiscal.adapter;

import br.com.pinsaude.fiscal.port.DadosNota;

record NfseRpsRequest(
    String idempotencyKey,
    String prestadorCnpj,
    String tomadorId,
    String competencia,
    long valorServicosCentavos,
    long issRetidoCentavos,
    long irRetidoCentavos,
    String descricao
) {
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
            descricao
        );
    }
}
