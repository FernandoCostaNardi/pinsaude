package br.com.pinsaude.faturamento.conciliacao.parser;

import br.com.pinsaude.faturamento.domain.TipoLancamentoExtrato;

import java.time.LocalDate;

public record LancamentoParseado(
    LocalDate data,
    String descricao,
    long valorCentavos,
    TipoLancamentoExtrato tipo,
    String identificadorExterno
) {}
