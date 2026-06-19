package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.StatusProducao;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.dto.PreviewCalculoRequest;
import br.com.pinsaude.faturamento.dto.PreviewCalculoResponse;
import br.com.pinsaude.faturamento.dto.ProducaoRequest;
import br.com.pinsaude.faturamento.dto.ProducaoResponse;
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
import java.util.List;
import java.util.UUID;

@Service
public class ProducaoService {

    private static final BigDecimal TAXA_PIN_PCT = new BigDecimal("0.15");

    private final ProducaoRepository producaoRepo;
    private final TomadorRepository tomadorRepo;
    private final ServicoRepository servicoRepo;

    public ProducaoService(ProducaoRepository producaoRepo,
                           TomadorRepository tomadorRepo,
                           ServicoRepository servicoRepo) {
        this.producaoRepo = producaoRepo;
        this.tomadorRepo  = tomadorRepo;
        this.servicoRepo  = servicoRepo;
    }

    @Transactional(readOnly = true)
    public List<ProducaoResponse> listar(String status, String competencia, UUID medicoId) {
        if (status != null && !status.isBlank()) {
            StatusProducao s = parseStatus(status);
            return producaoRepo.findByStatusOrderByCreatedAtDesc(s).stream()
                .map(ProducaoResponse::from).toList();
        }
        if (competencia != null && !competencia.isBlank()) {
            return producaoRepo.findByCompetenciaOrderByCreatedAtDesc(competencia).stream()
                .map(ProducaoResponse::from).toList();
        }
        if (medicoId != null) {
            return producaoRepo.findByMedicoIdOrderByCreatedAtDesc(medicoId).stream()
                .map(ProducaoResponse::from).toList();
        }
        return producaoRepo.findAllByOrderByCreatedAtDesc().stream()
            .map(ProducaoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProducaoResponse buscarPorId(UUID id) {
        return ProducaoResponse.from(findOrThrow(id));
    }

    @Transactional
    public ProducaoResponse criar(ProducaoRequest req) {
        if (req.valorBruto() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor bruto deve ser maior que zero");
        }

        Tomador tomador = tomadorRepo.findById(req.tomadorId())
            .orElseThrow(() -> new EntityNotFoundException("Tomador não encontrado: " + req.tomadorId()));
        Servico servico = servicoRepo.findById(req.servicoId())
            .orElseThrow(() -> new EntityNotFoundException("Serviço não encontrado: " + req.servicoId()));

        String tenant = SecurityUtils.currentCnpjTenant();

        Producao p = new Producao();
        p.setCnpjIdTenant(tenant != null ? tenant : "");
        p.setMedicoId(req.medicoId());
        p.setTomador(tomador);
        p.setServico(servico);
        p.setValorBruto(req.valorBruto());
        p.setCompetencia(req.competencia());
        p.setDescricaoComplementar(req.descricaoComplementar());
        p.setStatus(StatusProducao.CONFIRMADA);

        return ProducaoResponse.from(producaoRepo.save(p));
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

        return calcular(req.valorBruto(), servico, tomador);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private PreviewCalculoResponse calcular(long valorBruto, Servico servico, Tomador tomador) {
        BigDecimal bruto = BigDecimal.valueOf(valorBruto);

        long taxaPin = pct(bruto, TAXA_PIN_PCT);

        long issRetido   = tomador.isIndicadorRetencaoIss()
            ? pct(bruto, pct(servico.getAliquotaIss()))   : 0L;
        long irRetido    = tomador.isIndicadorRetencaoFederal()
            ? pct(bruto, pct(servico.getAliquotaIr()))    : 0L;
        long csllRetido  = tomador.isIndicadorRetencaoFederal()
            ? pct(bruto, pct(servico.getAliquotaCsll()))  : 0L;
        long pisRetido   = tomador.isIndicadorRetencaoFederal()
            ? pct(bruto, pct(servico.getAliquotaPis()))   : 0L;
        long cofinsRetido = tomador.isIndicadorRetencaoFederal()
            ? pct(bruto, pct(servico.getAliquotaCofins())) : 0L;

        long totalRetencoes = issRetido + irRetido + csllRetido + pisRetido + cofinsRetido;
        long valorLiquido   = valorBruto - taxaPin - totalRetencoes;

        return new PreviewCalculoResponse(
            valorBruto, taxaPin,
            issRetido, irRetido, csllRetido, pisRetido, cofinsRetido,
            totalRetencoes, valorLiquido
        );
    }

    /** Converte alíquota percentual (ex: 5.0000) para decimal (0.05) */
    private BigDecimal pct(BigDecimal aliquota) {
        return aliquota.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    }

    /** Aplica fator decimal sobre valor em centavos e retorna centavos (arredondado) */
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
