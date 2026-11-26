package br.com.pinsaude.fiscal.apuracao;

import br.com.pinsaude.fiscal.domain.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * APU-00 — fórmulas da apuração mensal, sem nenhum valor fixo: tudo vem da {@link RegraApuracao}.
 * Função pura (sem banco): o simulador e o motor da APU-01 alimentam com os números dos meses.
 *
 * <p>Para cada tributo ativo da regra que vale no regime da empresa (chave CBS/IBS):
 * <ol>
 *   <li>Período: o mês (MENSAL) ou o trimestre civil até a competência (TRIMESTRAL).</li>
 *   <li>Receita do período pelo regime de reconhecimento do tributo (caixa ou competência),
 *       separada em equiparada e padrão.</li>
 *   <li>Base: receita bruta; ou receita × presunção (IRPJ/CSLL, equiparada e padrão);
 *       ou soma do valor calculado nas notas (CBS/IBS).</li>
 *   <li>Devido = base × alíquota (CBS/IBS sem alíquota usam o cronograma; ISS × fator do ano).</li>
 *   <li>Adicional de IRPJ = (base − limite mensal × meses do período) × alíquota do adicional, se positivo.</li>
 *   <li>Retenções na fonte do período (pelo mesmo regime do tributo) abatem o devido (se o tributo deduz);
 *       sobra vira saldo credor.</li>
 *   <li>Guia só no mês que fecha o período e se o saldo atinge o valor mínimo; vencimento pela regra.</li>
 * </ol>
 * Arredondamento: HALF_UP em centavos a cada valor calculado.
 */
public final class CalculadoraApuracao {

    private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CalculadoraApuracao() {}

    public static ResultadoApuracao calcular(RegraApuracao regra, String cnpj, RegimeTributario regime,
                                             YearMonth competencia, List<MesApuracao> meses,
                                             AliquotasReformaAno reforma) {
        AliquotasReformaAno aliqReforma = reforma == null ? AliquotasReformaAno.NENHUMA : reforma;
        Map<YearMonth, MesApuracao> porMes = meses == null ? Map.of()
            : meses.stream().collect(Collectors.toMap(MesApuracao::competencia, Function.identity(), (a, b) -> b));

        List<String> avisos = new ArrayList<>();
        if (!regra.isHomologado()) {
            avisos.add("Regra de apuração ainda não homologada pela contabilidade: valores são estimativa.");
        }

        List<TributoApurado> resultado = new ArrayList<>();
        List<String> semDados = new ArrayList<>();
        regra.getTributos().stream()
            .filter(RegraApuracaoTributo::isAtivo)
            .filter(t -> t.getAplicaEm().vale(regime))
            .sorted(Comparator.comparing(RegraApuracaoTributo::getTributo))
            .forEach(t -> resultado.add(calcularTributo(regra, t, competencia, porMes, aliqReforma, avisos, semDados)));

        if (!semDados.isEmpty()) {
            avisos.add("Sem números de " + String.join(", ", semDados.stream().distinct().sorted().toList())
                + ": considerados zero no trimestre.");
        }

        long totalDevido = 0, totalRet = 0, totalRecolher = 0;
        for (TributoApurado t : resultado) {
            if (!t.fechaNesteMes()) continue;
            totalDevido += t.valorDevido() + t.adicional();
            totalRet += Math.min(t.retencoes(), t.valorDevido() + t.adicional());
            totalRecolher += t.valorARecolher();
        }

        return new ResultadoApuracao(competencia.toString(), cnpj, regime.name(),
            regra.getId() == null ? null : regra.getId().toString(), regra.getCompetenciaInicio(),
            regra.isGlobal(), regra.isHomologado(), !regra.isHomologado(),
            List.copyOf(resultado), totalDevido, totalRet, totalRecolher, List.copyOf(avisos));
    }

