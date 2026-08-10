package br.com.pinsaude.faturamento.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Acompanhamento (read-only) de uma modalidade META dentro de uma frequência: quanto já foi
 * acumulado na competência e quanto falta para fechar o bloco atual da meta. Não afeta o
 * cálculo do dinheiro (já resolvido por item em FrequenciaService) — é só um resumo para a UI.
 */
public record FrequenciaModalidadeProgressoResponse(
    UUID modalidadeId,
    String modalidadeNome,
    String unidadeCalculo,        // HORA | DIA — unidade que dirige o pagamento desta modalidade
    BigDecimal metaHoras,
    Integer metaDias,
    BigDecimal acumuladoHoras,    // soma de horasTrabalhadas dos itens desta modalidade na frequência
    int acumuladoDias,            // contagem de itens desta modalidade (cada item = 1 dia)
    int blocosCompletos,          // quantos blocos da meta primária (unidadeCalculo) já foram fechados
    BigDecimal restanteBlocoAtual // quanto falta, na unidade primária, para fechar o bloco em andamento
) {
    public static FrequenciaModalidadeProgressoResponse calcular(
            UUID modalidadeId, String modalidadeNome, String unidadeCalculo,
            BigDecimal metaHoras, Integer metaDias,
            BigDecimal acumuladoHoras, int acumuladoDias) {

        BigDecimal acumuladoPrimario = "HORA".equals(unidadeCalculo) ? acumuladoHoras : BigDecimal.valueOf(acumuladoDias);
        BigDecimal meta = "HORA".equals(unidadeCalculo) ? metaHoras : (metaDias != null ? BigDecimal.valueOf(metaDias) : null);

        int blocosCompletos = 0;
        BigDecimal restante = BigDecimal.ZERO;
        if (meta != null && meta.signum() > 0) {
            if (acumuladoPrimario.signum() == 0) {
                restante = meta;
            } else {
                blocosCompletos = acumuladoPrimario.divideToIntegralValue(meta).intValue();
                BigDecimal resto = acumuladoPrimario.subtract(meta.multiply(BigDecimal.valueOf(blocosCompletos)));
                restante = resto.signum() == 0 ? BigDecimal.ZERO : meta.subtract(resto).setScale(2, RoundingMode.HALF_UP);
            }
        }

        return new FrequenciaModalidadeProgressoResponse(
            modalidadeId, modalidadeNome, unidadeCalculo, metaHoras, metaDias,
            acumuladoHoras, acumuladoDias, blocosCompletos, restante
        );
    }
}
