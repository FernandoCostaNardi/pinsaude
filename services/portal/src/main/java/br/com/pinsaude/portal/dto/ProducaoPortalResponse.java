package br.com.pinsaude.portal.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProducaoPortalResponse(
    UUID id,
    String competencia,
    String tomadorNome,
    String servicoDescricao,
    long valorBrutoCentavos,
    long valorLiquidoEstimadoCentavos,
    String status,
    OffsetDateTime createdAt,
    // true quando o médico pode excluir esta produção (lançada errado): ainda sem NFS-e nem
    // conciliação, fora do Fechamento por Grupo e só com ele de participante.
    boolean podeExcluir
) {}
