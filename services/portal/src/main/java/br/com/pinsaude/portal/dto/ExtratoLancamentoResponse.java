package br.com.pinsaude.portal.dto;

public record ExtratoLancamentoResponse(
    String tipo,        // "CREDITO" ou "DEBITO"
    String categoria,   // "NFS_E", "ISS", "IR", "CSLL", "PIS", "COFINS", "TAXA_PIN"
    String descricao,
    long valor,         // centavos, sempre positivo
    long saldoApos,     // saldo running após este lançamento
    String competencia, // YYYY-MM
    String referencia,  // número da nota ou UUID truncado
    String dataRef      // ISO datetime
) {}
