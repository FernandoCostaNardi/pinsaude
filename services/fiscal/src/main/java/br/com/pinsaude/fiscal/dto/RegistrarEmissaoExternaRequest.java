package br.com.pinsaude.fiscal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Registro de uma NFS-e emitida fora do sistema (portal da prefeitura). Os dados da nota são os
 * mesmos da emissão pelo sistema; número e data vêm do documento emitido. PDF/XML chegam como
 * partes separadas do multipart.
 *
 * <p>CBS-42: no regime CBS/IBS o fiscal calcula CBS e IBS pela regra do motor; {@code valorCbs} e
 * {@code valorIbs} (centavos, opcionais) são os valores impressos na nota real, quando a operação
 * precisa corrigir o cálculo. Nulos = vale o cálculo do motor.
 */
public record RegistrarEmissaoExternaRequest(
    @NotNull @Valid EmitirNfseRequest nota,
    @NotBlank @Size(max = 20) String numeroNota,
    @NotNull @PastOrPresent LocalDate dataEmissao,
    @PositiveOrZero Long valorCbs,
    @PositiveOrZero Long valorIbs
) {

    /** Registro sem correção de CBS/IBS (vale o cálculo do motor). */
    public RegistrarEmissaoExternaRequest(EmitirNfseRequest nota, String numeroNota, LocalDate dataEmissao) {
        this(nota, numeroNota, dataEmissao, null, null);
    }
}
