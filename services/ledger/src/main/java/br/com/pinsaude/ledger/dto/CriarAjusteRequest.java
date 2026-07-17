package br.com.pinsaude.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Solicitação de ajuste manual (fica PENDENTE até a segunda aprovação). Valor em centavos. */
public record CriarAjusteRequest(
    UUID medicoId,
    @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}", message = "competencia deve estar no formato YYYY-MM")
    String competencia,
    @NotBlank String contaDebitoCodigo,
    @NotBlank String contaCreditoCodigo,
    @Positive long valorCentavos,
    @NotBlank String motivo
) {}
