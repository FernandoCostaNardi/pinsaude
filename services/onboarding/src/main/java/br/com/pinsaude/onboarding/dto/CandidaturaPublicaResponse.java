package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.DadosCivisMedico;
import br.com.pinsaude.onboarding.domain.EstadoCivil;
import br.com.pinsaude.onboarding.domain.Medico;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CandidaturaPublicaResponse(
    UUID id,
    String status,
    String nome,
    String cpf,
    String crm,
    String crmUf,
    String email,
    String telefone,

    LocalDate dataNascimento,
    String nacionalidade,
    String naturalidade,
    EstadoCivil estadoCivil,
    String nomeMae,
    String nomePai,

    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String cidade,
    String uf,
    String cep,

    String rgNumero,
    String rgOrgaoExpedidor,
    String rgUf,
    String rqe,

    String canalOrigem,
    String nomeIndicador,
    List<String> situacaoFormacao,
    String areasAtuacao,
    String procedimentosRealiza
) {
    public static CandidaturaPublicaResponse from(Medico medico, String cpfDecriptografado, DadosCivisMedico dc) {
        return new CandidaturaPublicaResponse(
            medico.getId(),
            medico.getStatus().name(),
            medico.getNome(),
            cpfDecriptografado,
            medico.getCrm(),
            medico.getCrmUf(),
            medico.getEmail(),
            medico.getTelefone(),
            dc.getDataNascimento(),
            dc.getNacionalidade(),
            dc.getNaturalidade(),
            dc.getEstadoCivil(),
            dc.getNomeMae(),
            dc.getNomePai(),
            dc.getLogradouro(),
            dc.getNumero(),
            dc.getComplemento(),
            dc.getBairro(),
            dc.getCidade(),
            dc.getUf(),
            dc.getCep(),
            dc.getRgNumero(),
            dc.getRgOrgaoExpedidor(),
            dc.getRgUf(),
            dc.getRqe(),
            dc.getCanalOrigem(),
            dc.getNomeIndicador(),
            dc.getSituacaoFormacao() != null ? List.of(dc.getSituacaoFormacao()) : null,
            dc.getAreasAtuacao(),
            dc.getProcedimentosRealiza()
        );
    }
}
