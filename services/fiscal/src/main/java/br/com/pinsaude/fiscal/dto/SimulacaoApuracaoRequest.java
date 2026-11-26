package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.RegimeTributario;
import br.com.pinsaude.fiscal.domain.TributoApuracao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * APU-00 — simulação da apuração de uma competência com números informados (centavos).
 *
 * @param cnpj    empresa (só para quem não tem cnpj_id no token); vazio = padrão global e regime LEGADO
 * @param regraId simula uma regra específica (ex.: rascunho novo); nulo = a vigente na competência
 * @param regime  força o regime (LEGADO/IBS_CBS); nulo = o da empresa na competência (chave CBS/IBS)
 * @param meses   números do mês e, para tributos trimestrais, dos meses anteriores do trimestre
 */
public record SimulacaoApuracaoRequest(
    @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Formato: AAAA-MM") String competencia,
    String cnpj,
    UUID regraId,
    RegimeTributario regime,
    @NotEmpty @Size(max = 12) List<@Valid Mes> meses
) {
    public record Mes(
        @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Formato: AAAA-MM") String competencia,
        @PositiveOrZero long receitaCompetenciaEquiparada,
        @PositiveOrZero long receitaCompetenciaPadrao,
        @PositiveOrZero long receitaCaixaEquiparada,
        @PositiveOrZero long receitaCaixaPadrao,
        Map<TributoApuracao, @PositiveOrZero Long> retencoes,
        @PositiveOrZero long cbsNotas,
        @PositiveOrZero long ibsNotas
    ) {}
}
