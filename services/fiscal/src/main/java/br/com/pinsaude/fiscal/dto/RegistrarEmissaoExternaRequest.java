package br.com.pinsaude.fiscal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Registro de uma NFS-e emitida fora do sistema (portal da prefeitura). Os dados da nota são os
 * mesmos da emissão pelo sistema; número e data vêm do documento emitido. PDF/XML chegam como
 * partes separadas do multipart.
 */
public record RegistrarEmissaoExternaRequest(
    @NotNull @Valid EmitirNfseRequest nota,
    @NotBlank @Size(max = 20) String numeroNota,
    @NotNull @PastOrPresent LocalDate dataEmissao
) {}
