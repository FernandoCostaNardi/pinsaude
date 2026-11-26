package br.com.pinsaude.fiscal.domain;

/**
 * APU-00 — base de cálculo de um tributo na apuração.
 * RECEITA_BRUTA: receita do período. BASE_PRESUMIDA: receita × presunção (IRPJ/CSLL).
 * SOMA_NOTAS: soma do valor já calculado em cada nota (CBS/IBS, com a redução do serviço).
 */
public enum BaseCalculoApuracao { RECEITA_BRUTA, BASE_PRESUMIDA, SOMA_NOTAS }
