package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TomadorModalidadeRequest(
    @NotBlank @Size(max = 120) String nome,
    @NotBlank @Pattern(regexp = "PLANTONISTA|DIARISTA|EVOLUCIONISTA|EVOLUCIONISTA_FDS",
        message = "tipo deve ser PLANTONISTA, DIARISTA, EVOLUCIONISTA ou EVOLUCIONISTA_FDS") String tipo,
    @Pattern(regexp = "DIURNO|NOTURNO", message = "turno deve ser DIURNO ou NOTURNO") String turno,
    @Size(max = 30) String horario,
    @DecimalMin("0.5") BigDecimal horas,
    @Min(0) long valorCentavos,
    @Min(0) long deslocamentoCentavos,
    boolean ativo,
    // ─── Campo do tipo DIARISTA ───
    @DecimalMin("0.5") BigDecimal horasSemanais
) {}
