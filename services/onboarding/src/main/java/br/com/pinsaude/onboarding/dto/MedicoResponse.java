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
    DadosBancariosMedicoResponse dadosBancarios,
    List<DocumentoMedicoResponse> documentos,
    ChecklistCondutaResponse checklist,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static MedicoResponse from(
            Medico m,
            String cpfDecriptografado,
            DadosBancariosMedicoResponse dadosBancarios,
            List<DocumentoMedicoResponse> documentos,
            ChecklistCondutaResponse checklist) {
        return new MedicoResponse(
            m.getId(), cpfDecriptografado, m.getNome(),
            m.getCrm(), m.getCrmUf(), m.getEspecialidade(),
            m.getEmail(), m.getTelefone(), m.getStatus(),
            dadosBancarios, documentos, checklist,
            m.getCreatedAt(), m.getUpdatedAt()
        );
    }
}
