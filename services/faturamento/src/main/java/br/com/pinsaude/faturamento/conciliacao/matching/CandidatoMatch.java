package br.com.pinsaude.faturamento.conciliacao.matching;

import br.com.pinsaude.faturamento.domain.Producao;

record CandidatoMatch(Producao producao, int score) {}
