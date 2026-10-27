package br.com.pinsaude.faturamento.dto;

import java.util.List;
import java.util.UUID;

/**
 * Posição de Caixa: só o que já entrou no banco (créditos do extrato conciliados com uma produção).
 * O resumo e a página respeitam o filtro (mês do depósito e busca por tomador); o histórico mensal
 * sempre mostra os últimos 6 meses. {@code aReceber} é só um lembrete do que ainda falta entrar.
 */
public record PosicaoCaixaResponse(
        Resumo resumo,
        List<RecebimentoMes> recebimentosPorMes,
        Pagina recebimentos,
        long aReceber,                  // notas emitidas ainda sem crédito conciliado (bruto)
        int notasAguardandoPagamento
) {

    public record Resumo(
            int quantidade,
            long valorNotas,            // bruto das notas recebidas
            long retencoes,             // valorNotas − recebido (impostos retidos pelo tomador)
            long recebido,              // o que de fato entrou no banco
            long parteMedicos,          // bruto − taxa Pin, por participação
            long repassado,             // EPIC-09 pendente: sempre 0
            long aRepassar,             // parteMedicos − repassado
            long resultadoPin           // recebido − parteMedicos
    ) {}

    public record Recebimento(
            UUID producaoId,
            String tomadorNome,
            String competencia,
            String recebidoEm,          // YYYY-MM-DD (data do depósito)
            long valorNota,
            long retencoes,
            long valorRecebido,
            long parteMedicos,
            long resultadoPin,
            int qtdMedicos,
            String tipoMatch,           // AUTOMATICO | MANUAL
            String statusRepasse        // AGUARDANDO (EPIC-09 pendente)
    ) {}

    public record RecebimentoMes(
            String mes,                 // YYYY-MM
            long valor,
            int quantidade
    ) {}

    public record Pagina(
            List<Recebimento> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
