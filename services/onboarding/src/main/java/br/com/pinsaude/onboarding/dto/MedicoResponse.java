package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.domain.StatusMedico;

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
    StatusMedico status,
    String statusJuntaComercial,
    UUID empresaId,
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
            UUID empresaId,
            DadosBancariosMedicoResponse dadosBancarios,
            List<DocumentoMedicoResponse> documentos,
            ChecklistCondutaResponse checklist,
            ContratoAssinaturaResponse contratoAssinatura,
            EnviarConviteResponse ultimoConvite) {
        return new MedicoResponse(
            m.getId(), cpfDecriptografado, m.getNome(),
            m.getCrm(), m.getCrmUf(), m.getEspecialidade(),
            m.getEmail(), m.getTelefone(), m.getStatus(),
            m.getStatusJuntaComercial(),
            empresaId, dadosBancarios, documentos, checklist,
            contratoAssinatura, ultimoConvite,
            m.getCreatedAt(), m.getUpdatedAt()
        );
    }
}
