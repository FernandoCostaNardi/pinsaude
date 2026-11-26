package br.com.pinsaude.fiscal.apuracao;

import br.com.pinsaude.fiscal.domain.TributoApuracao;

import java.time.YearMonth;
import java.util.Map;

/**
 * APU-00 — números de um mês que entram na apuração (centavos).
 * A receita vem separada por equiparação hospitalar (define a presunção de IRPJ/CSLL) e por regime:
 * competência (notas emitidas no mês) e caixa (notas recebidas no mês).
 *
 * @param retencoes retenções na fonte sofridas no mês, por tributo (o IR retido abate o IRPJ)
 * @param cbsNotas  CBS calculada nas notas do mês (base SOMA_NOTAS)
 * @param ibsNotas  IBS calculado nas notas do mês (base SOMA_NOTAS)
 */
public record MesApuracao(
    YearMonth competencia,
    long receitaCompetenciaEquiparada,
    long receitaCompetenciaPadrao,
    long receitaCaixaEquiparada,
    long receitaCaixaPadrao,
    Map<TributoApuracao, Long> retencoes,
    long cbsNotas,
    long ibsNotas
) {
    public MesApuracao {
        retencoes = retencoes == null ? Map.of() : Map.copyOf(retencoes);
    }

    public static MesApuracao vazio(YearMonth competencia) {
        return new MesApuracao(competencia, 0, 0, 0, 0, Map.of(), 0, 0);
    }

    public long retencao(TributoApuracao t) {
        return retencoes.getOrDefault(t, 0L);
    }
}
