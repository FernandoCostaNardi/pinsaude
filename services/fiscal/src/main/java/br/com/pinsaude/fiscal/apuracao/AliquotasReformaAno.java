package br.com.pinsaude.fiscal.apuracao;

import java.math.BigDecimal;

/**
 * APU-00 — alíquotas do ano no cronograma da reforma, usadas quando o tributo da regra
 * não tem alíquota própria (CBS/IBS) e para o fator do ISS. Nulos = não fixados / ano sem cronograma.
 */
public record AliquotasReformaAno(BigDecimal cbs, BigDecimal ibs, BigDecimal fatorIss) {

    public static final AliquotasReformaAno NENHUMA = new AliquotasReformaAno(null, null, null);
}
