package br.com.pinsaude.fiscal.dto;

import java.util.List;

/**
 * CBS-32 — tributos das notas emitidas num mês (pela data de emissão), para a Posição de Caixa.
 * Valores em centavos, lidos do snapshot gravado em cada nota (CBS-11), nunca do parâmetro atual.
 * <ul>
 *   <li>{@code retencoes}: ISS/IR/CSLL/PIS/COFINS gravados nas notas, que são os retidos pelo
 *       tomador (desde a CBS-21). No regime IBS_CBS não há PIS/COFINS.</li>
 *   <li>{@code cbsIbs}: CBS e IBS das notas IBS_CBS — débito próprio da Pin, a recolher (sem retenção
 *       na fonte enquanto o split payment não vale).</li>
 * </ul>
 */
public record TributosMesResponse(
        String mes,                 // AAAA-MM; nulo = todos os períodos
        int quantidadeNotas,
        long valorNotas,
        Retencoes retencoes,
        CbsIbs cbsIbs,
        List<PorRegime> porRegime   // só regimes com nota no período; LEGADO antes de IBS_CBS
) {

    public record Retencoes(long iss, long ir, long csll, long pis, long cofins, long total) {}

    public record CbsIbs(long cbs, long ibs, long total) {}

    public record PorRegime(String regime, int quantidadeNotas, long valorNotas,
                            Retencoes retencoes, CbsIbs cbsIbs) {}
}
