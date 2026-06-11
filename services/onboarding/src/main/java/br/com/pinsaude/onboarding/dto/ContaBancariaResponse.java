package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.ContaBancaria;
import br.com.pinsaude.onboarding.domain.TipoConta;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContaBancariaResponse(
    UUID id,
    String banco,
    String agencia,
    String conta,
    TipoConta tipoConta,
    String chavePix,
    boolean principal,
    boolean ativo,
    OffsetDateTime createdAt
) {
    public static ContaBancariaResponse from(ContaBancaria c) {
        return new ContaBancariaResponse(
            c.getId(), c.getBanco(), c.getAgencia(), c.getConta(),
            c.getTipoConta(), c.getChavePix(), c.isPrincipal(),
            c.isAtivo(), c.getCreatedAt()
        );
    }
}
