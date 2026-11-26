package br.com.pinsaude.fiscal.apuracao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * APU-00 — resultado de um tributo na apuração (centavos).
 *
 * @param fechaNesteMes  o período do tributo termina na competência apurada (trimestral: mar/jun/set/dez).
 *                       Se false, os valores são o acumulado parcial do período e não há guia.
 * @param saldo          devido + adicional − retenções, nunca negativo
 * @param saldoCredor    retenções que sobraram (retido maior que o devido)
 * @param abaixoMinimo   saldo positivo menor que o valor mínimo da guia: acumula para o próximo período
 * @param valorARecolher o que vai para a guia nesta competência
 * @param memoria        passo a passo do cálculo, para a contabilidade conferir
 */
public record TributoApurado(
    String tributo,
    String periodicidade,
    String regimeReconhecimento,
    String baseCalculo,
    String periodoInicio,
    String periodoFim,
    boolean fechaNesteMes,
    long receita,
    long base,
    BigDecimal aliquota,
    long valorDevido,
    long adicional,
    long retencoes,
    long saldo,
    long saldoCredor,
    boolean abaixoMinimo,
    long valorARecolher,
    LocalDate vencimento,
    String codigoReceita,
    String guia,
    List<String> memoria
) {}
