package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.ParticipacaoProducao;
import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.Tomador;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProducaoResponse(
    UUID id,
    UUID medicoId,                         // null para multi-médico; 1º participante para single (backward compat)
    UUID empresaId,
    List<ParticipacaoResponse> participantes,
    TomadorResumo tomador,
    ServicoResumo servico,
    long valorBruto,
    String competencia,
    String descricaoComplementar,
    String cnaeCodigo,
    String status,
    OffsetDateTime createdAt
) {
    public record TomadorResumo(UUID id, String razaoSocialNome, String nomeFantasia,
                                String municipio, boolean retencaoFederal, boolean retencaoIss) {
        static TomadorResumo from(Tomador t) {
            return new TomadorResumo(t.getId(), t.getRazaoSocialNome(), t.getNomeFantasia(),
                t.getMunicipio(), t.isIndicadorRetencaoFederal(), t.isIndicadorRetencaoIss());
        }
    }

    public record ServicoResumo(UUID id, String codigoLc116, String descricaoPadrao,
                                BigDecimal aliquotaIss, BigDecimal aliquotaIr,
                                BigDecimal aliquotaCsll, BigDecimal aliquotaPis,
                                BigDecimal aliquotaCofins) {
        static ServicoResumo from(Servico s) {
            return new ServicoResumo(s.getId(), s.getCodigoLc116(), s.getDescricaoPadrao(),
                s.getAliquotaIss(), s.getAliquotaIr(), s.getAliquotaCsll(),
                s.getAliquotaPis(), s.getAliquotaCofins());
        }
    }

    public static ProducaoResponse from(Producao p, List<ParticipacaoProducao> participacoes) {
        List<ParticipacaoResponse> parts = participacoes.stream()
            .map(ParticipacaoResponse::from).toList();
        UUID medicoIdCompat = parts.size() == 1 ? parts.get(0).medicoId() : null;
        return new ProducaoResponse(
            p.getId(),
            medicoIdCompat,
            p.getEmpresaId(),
            parts,
            TomadorResumo.from(p.getTomador()),
            ServicoResumo.from(p.getServico()),
            p.getValorBruto(),
            p.getCompetencia(),
            p.getDescricaoComplementar(),
            p.getCnaeCodigo(),
            p.getStatus().name(),
            p.getCreatedAt()
        );
    }
}
