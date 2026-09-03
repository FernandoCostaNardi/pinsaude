package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record TomadorModalidadeRequest(
    @NotBlank @Size(max = 120) String nome,
    // Pedido do cliente: uma modalidade pode ter mais de um Tipo de Escala, desde que todos
    // pertençam à mesma família de comportamento (validado em
    // TomadorService.aplicarCamposPorTipo — não dá pra expressar "mesma família" via anotação
    // Bean Validation simples).
    @NotEmpty List<@NotBlank @Pattern(regexp = "PLANTONISTA|DIARISTA|EVOLUCIONISTA|EVOLUCIONISTA_FDS",
        message = "cada tipo deve ser PLANTONISTA, DIARISTA, EVOLUCIONISTA ou EVOLUCIONISTA_FDS") String> tipos,
    @Pattern(regexp = "DIURNO|NOTURNO", message = "turno deve ser DIURNO ou NOTURNO") String turno,
    @Size(max = 30) String horario,
    @DecimalMin("0.5") BigDecimal horas,
    @Min(0) long valorCentavos,
    @Min(0) long deslocamentoCentavos,
    boolean ativo,
    // ─── Campo do tipo DIARISTA ───
    @DecimalMin("0.5") BigDecimal horasSemanais
) {}
