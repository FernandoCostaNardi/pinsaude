package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record EmitirNfseRequest(
    @NotNull UUID producaoId,
    UUID medicoId,             // null para produções multi-médico
    @NotNull UUID tomadorId,
    @NotNull @Pattern(regexp = "\\d{4}-\\d{2}") String competencia,
    @NotNull @Positive Long valorBruto,
    @NotNull @Positive Long taxaPin,
    Long valorIss,
    Long valorIr,
    Long valorCsll,
    Long valorPis,
    Long valorCofins,
    String tomadorNome,
    String cnaeCodigo,          // CNAE selecionado na emissão (opcional)
    String discriminacao,       // Descrição/discriminação da nota (grupo de faturamento, opcional)
    String cnpjEmpresaEmissora, // CNPJ da empresa Pin emissora; tenant quando o JWT não traz cnpj_id

    // ─── Reforma tributária (CBS-41): classificação do serviço LC116 da produção (CBS-12). ───
    // O regime e os valores de CBS/IBS não vêm do navegador: o fiscal calcula pela chave da empresa.

    /** Redução de CBS/IBS do serviço, em fração (0.6 = 60%). Null = sem redução. */
    @DecimalMin("0") @DecimalMax("1") BigDecimal reducaoIbsCbs,
    /** CST de IBS/CBS do serviço (3 dígitos). Obrigatório quando a competência está no regime IBS_CBS. */
    @Pattern(regexp = "\\d{3}", message = "cstIbsCbs deve ter 3 dígitos") String cstIbsCbs,
    /** cClassTrib do serviço (6 dígitos). Obrigatório quando a competência está no regime IBS_CBS. */
    @Pattern(regexp = "\\d{6}", message = "cClassTrib deve ter 6 dígitos") String cClassTrib,
    /** Acerto com o tomador (CBS-01): true = CBS/IBS somados ao preço; null/false = dentro do bruto. */
    Boolean cbsIbsPorFora
) {

    /** Pedido sem a classificação CBS/IBS do serviço (regime anterior). */
    public EmitirNfseRequest(UUID producaoId, UUID medicoId, UUID tomadorId, String competencia,
                             Long valorBruto, Long taxaPin, Long valorIss, Long valorIr, Long valorCsll,
                             Long valorPis, Long valorCofins, String tomadorNome, String cnaeCodigo,
                             String discriminacao, String cnpjEmpresaEmissora) {
        this(producaoId, medicoId, tomadorId, competencia, valorBruto, taxaPin, valorIss, valorIr,
             valorCsll, valorPis, valorCofins, tomadorNome, cnaeCodigo, discriminacao,
             cnpjEmpresaEmissora, null, null, null, null);
    }
}
