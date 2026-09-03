package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorModalidade;

// Par (modalidade, tipo resolvido) de um vínculo Setor↔Modalidade — desde que uma Modalidade
// possa ter mais de um Tipo de Escala (ver TomadorModalidade.tipos), o "tipo" exibido/usado para
// um vínculo específico vem do próprio vínculo (SetorOperacionalModalidade.tipo), não mais
// direto da modalidade. Tipo interno de composição entre TomadorService e os *Response — nunca
// serializado como está (vira ModalidadeResumo na resposta HTTP).
public record ModalidadeVinculoResolvido(TomadorModalidade modalidade, String tipo) {}
