package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Map;

public record CalculoFiscalRequest(

    /** Valor bruto do serviço em centavos. */
    @Positive
    long valorBruto,

    /** Competência no formato YYYY-MM (ex: "2024-06"). */
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "competencia deve estar no formato YYYY-MM")
    String competencia,

    /** true = tomador é Pessoa Jurídica; false = Pessoa Física. */
    boolean tomadorPj,

    /** PJ: tomador retém IR/CSLL/PIS/COFINS na fonte. */
    boolean indicadorRetencaoFederal,

    /** PJ: tomador retém ISS (substituição tributária). */
    boolean indicadorRetencaoIss,

    /** PF: tomador é equiparado a hospital (nota com tributos zerados). */
    boolean equiparacaoHospitalar,

    /**
     * Alíquotas do tomador que sobrescrevem as da empresa (chave = ISS|IR|CSLL|PIS|COFINS, valor = fração decimal 0.05 = 5%).
     * Nullable — quando ausente o motor usa as alíquotas configuradas na empresa.
     */
    Map<String, BigDecimal> aliquotasOverride,

    // ─── Reforma tributária (CBS-13). Todos opcionais; só usados no regime IBS_CBS. ───

    /** Redução de CBS/IBS do serviço LC116, em fração (0.6 = 60%). Null = sem redução. */
    @DecimalMin("0") @DecimalMax("1")
    BigDecimal reducaoIbsCbs,

    /** CST de IBS/CBS do serviço (3 dígitos). Só é copiado para a resposta. */
    @Pattern(regexp = "\\d{3}", message = "cstIbsCbs deve ter 3 dígitos")
    String cstIbsCbs,

    /** cClassTrib do serviço (6 dígitos). Só é copiado para a resposta. */
    @Pattern(regexp = "\\d{6}", message = "cClassTrib deve ter 6 dígitos")
    String cClassTrib,

    /**
     * Acerto comercial com o tomador (CBS-01): true = CBS/IBS somados ao preço (por fora);
     * false/null = CBS/IBS dentro do valor bruto (o bruto já inclui os tributos).
     */
    Boolean cbsIbsPorFora
) {

    /** Construtor do regime anterior (sem classificação CBS/IBS do serviço). */
    public CalculoFiscalRequest(long valorBruto, String competencia, boolean tomadorPj,
                                boolean indicadorRetencaoFederal, boolean indicadorRetencaoIss,
                                boolean equiparacaoHospitalar, Map<String, BigDecimal> aliquotasOverride) {
        this(valorBruto, competencia, tomadorPj, indicadorRetencaoFederal, indicadorRetencaoIss,
             equiparacaoHospitalar, aliquotasOverride, null, null, null, null);
    }
}
