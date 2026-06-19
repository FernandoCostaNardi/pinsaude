package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.Servico;

import java.math.BigDecimal;
import java.util.UUID;

public record ServicoResponse(
    UUID id,
    String codigoLc116,
    String cnae,
    String descricaoPadrao,
    boolean indicadorEquiparacao,
    BigDecimal aliquotaIss,
    BigDecimal aliquotaIr,
    BigDecimal aliquotaCsll,
    BigDecimal aliquotaPis,
    BigDecimal aliquotaCofins
) {
    public static ServicoResponse from(Servico s) {
        return new ServicoResponse(
            s.getId(), s.getCodigoLc116(), s.getCnae(), s.getDescricaoPadrao(),
            s.isIndicadorEquiparacao(), s.getAliquotaIss(), s.getAliquotaIr(),
            s.getAliquotaCsll(), s.getAliquotaPis(), s.getAliquotaCofins()
        );
    }
}
