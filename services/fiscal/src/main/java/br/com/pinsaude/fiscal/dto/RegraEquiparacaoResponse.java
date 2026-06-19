package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.RegraEquiparacao;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RegraEquiparacaoResponse(
    UUID id,
    String cnae,
    String codigoLc116,
    boolean indicadorEquiparacao,
    String regimePresuncao,
    OffsetDateTime createdAt,
    List<HistoricoRegraResponse> historico
) {
    public static RegraEquiparacaoResponse from(RegraEquiparacao r) {
        return new RegraEquiparacaoResponse(
            r.getId(), r.getCnae(), r.getCodigoLc116(),
            Boolean.TRUE.equals(r.getIndicadorEquiparacao()),
            r.getRegimePresuncao(), r.getCreatedAt(), List.of()
        );
    }

    public static RegraEquiparacaoResponse from(RegraEquiparacao r, List<HistoricoRegraResponse> hist) {
        return new RegraEquiparacaoResponse(
            r.getId(), r.getCnae(), r.getCodigoLc116(),
            Boolean.TRUE.equals(r.getIndicadorEquiparacao()),
            r.getRegimePresuncao(), r.getCreatedAt(), hist
        );
    }
}
