package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.RegimeTributario;
import br.com.pinsaude.onboarding.service.ValidCnpj;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EmpresaRequest(
    @NotBlank @ValidCnpj String cnpj,
    @NotBlank String razaoSocial,
    @NotBlank String inscricaoMunicipal,
    String municipio,
    String codigoMunicipioIbge,
    @NotNull RegimeTributario regimeTributario,
    String logradouro,
    String bairro,
    String uf,
    String cep,
    String telefone,
    String emailContato
) {}
