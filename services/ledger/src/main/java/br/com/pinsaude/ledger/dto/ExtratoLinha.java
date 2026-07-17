package br.com.pinsaude.ledger.dto;

import br.com.pinsaude.ledger.domain.TipoOrigem;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Projeção interna (constructor expression JPQL) de uma linha do extrato:
 * o efeito líquido, em centavos, de um lançamento sobre a conta de repasse do médico.
 * netCentavos = SUM(crédito) - SUM(débito) das partidas do lançamento naquela conta.
 */
public record ExtratoLinha(
    UUID lancamentoId,
    LocalDate dataLancamento,
    String competencia,
    TipoOrigem tipoOrigem,
    UUID origemId,
    String descricao,
    long netCentavos
) {}
