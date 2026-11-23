package br.com.pinsaude.faturamento.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Motor fiscal do serviço Fiscal (POST /api/motor-fiscal/calcular). CBS-30: o faturamento não
 * calcula tributo nenhum; a prévia da produção usa o mesmo cálculo da emissão da nota.
 *
 * Atenção à escala: no motor as alíquotas são FRAÇÕES (0.05 = 5%); no faturamento os cadastros
 * de serviço e tomador guardam PERCENTUAL (5.0000 = 5%). Quem monta o pedido converte.
 */
public interface MotorFiscalPort {

    /**
     * @param cnpjEmpresa empresa emissora (14 dígitos). O Fiscal só usa quando o token não tem
     *                    cnpj_id (gestão); com cnpj_id no token vale sempre a empresa do token.
     * @throws MotorFiscalException quando o Fiscal recusa o pedido (4xx, com o status e a mensagem
     *                              dele) ou não responde (status 503).
     */
    ResultadoCalculo calcular(String cnpjEmpresa, PedidoCalculo pedido);

    /** Espelho do CalculoFiscalRequest do Fiscal. Alíquotas em fração. */
    record PedidoCalculo(
        long valorBruto,
        String competencia,
        boolean tomadorPj,
        boolean indicadorRetencaoFederal,
        boolean indicadorRetencaoIss,
        boolean equiparacaoHospitalar,
        Map<String, BigDecimal> aliquotasOverride,
        BigDecimal reducaoIbsCbs,
        String cstIbsCbs,
        String cClassTrib,
        Boolean cbsIbsPorFora
    ) {}

    /** Campos do CalculoFiscalResponse do Fiscal que o faturamento usa. Valores em centavos. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResultadoCalculo(
        long valorBruto,
        long taxaPin,
        long valorIss,
        long valorIr,
        long valorCsll,
        long valorPis,
        long valorCofins,
        long totalRetencoes,
        long totalTributos,
        long resultadoPin,
        String cenario,
        String competencia,
        String regime,
        long valorCbs,
        long valorIbs,
        BigDecimal aliqCbs,
        BigDecimal aliqIbs,
        BigDecimal fatorIss,
        String cstIbsCbs,
        String cClassTrib,
        boolean cbsIbsPorFora,
        long baseCalculoIbsCbs,
        long valorTotalNota,
        List<String> tributosRetidos
    ) {}

    class MotorFiscalException extends RuntimeException {
        private final int status;

        public MotorFiscalException(int status, String msg, Throwable cause) {
            super(msg, cause);
            this.status = status;
        }

        public int status() { return status; }
    }
}
