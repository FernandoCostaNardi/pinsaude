package br.com.pinsaude.fiscal.domain;

/** APU-00 — período de apuração do tributo. Trimestral fecha em março, junho, setembro e dezembro. */
public enum PeriodicidadeApuracao {
    MENSAL(1), TRIMESTRAL(3);

    private final int meses;

    PeriodicidadeApuracao(int meses) { this.meses = meses; }

    public int meses() { return meses; }
}
