package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** CBS-12 — classificação tributária de CBS/IBS de um serviço. Redução como fração (0.6 = 60%). */
public record ServicoClassificacaoRequest(
    @NotBlank @Pattern(regexp = "\\d\\.\\d{4}\\.\\d{2}\\.\\d{2}", message = "NBS no formato 1.2301.19.00") String nbs,
    @NotBlank @Pattern(regexp = "\\d{3}", message = "CST com 3 dígitos") String cstIbsCbs,
    @NotBlank @Pattern(regexp = "\\d{6}", message = "cClassTrib com 6 dígitos") String cClassTrib,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal reducaoIbsCbs
) {}
