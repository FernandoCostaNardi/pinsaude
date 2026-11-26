package br.com.pinsaude.fiscal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * APU-00 — criação/edição de uma regra de apuração. Frações decimais (0.08 = 8%), valores em centavos.
 * {@code cnpj}: empresa da regra, só para quem não tem cnpj_id no token (gestão); vazio = padrão global.
 * Com cnpj_id no token a regra é sempre da empresa do token.
 */
public record RegraApuracaoRequest(
    String cnpj,
    @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Formato: AAAA-MM") String competenciaInicio,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal presuncaoIrpjEquiparado,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal presuncaoCsllEquiparado,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal presuncaoIrpjPadrao,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal presuncaoCsllPadrao,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal aliqAdicionalIrpj,
    @NotNull @PositiveOrZero Long limiteAdicionalIrpjMensal,
    @NotNull @PositiveOrZero Long tetoReceitaAnual,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal alertaTetoPercentual,
    @NotNull @PositiveOrZero Long valorMinimoGuia,
    @Size(max = 2000) String fonte,
    @Size(max = 4000) String observacoes,
    @NotEmpty List<@Valid TributoRegraApuracaoRequest> tributos
) {}
