package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.RegraApuracao;
import br.com.pinsaude.fiscal.domain.RegraApuracaoTributo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** APU-00 — regra de apuração. {@code cnpjId} nulo = padrão global. */
public record RegraApuracaoResponse(
    UUID id,
    String cnpjId,
    boolean global,
    String competenciaInicio,
    BigDecimal presuncaoIrpjEquiparado,
    BigDecimal presuncaoCsllEquiparado,
    BigDecimal presuncaoIrpjPadrao,
    BigDecimal presuncaoCsllPadrao,
    BigDecimal aliqAdicionalIrpj,
    Long limiteAdicionalIrpjMensal,
    Long tetoReceitaAnual,
    BigDecimal alertaTetoPercentual,
    Long valorMinimoGuia,
    String referenciaCompetencia,
    boolean homologado,
    String homologadoPor,
    OffsetDateTime homologadoEm,
    String fonte,
    String observacoes,
    String atualizadoPor,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<Tributo> tributos
) {
    public record Tributo(
        String tributo, boolean ativo, String aplicaEm, BigDecimal aliquota, String baseCalculo,
        String regimeReconhecimento, String periodicidade, boolean deduzRetencao, String codigoReceita,
        String guia, Integer vencimentoDia, Integer vencimentoMesesApos, String vencimentoAjuste
    ) {
        static Tributo from(RegraApuracaoTributo t) {
            return new Tributo(t.getTributo().name(), t.isAtivo(), t.getAplicaEm().name(), t.getAliquota(),
                t.getBaseCalculo().name(), t.getRegimeReconhecimento().name(), t.getPeriodicidade().name(),
                t.isDeduzRetencao(), t.getCodigoReceita(), t.getGuia(),
                t.getVencimentoDia() == null ? null : t.getVencimentoDia().intValue(),
                t.getVencimentoMesesApos() == null ? null : t.getVencimentoMesesApos().intValue(),
                t.getVencimentoAjuste() == null ? null : t.getVencimentoAjuste().name());
        }
    }

    public static RegraApuracaoResponse from(RegraApuracao r) {
        return new RegraApuracaoResponse(r.getId(), r.getCnpjId(), r.isGlobal(), r.getCompetenciaInicio(),
            r.getPresuncaoIrpjEquiparado(), r.getPresuncaoCsllEquiparado(),
            r.getPresuncaoIrpjPadrao(), r.getPresuncaoCsllPadrao(),
            r.getAliqAdicionalIrpj(), r.getLimiteAdicionalIrpjMensal(),
            r.getTetoReceitaAnual(), r.getAlertaTetoPercentual(), r.getValorMinimoGuia(),
            r.getReferenciaCompetencia().name(),
            r.isHomologado(), r.getHomologadoPor(), r.getHomologadoEm(), r.getFonte(), r.getObservacoes(),
            r.getAtualizadoPor(), r.getCreatedAt(), r.getUpdatedAt(),
            r.getTributos().stream().map(Tributo::from).toList());
    }
}
