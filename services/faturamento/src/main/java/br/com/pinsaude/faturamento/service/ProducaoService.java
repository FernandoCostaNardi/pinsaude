package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.ParticipacaoProducao;
import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.StatusProducao;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.dto.PreviewCalculoRequest;
import br.com.pinsaude.faturamento.dto.PreviewCalculoResponse;
import br.com.pinsaude.faturamento.dto.ProducaoRequest;
import br.com.pinsaude.faturamento.dto.ProducaoResponse;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.ParticipacaoRepository;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProducaoService {

    private static final BigDecimal TAXA_PIN_DEFAULT = new BigDecimal("0.1500");

    private final ProducaoRepository      producaoRepo;
    private final ParticipacaoRepository  participacaoRepo;
    private final TomadorRepository       tomadorRepo;
    private final ServicoRepository       servicoRepo;
    private final MedicoTomadorRepository medicoTomadorRepo;

    public ProducaoService(ProducaoRepository producaoRepo,
                           ParticipacaoRepository participacaoRepo,
                           TomadorRepository tomadorRepo,
                           ServicoRepository servicoRepo,
                           MedicoTomadorRepository medicoTomadorRepo) {
        this.producaoRepo     = producaoRepo;
        this.participacaoRepo = participacaoRepo;
        this.tomadorRepo      = tomadorRepo;
        this.servicoRepo      = servicoRepo;
        this.medicoTomadorRepo = medicoTomadorRepo;
    }

    @Transactional(readOnly = true)
    public List<ProducaoResponse> listar(String status, String competencia, UUID medicoId,
                                         UUID tomadorId, String periodoInicio, String periodoFim) {
        StatusProducao statusEnum = (status != null && !status.isBlank()) ? parseStatus(status) : null;

        // IDs de produções em que o médico filtrado participa
        Set<UUID> producaoIdsDoMedico = medicoId != null
            ? new HashSet<>(participacaoRepo.findProducaoIdsByMedicoId(medicoId))
            : null;

        List<Producao> all = producaoRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(p -> statusEnum == null || p.getStatus() == statusEnum)
            .filter(p -> competencia == null || competencia.isBlank() || competencia.equals(p.getCompetencia()))
            .filter(p -> producaoIdsDoMedico == null || producaoIdsDoMedico.contains(p.getId()))
            .filter(p -> tomadorId == null || tomadorId.equals(p.getTomador().getId()))
            .filter(p -> periodoInicio == null || periodoInicio.isBlank() || p.getCompetencia().compareTo(periodoInicio) >= 0)
            .filter(p -> periodoFim == null || periodoFim.isBlank() || p.getCompetencia().compareTo(periodoFim) <= 0)
            .toList();

        if (all.isEmpty()) return List.of();

        List<UUID> ids = all.stream().map(Producao::getId).toList();
        Map<UUID, List<ParticipacaoProducao>> byProducao = participacaoRepo.findByProducaoIdIn(ids).stream()
            .collect(Collectors.groupingBy(ParticipacaoProducao::getProducaoId));

        return all.stream()
            .map(p -> ProducaoResponse.from(p, byProducao.getOrDefault(p.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public ProducaoResponse buscarPorId(UUID id) {
        Producao p = findOrThrow(id);
        return ProducaoResponse.from(p, participacaoRepo.findByProducaoId(id));
    }

    @Transactional
    public ProducaoResponse criar(ProducaoRequest req) {
        long valorTotal = req.participantes().stream()
            .mapToLong(part -> part.valorBruto()).sum();
        if (valorTotal <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor total deve ser maior que zero");
        }

        Tomador tomador = tomadorRepo.findById(req.tomadorId())
            .orElseThrow(() -> new EntityNotFoundException("Tomador não encontrado: " + req.tomadorId()));
        Servico servico = servicoRepo.findById(req.servicoId())
            .orElseThrow(() -> new EntityNotFoundException("Serviço não encontrado: " + req.servicoId()));

        for (var participante : req.participantes()) {
            if (!medicoTomadorRepo.existsByTomadorIdAndMedicoId(req.tomadorId(), participante.medicoId())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Médico não está alocado a este tomador: " + participante.medicoId());
            }
        }

        String tenant = SecurityUtils.currentCnpjTenant();

        Producao p = new Producao();
        p.setCnpjIdTenant(tenant != null ? tenant : "");
        p.setEmpresaId(req.empresaId());
        p.setTomador(tomador);
        p.setServico(servico);
        p.setValorBruto(valorTotal);
        p.setCompetencia(req.competencia());
        p.setDescricaoComplementar(req.descricaoComplementar());
        p.setCnaeCodigo(req.cnaeCodigo());
        p.setStatus(StatusProducao.CONFIRMADA);

        producaoRepo.save(p);

        List<ParticipacaoProducao> participacoes = req.participantes().stream().map(part -> {
            var pp = new ParticipacaoProducao();
            pp.setProducaoId(p.getId());
            pp.setMedicoId(part.medicoId());
            pp.setValorBruto(part.valorBruto());
            pp.setTaxaPinPct(part.taxaPinPct() != null ? part.taxaPinPct() : TAXA_PIN_DEFAULT);
            return pp;
        }).toList();
        participacaoRepo.saveAll(participacoes);

        return ProducaoResponse.from(p, participacoes);
    }

    @Transactional(readOnly = true)
    public PreviewCalculoResponse calcularPreview(PreviewCalculoRequest req) {
        if (req.valorBruto() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor bruto deve ser maior que zero");
        }

        Tomador tomador = tomadorRepo.findById(req.tomadorId())
            .orElseThrow(() -> new EntityNotFoundException("Tomador não encontrado: " + req.tomadorId()));
        Servico servico = servicoRepo.findById(req.servicoId())
            .orElseThrow(() -> new EntityNotFoundException("Serviço não encontrado: " + req.servicoId()));

        BigDecimal taxaPinPct = req.taxaPinPct() != null ? req.taxaPinPct() : TAXA_PIN_DEFAULT;
        return calcular(req.valorBruto(), servico, tomador, taxaPinPct);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private PreviewCalculoResponse calcular(long valorBruto, Servico servico, Tomador tomador,
                                            BigDecimal taxaPinPct) {
        BigDecimal bruto = BigDecimal.valueOf(valorBruto);

        long taxaPin = pct(bruto, taxaPinPct);

        long issRetido    = tomador.isIndicadorRetencaoIss()
            ? pct(bruto, pct(servico.getAliquotaIss()))    : 0L;
        long irRetido     = tomador.isIndicadorRetencaoFederal()
            ? pct(bruto, pct(servico.getAliquotaIr()))     : 0L;
        long csllRetido   = tomador.isIndicadorRetencaoFederal()
            ? pct(bruto, pct(servico.getAliquotaCsll()))   : 0L;
        long pisRetido    = tomador.isIndicadorRetencaoFederal()
            ? pct(bruto, pct(servico.getAliquotaPis()))    : 0L;
        long cofinsRetido = tomador.isIndicadorRetencaoFederal()
            ? pct(bruto, pct(servico.getAliquotaCofins())) : 0L;

        long totalRetencoes     = issRetido + irRetido + csllRetido + pisRetido + cofinsRetido;
        long valorLiquidoMedico = valorBruto - taxaPin;
        long resultadoPin       = taxaPin - totalRetencoes;

        return new PreviewCalculoResponse(
            valorBruto, taxaPin,
            issRetido, irRetido, csllRetido, pisRetido, cofinsRetido,
            totalRetencoes, valorLiquidoMedico, resultadoPin
        );
    }

    private BigDecimal pct(BigDecimal aliquota) {
        return aliquota.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    }

    private long pct(BigDecimal valorCentavos, BigDecimal fator) {
        return valorCentavos.multiply(fator).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private Producao findOrThrow(UUID id) {
        return producaoRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Produção não encontrada: " + id));
    }

    private StatusProducao parseStatus(String s) {
        try {
            return StatusProducao.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Status inválido. Use: RASCUNHO, CONFIRMADA, EMITIDA ou CANCELADA");
        }
    }
}
