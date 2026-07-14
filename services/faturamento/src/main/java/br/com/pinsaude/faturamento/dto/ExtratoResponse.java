package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.BancoEnum;
import br.com.pinsaude.faturamento.domain.ExtratoBancario;
import br.com.pinsaude.faturamento.domain.StatusImportacao;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExtratoResponse(
        UUID id,
        String nomeArquivo,
        BancoEnum banco,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        StatusImportacao statusImportacao,
        int totalLancamentos,
        String createdBy,
        OffsetDateTime dataUpload
) {
    public static ExtratoResponse from(ExtratoBancario e) {
        return new ExtratoResponse(
                e.getId(),
                e.getNomeArquivo(),
                e.getBanco(),
                e.getPeriodoInicio(),
                e.getPeriodoFim(),
                e.getStatusImportacao(),
                e.getTotalLancamentos(),
                e.getCreatedBy(),
                e.getDataUpload()
        );
    }
}
