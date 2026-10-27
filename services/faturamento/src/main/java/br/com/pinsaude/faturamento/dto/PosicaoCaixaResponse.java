package br.com.pinsaude.faturamento.dto;

import java.util.List;
import java.util.UUID;

public record PosicaoCaixaResponse(
        long aReceber,
        long recebidoNaoRepassado,
        long aRepassar,                 // parte dos médicos nas produções recebidas e não repassadas
        long repassadoNoMes,
        long saldoEstimado,
        List<NotaEmAberto> notasEmAberto,
        List<RecebidaAguardandoRepasse> recebidasAguardandoRepasse,
        List<RecebimentoSemana> recebimentosPorSemana
) {

    public record NotaEmAberto(
            UUID producaoId,
            UUID medicoId,
            String tomadorNome,
            long valorBruto,
            String dataReferencia,
            int diasEmAberto
    ) {}

    public record RecebidaAguardandoRepasse(
            UUID producaoId,
            String tomadorNome,
            String competencia,
            String recebidoEm,
            long valorBruto,
            long valorRecebido,          // crédito do extrato (líquido das retenções na fonte)
            long aRepassar,              // bruto − taxa Pin, somado entre os médicos
            int qtdMedicos
    ) {}

    public record RecebimentoSemana(
            String semanaKey,
            String semanaLabel,
            long valor
    ) {}
}
