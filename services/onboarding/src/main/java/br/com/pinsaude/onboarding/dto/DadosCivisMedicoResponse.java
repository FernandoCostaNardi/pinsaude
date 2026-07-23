package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.DadosCivisMedico;
import br.com.pinsaude.onboarding.domain.EstadoCivil;

import java.time.LocalDate;
import java.util.List;

/**
 * Dados civis/profissionais coletados no auto-cadastro público (EPIC-14.1/14.2) e exibidos
 * na tela de Aprovação de Onboarding (EPIC-14.8) para o operador/gestão revisar a candidatura.
 * Médicos cadastrados manualmente (origemCadastro = MANUAL) não têm essa linha — nesse caso
 * MedicoService.toFullResponse() retorna dadosCivis = null.
 */
public record DadosCivisMedicoResponse(
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
    public static DadosCivisMedicoResponse from(DadosCivisMedico dc) {
        return new DadosCivisMedicoResponse(
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
