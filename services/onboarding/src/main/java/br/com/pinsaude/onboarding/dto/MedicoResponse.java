package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.domain.StatusMedico;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MedicoResponse(
    UUID id,
    String cpf,
    String nome,
    String crm,
    String crmUf,
    String especialidade,
    String email,
    String telefone,
    BigDecimal taxaPinPct,
    StatusMedico status,
    String statusJuntaComercial,
    UUID empresaId,
    List<VinculoEmpresaResponse> empresas,
    DadosBancariosMedicoResponse dadosBancarios,
    List<DocumentoMedicoResponse> documentos,
    ChecklistCondutaResponse checklist,
    ContratoAssinaturaResponse contratoAssinatura,
    EnviarConviteResponse ultimoConvite,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static MedicoResponse from(
            Medico m,
            String cpfDecriptografado,
            List<VinculoEmpresaResponse> empresas,
            DadosBancariosMedicoResponse dadosBancarios,
            List<DocumentoMedicoResponse> documentos,
            ChecklistCondutaResponse checklist,
            ContratoAssinaturaResponse contratoAssinatura,
            EnviarConviteResponse ultimoConvite) {
        UUID primeiraEmpresaId = empresas.isEmpty() ? null : empresas.get(0).empresaId();
        return new MedicoResponse(
            m.getId(), cpfDecriptografado, m.getNome(),
            m.getCrm(), m.getCrmUf(), m.getEspecialidade(),
            m.getEmail(), m.getTelefone(), m.getTaxaPinPct(), m.getStatus(),
            m.getStatusJuntaComercial(),
            primeiraEmpresaId, empresas, dadosBancarios, documentos, checklist,
            contratoAssinatura, ultimoConvite,
            m.getCreatedAt(), m.getUpdatedAt()
        );
    }
}
