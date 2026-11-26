package br.com.pinsaude.fiscal.apuracao;

import java.util.List;

/**
 * APU-00 — apuração de uma competência (centavos).
 *
 * @param regraGlobal  a regra usada é o padrão global (a empresa não tem regra própria)
 * @param aHomologar   a regra ainda não foi homologada pela contabilidade: valores são estimativa
 */
public record ResultadoApuracao(
    String competencia,
    String cnpj,
    String regime,
    String regraId,
    String regraCompetenciaInicio,
    boolean regraGlobal,
    boolean homologado,
    boolean aHomologar,
    List<TributoApurado> tributos,
    long totalDevido,
    long totalRetencoes,
    long totalARecolher,
    List<String> avisos
) {}
