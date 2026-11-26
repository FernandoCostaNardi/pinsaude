package br.com.pinsaude.fiscal.domain;

/** APU-00 — tributos apurados mensalmente pela Pin. */
public enum TributoApuracao {
    IRPJ, CSLL, PIS, COFINS, ISS, CBS, IBS;

    public boolean isBasePresumidaPermitida() { return this == IRPJ || this == CSLL; }
    public boolean isReforma()                { return this == CBS || this == IBS; }
}
