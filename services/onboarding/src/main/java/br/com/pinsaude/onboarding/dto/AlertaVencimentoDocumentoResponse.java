package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.TipoDocumentoEmpresa;

import java.time.LocalDate;
import java.util.UUID;

public record AlertaVencimentoDocumentoResponse(
    UUID documentoId,
    UUID empresaId,
    String empresaNome,
    TipoDocumentoEmpresa tipo,
    String nomeArquivo,
    LocalDate dataValidade,
    long diasRestantes
) {}
