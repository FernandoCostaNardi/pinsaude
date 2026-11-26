package br.com.pinsaude.fiscal.apuracao;

import br.com.pinsaude.fiscal.domain.TributoApuracao;

import java.time.YearMonth;
import java.util.Map;

/**
 * APU-00 — números de um mês que entram na apuração (centavos).
 * A receita vem separada por equiparação hospitalar (define a presunção de IRPJ/CSLL) e por regime:
 * competência (notas do mês) e caixa (notas recebidas no mês).
 *
 * <p>APU-01: retenções e CBS/IBS também vêm pelos dois regimes. O tributo apurado por caixa usa as
 * retenções e o CBS/IBS das notas <b>recebidas</b> no mês; o apurado por competência, os das notas do mês.
 * O construtor de 8 argumentos (simulador da APU-00) usa os mesmos números nos dois regimes.
 *
 * @param retencoes      retenções na fonte das notas do mês (competência), por tributo (o IR retido abate o IRPJ)
 * @param cbsNotas       CBS calculada nas notas do mês (base SOMA_NOTAS, competência)
 * @param ibsNotas       IBS calculado nas notas do mês (base SOMA_NOTAS, competência)
 * @param retencoesCaixa retenções das notas recebidas no mês
 * @param cbsNotasCaixa  CBS das notas recebidas no mês
 * @param ibsNotasCaixa  IBS das notas recebidas no mês
 */
public record MesApuracao(
    YearMonth competencia,
    long receitaCompetenciaEquiparada,
    long receitaCompetenciaPadrao,
    long receitaCaixaEquiparada,
    long receitaCaixaPadrao,
    Map<TributoApuracao, Long> retencoes,
    long cbsNotas,
    long ibsNotas,
    Map<TributoApuracao, Long> retencoesCaixa,
    long cbsNotasCaixa,
    long ibsNotasCaixa
) {
    public MesApuracao {
        retencoes = retencoes == null ? Map.of() : Map.copyOf(retencoes);
        retencoesCaixa = retencoesCaixa == null ? Map.of() : Map.copyOf(retencoesCaixa);
    }

    /** Mesmos números de retenção e CBS/IBS nos dois regimes (simulador). */
    public MesApuracao(YearMonth competencia, long receitaCompetenciaEquiparada, long receitaCompetenciaPadrao,
                       long receitaCaixaEquiparada, long receitaCaixaPadrao, Map<TributoApuracao, Long> retencoes,
                       long cbsNotas, long ibsNotas) {
        this(competencia, receitaCompetenciaEquiparada, receitaCompetenciaPadrao, receitaCaixaEquiparada,
             receitaCaixaPadrao, retencoes, cbsNotas, ibsNotas, retencoes, cbsNotas, ibsNotas);
    }

    public static MesApuracao vazio(YearMonth competencia) {
        return new MesApuracao(competencia, 0, 0, 0, 0, Map.of(), 0, 0);
    }

    public long retencao(TributoApuracao t) {
        return retencoes.getOrDefault(t, 0L);
    }

    public long retencaoCaixa(TributoApuracao t) {
        return retencoesCaixa.getOrDefault(t, 0L);
    }
}
