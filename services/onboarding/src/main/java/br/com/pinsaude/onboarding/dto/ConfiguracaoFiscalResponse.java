package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.ConfiguracaoFiscal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ConfiguracaoFiscalResponse(
    UUID empresaId,
    String cnaeCodigo,
    String cnaeDescricao,
    String codigoLc116,
    boolean indicadorEquiparacaoHospitalar,
    LocalDate vencimentoCertificadoA1,
    String statusCertificado,   // VALIDO | EXPIRANDO | VENCIDO | NAO_CONFIGURADO
    String updatedBy,
    OffsetDateTime updatedAt,
    List<AliquotaCompetenciaResponse> historicoAliquotas
) {
    public static ConfiguracaoFiscalResponse from(
            ConfiguracaoFiscal config,
            List<AliquotaCompetenciaResponse> historico) {
        return new ConfiguracaoFiscalResponse(
            config.getEmpresaId(),
            config.getCnaeCodigo(),
            config.getCnaeDescricao(),
            config.getCodigoLc116(),
            config.isIndicadorEquiparacaoHospitalar(),
            config.getVencimentoCertificadoA1(),
            calcularStatusCertificado(config.getVencimentoCertificadoA1()),
            config.getUpdatedBy(),
            config.getUpdatedAt(),
            historico
        );
    }

    private static String calcularStatusCertificado(LocalDate vencimento) {
        if (vencimento == null) return "NAO_CONFIGURADO";
        LocalDate hoje = LocalDate.now();
        if (vencimento.isBefore(hoje)) return "VENCIDO";
        if (vencimento.isBefore(hoje.plusDays(30))) return "EXPIRANDO";
        return "VALIDO";
    }
}
