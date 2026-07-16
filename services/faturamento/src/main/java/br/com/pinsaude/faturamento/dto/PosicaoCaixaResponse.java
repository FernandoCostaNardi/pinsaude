package br.com.pinsaude.faturamento.dto;

import java.util.List;
import java.util.UUID;

public record PosicaoCaixaResponse(
        long aReceber,
        long recebidoNaoRepassado,
        long repassadoNoMes,
        long saldoEstimado,
        List<NotaEmAberto> notasEmAberto,
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

    public record RecebimentoSemana(
            String semanaKey,
            String semanaLabel,
            long valor
    ) {}
}
