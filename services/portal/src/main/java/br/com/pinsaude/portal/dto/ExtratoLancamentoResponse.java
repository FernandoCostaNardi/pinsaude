package br.com.pinsaude.portal.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um lançamento do médico no Extrato do portal: uma Produção (lançada manualmente) ou uma
 * Frequência Médica (plantões/diárias do mês). Valores em centavos.
 */
public record ExtratoLancamentoResponse(
    UUID id,
    String origem,          // "PRODUCAO" | "FREQUENCIA"
    String competencia,     // YYYY-MM
    String tomadorNome,
    String descricao,       // serviço (produção) ou setor operacional (frequência)
    int quantidade,         // plantões lançados (frequência); 1 para produção
    long valorBruto,
    long taxaPin,
    long valorPrevisto,     // o que o médico recebe: bruto − taxa Pin
    String status,          // "PROVISIONADO" | "FATURADO" | "PAGO"
    String numeroNota,      // número da NFS-e quando já faturado
    OffsetDateTime dataRef
) {}
