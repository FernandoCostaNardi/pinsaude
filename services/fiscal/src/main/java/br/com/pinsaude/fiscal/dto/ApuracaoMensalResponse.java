package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.apuracao.ResultadoApuracao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * APU-01 — apuração mensal de uma empresa sobre as notas reais (centavos).
 *
 * @param apuracao              resultado da calculadora da APU-00 (tributos, guias, vencimentos, memória,
 *                              {@code aHomologar} enquanto a regra não for homologada) com os avisos do motor
 * @param referenciaCompetencia COMPETENCIA_NOTA | DATA_EMISSAO (da regra)
 * @param usouCaixa             algum tributo aplicável é apurado por caixa (recebimentos lidos do Faturamento)
 * @param meses                 números de cada mês do trimestre até a competência
 * @param notas                 notas que entraram na apuração, para conferência
 */
public record ApuracaoMensalResponse(
    ResultadoApuracao apuracao,
    String referenciaCompetencia,
    boolean usouCaixa,
    List<MesComposicao> meses,
    List<NotaApurada> notas
) {
    /** Receita, retenções e CBS/IBS de um mês, pelos dois regimes. Retenções por tributo (IR em IRPJ). */
    public record MesComposicao(
        String competencia,
        int notasCompetencia,
        long receitaCompetenciaEquiparada,
        long receitaCompetenciaPadrao,
        int notasRecebidas,
        long receitaCaixaEquiparada,
        long receitaCaixaPadrao,
        Map<String, Long> retencoesCompetencia,
        Map<String, Long> retencoesCaixa,
        long cbsCompetencia,
        long ibsCompetencia,
        long cbsCaixa,
        long ibsCaixa
    ) {}

    /**
     * Uma nota da apuração.
     * @param mesCompetencia mês em que entrou na receita por competência (nulo se só entrou por caixa)
     * @param recebidoEm     data do depósito conciliado (nulo se não recebida no período)
     * @param retencoes      soma de ISS/IR/CSLL/PIS/COFINS retidos gravados na nota
     */
    public record NotaApurada(
        UUID notaId,
        UUID producaoId,
        String numeroNota,
        String tomadorNome,
        String competencia,
        LocalDate emitidaEm,
        LocalDate recebidoEm,
        String mesCompetencia,
        long valorBruto,
        String cnae,
        boolean equiparada,
        String regime,
        long retencoes,
        long valorCbs,
        long valorIbs
    ) {}
}
