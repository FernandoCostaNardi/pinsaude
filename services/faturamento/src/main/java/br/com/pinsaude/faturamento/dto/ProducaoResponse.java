package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.Tomador;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProducaoResponse(
    UUID id,
    UUID medicoId,
    TomadorResumo tomador,
    ServicoResumo servico,
    long valorBruto,
    String competencia,
    String descricaoComplementar,
    String status,
    OffsetDateTime createdAt
) {
    public record TomadorResumo(UUID id, String razaoSocialNome, String municipio,
                                boolean retencaoFederal, boolean retencaoIss) {
        static TomadorResumo from(Tomador t) {
            return new TomadorResumo(t.getId(), t.getRazaoSocialNome(), t.getMunicipio(),
                t.isIndicadorRetencaoFederal(), t.isIndicadorRetencaoIss());
        }
    }

    public record ServicoResumo(UUID id, String codigoLc116, String descricaoPadrao) {
        static ServicoResumo from(Servico s) {
            return new ServicoResumo(s.getId(), s.getCodigoLc116(), s.getDescricaoPadrao());
        }
    }

    public static ProducaoResponse from(Producao p) {
        return new ProducaoResponse(
            p.getId(),
            p.getMedicoId(),
            TomadorResumo.from(p.getTomador()),
            ServicoResumo.from(p.getServico()),
            p.getValorBruto(),
            p.getCompetencia(),
            p.getDescricaoComplementar(),
            p.getStatus().name(),
            p.getCreatedAt()
        );
    }
}
