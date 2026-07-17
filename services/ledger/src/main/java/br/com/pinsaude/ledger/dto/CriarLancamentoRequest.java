package br.com.pinsaude.ledger.dto;

import br.com.pinsaude.ledger.domain.TipoOrigem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Requisição de criação de lançamento (chamada interna, service token).
 * Valores em centavos. dataLancamento é opcional (default: hoje).
 */
public record CriarLancamentoRequest(
    @NotBlank String cnpjIdTenant,
    UUID medicoId,
    LocalDate dataLancamento,
    @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}", message = "competencia deve estar no formato YYYY-MM")
    String competencia,
    @NotNull TipoOrigem tipoOrigem,
    UUID origemId,
    @NotBlank String descricao,
    @NotBlank String correlationId,
    @NotEmpty @Valid List<PartidaRequest> partidas
) {}
