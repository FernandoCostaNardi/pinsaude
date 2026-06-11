package br.com.pinsaude.onboarding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ConfiguracaoFiscalRequest(
    String cnaeCodigo,
    String cnaeDescricao,
    String codigoLc116,
    boolean indicadorEquiparacaoHospitalar,
    LocalDate vencimentoCertificadoA1,
    @Valid @NotNull AliquotaCompetenciaRequest aliquota
) {}
