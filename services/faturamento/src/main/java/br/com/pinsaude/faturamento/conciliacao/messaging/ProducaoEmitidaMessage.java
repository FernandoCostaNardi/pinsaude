package br.com.pinsaude.faturamento.conciliacao.messaging;

import java.util.UUID;

/**
 * Publicado pelo Fiscal quando a NFS-e da produção fica EMITIDA.
 * CBS-31: {@code valorLiquidoEsperado} (centavos, bruto − retenções do tomador) e {@code emitidaEm}
 * (AAAA-MM-DD) chegam nulos em mensagens de versões anteriores do Fiscal.
 */
public record ProducaoEmitidaMessage(UUID producaoId, String cnpjTenant,
                                     Long valorLiquidoEsperado, String emitidaEm) {

    public ProducaoEmitidaMessage(UUID producaoId, String cnpjTenant) {
        this(producaoId, cnpjTenant, null, null);
    }
}
