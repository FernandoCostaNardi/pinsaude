package br.com.pinsaude.faturamento.port;

import br.com.pinsaude.faturamento.dto.ReceitaFederalResponse;

import java.util.Optional;

public interface ConsultaCnpjPort {
    Optional<ReceitaFederalResponse> consultar(String cnpj);
}
