package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.EstadoCivil;
import br.com.pinsaude.onboarding.service.ValidCpf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Dados civis e profissionais coletados na primeira etapa do auto-cadastro público
 * de médico (EPIC-14.2). Usado tanto na criação (POST) quanto na atualização (PUT)
 * da candidatura — mesmo padrão de "full resource" já usado em MedicoRequest.
 */
public record CandidaturaPublicaRequest(
    @NotBlank @Size(max = 200) String nome,
    @NotBlank @ValidCpf String cpf,
    @NotBlank @Size(max = 20) String crm,
    @NotBlank @Size(min = 2, max = 2) String crmUf,
    @NotBlank @Size(max = 200) String email,
    @Size(max = 20) String telefone,

    LocalDate dataNascimento,
    @Size(max = 100) String nacionalidade,
    @Size(max = 150) String naturalidade,
    EstadoCivil estadoCivil,
    @Size(max = 200) String nomeMae,
    @Size(max = 200) String nomePai,

    @Size(max = 255) String logradouro,
    @Size(max = 20) String numero,
    @Size(max = 100) String complemento,
    @Size(max = 100) String bairro,
    @Size(max = 150) String cidade,
    @Size(min = 2, max = 2) String uf,
    @Size(max = 9) String cep,

    @Size(max = 20) String rgNumero,
    @Size(max = 20) String rgOrgaoExpedidor,
    @Size(min = 2, max = 2) String rgUf,
    @Size(max = 20) String rqe,

    @Size(max = 50) String canalOrigem,
    @Size(max = 200) String nomeIndicador,
    List<String> situacaoFormacao,
    String areasAtuacao,
    String procedimentosRealiza
) {}
