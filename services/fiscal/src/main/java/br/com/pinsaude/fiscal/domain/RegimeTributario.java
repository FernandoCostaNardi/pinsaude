package br.com.pinsaude.fiscal.domain;

/** Regime usado no cálculo de uma nota (CBS-11). */
public enum RegimeTributario {
    /** PIS/COFINS + ISS + IR/CSLL (antes da virada da chave IBS/CBS). */
    LEGADO,
    /** CBS + IBS + ISS (com fator do ano) + IR/CSLL. */
    IBS_CBS
}
