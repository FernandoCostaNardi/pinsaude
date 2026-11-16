package br.com.pinsaude.fiscal.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * CBS-15 — regime tributário da empresa numa competência, para as telas (CBS-20) e o
 * faturamento (CBS-30) decidirem quais tributos mostrar sem repetir a regra do motor.
 *
 * @param regime              LEGADO ou IBS_CBS (o mesmo que o motor fiscal usa na competência)
 * @param tributosVisiveis    tributos que existem no regime, na ordem de exibição
 * @param chaveLigada         o parâmetro vigente tem a chave CBS/IBS ligada (mesmo sem homologação)
 * @param homologado          o parâmetro vigente está homologado pela contabilidade
 * @param vigenciaParametro   competência (YYYY-MM) em que o parâmetro vigente começou; null se não houver
 * @param aliquotas           alíquotas do ano no cronograma da reforma; null no regime LEGADO
 */
public record RegimeCompetenciaResponse(
    String competencia,
    String regime,
    List<String> tributosVisiveis,
    boolean chaveLigada,
    boolean homologado,
    String vigenciaParametro,
    AliquotasAno aliquotas
) {

    /**
     * Alíquotas de referência do ano (frações: 0.1120 = 11,2%), sem a redução do serviço.
     * {@code ibs} = IBS estadual + municipal. Valores nulos = ainda não fixados.
     */
    public record AliquotasAno(
        int ano,
        BigDecimal cbs,
        BigDecimal ibsUf,
        BigDecimal ibsMun,
        BigDecimal ibs,
        BigDecimal fatorIss,
        boolean definitivas
    ) {}
}
