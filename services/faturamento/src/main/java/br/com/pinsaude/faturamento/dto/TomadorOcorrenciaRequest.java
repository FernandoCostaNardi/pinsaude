package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TomadorOcorrenciaRequest(
    @NotBlank @Size(max = 120) String nome,
    @NotBlank @Pattern(regexp = "PERCENTUAL|FIXO|SEM_VALOR",
        message = "tipoValor deve ser PERCENTUAL, FIXO ou SEM_VALOR") String tipoValor,
    @DecimalMin(value = "0", message = "valor percentual não pode ser negativo") BigDecimal valorPercentual,
    @Min(value = 0, message = "valor não pode ser negativo") Long valorCentavos,
    boolean ativo,
    // Setores Operacionais em que esta ocorrência deve ser sugerida — lista vazia (ou omitida)
    // significa "todos os setores do tomador" (comportamento anterior, sem restrição).
    List<UUID> setorIds
) {
    public TomadorOcorrenciaRequest {
        if (setorIds == null) setorIds = List.of();
    }
}
