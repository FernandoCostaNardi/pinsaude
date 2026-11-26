package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** APU-00 — configuração de um tributo na regra de apuração. Alíquota em fração (0.15 = 15%). */
public record TributoRegraApuracaoRequest(
    @NotNull TributoApuracao tributo,
    boolean ativo,
    @NotNull AplicacaoRegime aplicaEm,
    @DecimalMin("0") @DecimalMax("1") BigDecimal aliquota,
    @NotNull BaseCalculoApuracao baseCalculo,
    @NotNull RegimeReconhecimento regimeReconhecimento,
    @NotNull PeriodicidadeApuracao periodicidade,
    boolean deduzRetencao,
    @Size(max = 10) String codigoReceita,
    @Size(max = 40) String guia,
    @Min(1) @Max(31) Integer vencimentoDia,
    @Min(0) @Max(12) Integer vencimentoMesesApos,
    AjusteVencimento vencimentoAjuste
) {}
