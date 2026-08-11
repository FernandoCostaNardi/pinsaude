package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TomadorHorarioPadraoRequest(
    @NotBlank @Pattern(regexp = "DIURNO|NOTURNO", message = "turno deve ser DIURNO ou NOTURNO") String turno,
    @NotNull @DecimalMin(value = "0.5", message = "horas deve ser maior que zero") BigDecimal horas,
    @NotBlank @Size(max = 30) String horario,
    int ordem,
    boolean ativo
) {}