    private static TributoApurado calcularTributo(RegraApuracao regra, RegraApuracaoTributo t, YearMonth competencia,
                                                  Map<YearMonth, MesApuracao> porMes, AliquotasReformaAno reforma,
                                                  List<String> avisos, List<String> semDados) {
        TributoApuracao tributo = t.getTributo();
        PeriodicidadeApuracao per = t.getPeriodicidade();
        YearMonth inicio = inicioDoPeriodo(competencia, per);
        YearMonth fimPeriodo = inicio.plusMonths(per.meses() - 1L);
        boolean fecha = competencia.equals(fimPeriodo);
        boolean caixa = t.getRegimeReconhecimento() == RegimeReconhecimento.CAIXA;
        List<String> memoria = new ArrayList<>();

        long recEq = 0, recPad = 0, retidas = 0, cbsNotas = 0, ibsNotas = 0;
        for (YearMonth m = inicio; !m.isAfter(competencia); m = m.plusMonths(1)) {
            MesApuracao mes = porMes.get(m);
            if (mes == null) {
                if (per == PeriodicidadeApuracao.TRIMESTRAL) semDados.add(m.toString());
                continue;
            }
            recEq  += caixa ? mes.receitaCaixaEquiparada() : mes.receitaCompetenciaEquiparada();
            recPad += caixa ? mes.receitaCaixaPadrao()     : mes.receitaCompetenciaPadrao();
            retidas += caixa ? mes.retencaoCaixa(tributo) : mes.retencao(tributo);
            cbsNotas += caixa ? mes.cbsNotasCaixa() : mes.cbsNotas();
            ibsNotas += caixa ? mes.ibsNotasCaixa() : mes.ibsNotas();
        }
        long receita = recEq + recPad;
        String periodo = per == PeriodicidadeApuracao.MENSAL ? competencia.toString()
            : inicio + " a " + fimPeriodo + (fecha ? "" : " (parcial até " + competencia + ")");
        memoria.add("Período " + periodo + ", receita por " + (caixa ? "caixa" : "competência") + ": "
            + brl(receita) + " (equiparada " + brl(recEq) + ", sem equiparação " + brl(recPad) + ")");

        long base;
        BigDecimal aliquota = t.getAliquota();
        long devido;
        switch (t.getBaseCalculo()) {
            case BASE_PRESUMIDA -> {
                BigDecimal presEq  = tributo == TributoApuracao.CSLL ? regra.getPresuncaoCsllEquiparado() : regra.getPresuncaoIrpjEquiparado();
                BigDecimal presPad = tributo == TributoApuracao.CSLL ? regra.getPresuncaoCsllPadrao()     : regra.getPresuncaoIrpjPadrao();
                long baseEq = aplicar(recEq, presEq);
                long basePad = aplicar(recPad, presPad);
                base = baseEq + basePad;
                memoria.add("Base presumida: " + brl(recEq) + " × " + pct(presEq) + " + " + brl(recPad) + " × "
                    + pct(presPad) + " = " + brl(base));
                devido = aplicar(base, aliquota);
                memoria.add("Devido: " + brl(base) + " × " + pct(aliquota) + " = " + brl(devido));
            }
            case SOMA_NOTAS -> {
                base = receita;
                devido = tributo == TributoApuracao.IBS ? ibsNotas : tributo == TributoApuracao.CBS ? cbsNotas : 0;
                aliquota = null;
                memoria.add("Devido: soma do " + tributo + " calculado nas notas do período = " + brl(devido));
            }
            default -> {
                base = receita;
                if (aliquota == null && tributo.isReforma()) {
                    aliquota = tributo == TributoApuracao.CBS ? reforma.cbs() : reforma.ibs();
                    if (aliquota == null) {
                        avisos.add(tributo + ": alíquota do ano não fixada no cronograma da reforma; considerado zero.");
                    } else {
                        memoria.add("Alíquota do cronograma da reforma (sem redução do serviço): " + pct(aliquota));
                    }
                }
                if (tributo == TributoApuracao.ISS && aliquota != null && reforma.fatorIss() != null
                        && reforma.fatorIss().compareTo(BigDecimal.ONE) != 0) {
                    BigDecimal efetiva = aliquota.multiply(reforma.fatorIss()).setScale(8, RoundingMode.HALF_UP);
                    memoria.add("ISS × fator do ano " + reforma.fatorIss().stripTrailingZeros().toPlainString()
                        + ": " + pct(aliquota) + " → " + pct(efetiva));
                    aliquota = efetiva;
                }
                devido = aplicar(base, aliquota);
                memoria.add("Devido: " + brl(base) + " × " + pct(aliquota) + " = " + brl(devido));
            }
        }

        long adicional = 0;
        if (tributo == TributoApuracao.IRPJ && regra.getAliqAdicionalIrpj() != null
                && regra.getAliqAdicionalIrpj().signum() > 0) {
            long limite = regra.getLimiteAdicionalIrpjMensal() * per.meses();
            long excedente = Math.max(0, base - limite);
            adicional = aplicar(excedente, regra.getAliqAdicionalIrpj());
            memoria.add("Adicional: (" + brl(base) + " − limite " + brl(limite) + ") × "
                + pct(regra.getAliqAdicionalIrpj()) + " = " + brl(adicional));
        }

        long total = devido + adicional;
        long abatido = t.isDeduzRetencao() ? retidas : 0;
        if (t.isDeduzRetencao()) {
            if (retidas > 0) memoria.add("(−) Retenções na fonte do período: " + brl(retidas));
        } else if (retidas > 0) {
            memoria.add("Retenções de " + brl(retidas) + " não abatidas (regra não deduz)");
        }
        long saldo = Math.max(0, total - abatido);
        long credor = Math.max(0, abatido - total);
        if (credor > 0) memoria.add("Saldo credor (retido maior que o devido): " + brl(credor));

        long minimo = regra.getValorMinimoGuia() == null ? 0 : regra.getValorMinimoGuia();
        boolean abaixoMinimo = fecha && saldo > 0 && saldo < minimo;
        long aRecolher = fecha && !abaixoMinimo ? saldo : 0;
        LocalDate vencimento = null;
        if (!fecha) {
            memoria.add("Período ainda não fechou: guia só em " + fimPeriodo);
        } else if (abaixoMinimo) {
            memoria.add("Saldo " + brl(saldo) + " abaixo do mínimo da guia (" + brl(minimo) + "): acumula para o próximo período");
        } else if (saldo > 0) {
            vencimento = vencimento(fimPeriodo, t);
            memoria.add("A recolher: " + brl(aRecolher) + (vencimento == null
                ? " (vencimento não cadastrado na regra)" : " até " + vencimento.format(DD_MM_AAAA)));
            if (vencimento == null) avisos.add(tributo + ": vencimento não cadastrado na regra de apuração.");
        }

        return new TributoApurado(tributo.name(), per.name(), t.getRegimeReconhecimento().name(),
            t.getBaseCalculo().name(), inicio.toString(), fimPeriodo.toString(), fecha,
            receita, base, aliquota, devido, adicional, abatido, saldo, credor, abaixoMinimo, aRecolher,
            vencimento, t.getCodigoReceita(), t.getGuia(), List.copyOf(memoria));
    }

