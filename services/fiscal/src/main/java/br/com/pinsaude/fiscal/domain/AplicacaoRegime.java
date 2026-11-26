package br.com.pinsaude.fiscal.domain;

/**
 * APU-00 — em qual regime da empresa (chave CBS/IBS) o tributo é apurado.
 * LEGADO = chave desligada (PIS/COFINS); IBS_CBS = chave ligada e homologada (CBS/IBS); AMBOS = sempre.
 */
public enum AplicacaoRegime {
    LEGADO, IBS_CBS, AMBOS;

    public boolean vale(RegimeTributario regime) {
        return this == AMBOS || name().equals(regime.name());
    }
}
