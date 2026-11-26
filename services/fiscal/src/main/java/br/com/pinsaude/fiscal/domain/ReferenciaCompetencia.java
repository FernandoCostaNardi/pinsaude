package br.com.pinsaude.fiscal.domain;

/**
 * APU-01 — qual data põe a nota na receita por competência de um mês.
 * A receita por caixa usa sempre a data do depósito conciliado.
 */
public enum ReferenciaCompetencia {
    /** Mês de competência gravado na nota (mês da prestação do serviço). */
    COMPETENCIA_NOTA,
    /** Mês da data de emissão da nota, no fuso de São Paulo. */
    DATA_EMISSAO
}
