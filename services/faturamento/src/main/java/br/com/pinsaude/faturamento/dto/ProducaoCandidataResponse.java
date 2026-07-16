package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.Tomador;

import java.util.UUID;

public record ProducaoCandidataResponse(
        UUID    id,
        String  tomadorNome,
        String  municipio,
        long    valorBruto,
        String  competencia
) {
    public static ProducaoCandidataResponse from(Producao p) {
        Tomador t = p.getTomador();
        return new ProducaoCandidataResponse(
                p.getId(),
                t != null ? t.getRazaoSocialNome() : "—",
                t != null ? t.getMunicipio()        : null,
                p.getValorBruto(),
                p.getCompetencia()
        );
    }
}