    static YearMonth inicioDoPeriodo(YearMonth competencia, PeriodicidadeApuracao per) {
        if (per == PeriodicidadeApuracao.MENSAL) return competencia;
        int primeiroMes = ((competencia.getMonthValue() - 1) / 3) * 3 + 1;
        return YearMonth.of(competencia.getYear(), primeiroMes);
    }

    /** Vencimento: dia da regra (limitado ao fim do mês) N meses após o fim do período, ajustado no fim de semana. */
    static LocalDate vencimento(YearMonth fimPeriodo, RegraApuracaoTributo t) {
        if (t.getVencimentoDia() == null || t.getVencimentoMesesApos() == null) return null;
        YearMonth mes = fimPeriodo.plusMonths(t.getVencimentoMesesApos());
        LocalDate data = mes.atDay(Math.min(t.getVencimentoDia(), mes.lengthOfMonth()));
        AjusteVencimento ajuste = t.getVencimentoAjuste() == null ? AjusteVencimento.NENHUM : t.getVencimentoAjuste();
        while (ajuste != AjusteVencimento.NENHUM
                && (data.getDayOfWeek() == DayOfWeek.SATURDAY || data.getDayOfWeek() == DayOfWeek.SUNDAY)) {
            data = ajuste == AjusteVencimento.ANTECIPA ? data.minusDays(1) : data.plusDays(1);
        }
        return data;
    }

    static long aplicar(long valor, BigDecimal fracao) {
        if (fracao == null || valor == 0) return 0;
        return BigDecimal.valueOf(valor).multiply(fracao).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static String brl(long centavos) {
        String sinal = centavos < 0 ? "-" : "";
        long abs = Math.abs(centavos);
        String inteiro = String.format(Locale.ROOT, "%,d", abs / 100).replace(',', '.');
        return sinal + "R$ " + inteiro + "," + String.format("%02d", abs % 100);
    }

    static String pct(BigDecimal fracao) {
        if (fracao == null) return "0%";
        return fracao.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }
}
