package br.com.pinsaude.ledger.dto;

import java.math.BigDecimal;

/** Conversão de centavos (armazenamento interno) para reais com 2 casas (respostas da API). */
public final class Money {

    private Money() {}

    public static BigDecimal reais(long centavos) {
        return BigDecimal.valueOf(centavos, 2);
    }
}
