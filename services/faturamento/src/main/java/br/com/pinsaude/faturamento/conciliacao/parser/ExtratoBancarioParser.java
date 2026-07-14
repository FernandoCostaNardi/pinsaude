package br.com.pinsaude.faturamento.conciliacao.parser;

import br.com.pinsaude.faturamento.domain.BancoEnum;

import java.text.ParseException;
import java.util.List;

public interface ExtratoBancarioParser {

    List<LancamentoParseado> parse(byte[] content) throws ParseException;

    boolean suporta(BancoEnum banco, String nomeArquivo);
}
