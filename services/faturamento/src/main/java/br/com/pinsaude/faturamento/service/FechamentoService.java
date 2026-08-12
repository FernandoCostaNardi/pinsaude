package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.*;
import br.com.pinsaude.faturamento.dto.FechamentoPreviewResponse;
import br.com.pinsaude.faturamento.dto.FechamentoRequest;
import br.com.pinsaude.faturamento.dto.FechamentoResponse;
import br.com.pinsaude.faturamento.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FechamentoService {

    private static final BigDecimal TAXA_PIN_DEFAULT = new BigDecimal("0.1500");

    private static final String[] MESES = {
        "JANEIRO", "FEVEREIRO", "MARÇO", "ABRIL", "MAIO", "JUNHO",
        "JULHO", "AGOSTO", "SETEMBRO", "OUTUBRO", "NOVEMBRO", "DEZEMBRO"
    };

    private final FechamentoRepository fechamentoRepo;
    private final FrequenciaMedicaRepository frequenciaRepo;
    private final FrequenciaItemRepository itemRepo;
    private final TomadorServicoOperacionalRepository setorRepo;
    private final TomadorGrupoFaturamentoRepository grupoRepo;
    private final TomadorModalidadeRepository modalidadeRepo;
    private final TomadorRepository tomadorRepo;
    private final ServicoRepository servicoRepo;
    private final ProducaoRepository producaoRepo;
    private final ParticipacaoRepository participacaoRepo;

    public FechamentoService(FechamentoRepository fechamentoRepo,
                             FrequenciaMedicaRepository frequenciaRepo,
                             FrequenciaItemRepository itemRepo,
                             TomadorServicoOperacionalRepository setorRepo,
                             TomadorGrupoFaturamentoRepository grupoRepo,
                             TomadorModalidadeRepository modalidadeRepo,
                             TomadorRepository tomadorRepo,
                             ServicoRepository servicoRepo,
                             ProducaoRepository producaoRepo,
                             ParticipacaoRepository participacaoRepo) {
        this.fechamentoRepo  = fechamentoRepo;
        this.frequenciaRepo  = frequenciaRepo;
        this.itemRepo        = itemRepo;
        this.setorRepo       = setorRepo;
        this.grupoRepo       = grupoRepo;
        this.modalidadeRepo  = modalidadeRepo;
        this.tomadorRepo     = tomadorRepo;
        this.servicoRepo     = servicoRepo;
        this.producaoRepo    = producaoRepo;
        this.participacaoRepo = participacaoRepo;
    }

    // ── Preview (somente leitura, sem persistir) ─────────────────────────────

    @Transactional(readOnly = true)
    public FechamentoPreviewResponse preview(UUID tomadorId, String competencia) {
        AggregationResult agg = computeAggregation(tomadorId, competencia);

        // Tabela global de modalidades
        List<FechamentoPreviewResponse.ModalidadeDetalhe> modalidades = new ArrayList<>();
        for (Map.Entry<UUID, long[]> entry : agg.modalidadeAgg().entrySet()) {
            UUID modalidadeId = entry.getKey();
            long[] countTotal = entry.getValue();
            TomadorModalidade m = agg.modalidadesMap().get(modalidadeId);
            if (m == null) continue;
            long valorItem = m.getValorCentavos() + m.getDeslocamentoCentavos();
            boolean fds = m.getNome() != null && m.getNome().toUpperCase().contains("FDS");
            modalidades.add(new FechamentoPreviewResponse.ModalidadeDetalhe(
                modalidadeId,
                m.getNome(),
                m.getTurno(),
                m.getHoras(),
                m.getValorCentavos(),
                m.getDeslocamentoCentavos(),
                valorItem,
                (int) countTotal[0],
                countTotal[1],
                fds
            ));
        }

        long totalGeral = 0;
        List<FechamentoPreviewResponse.GrupoPreview> grupos = new ArrayList<>();

        for (Map.Entry<UUID, Map<UUID, Long>> entry : agg.agrupado().entrySet()) {
            UUID grupoId = entry.getKey();
            Map<UUID, Long> medicoValores = entry.getValue();
            TomadorGrupoFaturamento grupo = agg.gruposMap().get(grupoId);
            if (grupo == null) continue;

            long totalGrupo = medicoValores.values().stream().mapToLong(Long::longValue).sum();

            // Setor breakdown dentro do grupo
            Map<UUID, Long> setorTotais = agg.grupoSetorTotal().getOrDefault(grupoId, Map.of());
            List<FechamentoPreviewResponse.SetorDetalhe> setores = setorTotais.entrySet().stream()
                .map(se -> {
                    TomadorServicoOperacional setor = agg.setoresMap().get(se.getKey());
                    String setorNome = setor != null ? setor.getNome() : se.getKey().toString().substring(0, 8);
                    return new FechamentoPreviewResponse.SetorDetalhe(se.getKey(), setorNome, se.getValue());
                })
                .sorted(Comparator.comparing(FechamentoPreviewResponse.SetorDetalhe::setorNome))
                .toList();

            List<FechamentoPreviewResponse.MedicoParticipacao> medicos = medicoValores.entrySet().stream()
                .map(me -> new FechamentoPreviewResponse.MedicoParticipacao(me.getKey(), me.getValue()))
                .toList();

            grupos.add(new FechamentoPreviewResponse.GrupoPreview(
                grupoId,
                grupo.getNome(),
                grupo.getDescricaoNota(),
                interpolarDescricao(grupo.getDescricaoNota(), competencia),
                grupo.getServicoLc116Id(),
                setores,
                medicos,
                totalGrupo
            ));
            totalGeral += totalGrupo;
        }

        return new FechamentoPreviewResponse(
            tomadorId,
            competencia,
            modalidades,
            grupos,
            totalGeral,
            agg.frequencias().size()
        );
    }

    // ── Executar fechamento ───────────────────────────────────────────────────

    @Transactional
    public FechamentoResponse executar(FechamentoRequest req) {
        fechamentoRepo.findByTomadorIdAndCompetencia(req.tomadorId(), req.competencia())
            .filter(f -> "FECHADO".equals(f.getStatus()))
            .ifPresent(f -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A competência " + req.competencia() + " já foi fechada para este tomador.");
            });

        Tomador tomador = tomadorRepo.findById(req.tomadorId())
            .orElseThrow(() -> new EntityNotFoundException("Tomador não encontrado: " + req.tomadorId()));

        AggregationResult agg = computeAggregation(req.tomadorId(), req.competencia());
        if (agg.frequencias().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Nenhuma frequência encontrada para fechar na competência " + req.competencia());
        }

        String tenant = SecurityUtils.currentCnpjTenant();
        if (tenant == null) tenant = "";
        String finalTenant = tenant;

        Fechamento fechamento = fechamentoRepo
            .findByTomadorIdAndCompetencia(req.tomadorId(), req.competencia())
            .orElseGet(Fechamento::new);
        fechamento.setCnpjIdTenant(finalTenant);
        fechamento.setTomadorId(req.tomadorId());
        fechamento.setCompetencia(req.competencia());
        fechamento.setStatus("ABERTO");
        fechamento.setTotalCentavos(0);
        fechamentoRepo.save(fechamento);

        List<FechamentoResponse.ProducaoRef> refs = new ArrayList<>();
        long totalGeral = 0;

        for (Map.Entry<UUID, Map<UUID, Long>> entry : agg.agrupado().entrySet()) {
            UUID grupoId = entry.getKey();
            Map<UUID, Long> medicoValores = entry.getValue();
            TomadorGrupoFaturamento grupo = agg.gruposMap().get(grupoId);
            if (grupo == null) continue;

            long totalGrupo = medicoValores.values().stream().mapToLong(Long::longValue).sum();
            if (totalGrupo == 0) continue;

            Servico servico = servicoRepo.findById(grupo.getServicoLc116Id())
                .orElseThrow(() -> new EntityNotFoundException(
                    "Serviço LC116 não encontrado: " + grupo.getServicoLc116Id()));

            Producao p = new Producao();
            p.setCnpjIdTenant(finalTenant);
            p.setTomador(tomador);
            p.setServico(servico);
            p.setValorBruto(totalGrupo);
            p.setCompetencia(req.competencia());
            p.setDescricaoComplementar(interpolarDescricao(grupo.getDescricaoNota(), req.competencia()));
            p.setStatus(StatusProducao.CONFIRMADA);
            producaoRepo.save(p);

            List<ParticipacaoProducao> participacoes = medicoValores.entrySet().stream()
                .filter(me -> me.getValue() > 0)
                .map(me -> {
                    var pp = new ParticipacaoProducao();
                    pp.setProducaoId(p.getId());
                    pp.setMedicoId(me.getKey());
                    pp.setValorBruto(me.getValue());
                    pp.setTaxaPinPct(TAXA_PIN_DEFAULT);
                    return pp;
                }).toList();
            participacaoRepo.saveAll(participacoes);

            refs.add(new FechamentoResponse.ProducaoRef(grupoId, grupo.getNome(), p.getId(), totalGrupo));
            totalGeral += totalGrupo;

            UUID producaoId = p.getId();
            UUID fechamentoId = fechamento.getId();
            for (FrequenciaMedica freq : agg.frequencias()) {
                TomadorServicoOperacional setor = agg.setoresMap().get(freq.getServicoOperacionalId());
                if (setor != null && grupoId.equals(setor.getGrupoId())) {
                    freq.setStatus("FATURADA");
                    freq.setFechamentoId(fechamentoId);
                    freq.setProducaoId(producaoId);
                }
            }
        }

        frequenciaRepo.saveAll(agg.frequencias());

        fechamento.setStatus("FECHADO");
        fechamento.setTotalCentavos(totalGeral);
        fechamento.setFechadoEm(OffsetDateTime.now());
        fechamentoRepo.save(fechamento);

        return FechamentoResponse.from(fechamento, refs);
    }

    // ── Listagem ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FechamentoResponse> listar(UUID tomadorId) {
        List<Fechamento> lista = tomadorId != null
            ? fechamentoRepo.findByTomadorIdOrderByCompetenciaDesc(tomadorId)
            : fechamentoRepo.findAll();
        return lista.stream()
            .map(f -> FechamentoResponse.from(f, List.of()))
            .toList();
    }

    @Transactional(readOnly = true)
    public FechamentoResponse buscarPorId(UUID id) {
        Fechamento f = fechamentoRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Fechamento não encontrado: " + id));
        return FechamentoResponse.from(f, List.of());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private record AggregationResult(
        List<FrequenciaMedica> frequencias,
        Map<UUID, TomadorServicoOperacional> setoresMap,
        Map<UUID, TomadorGrupoFaturamento> gruposMap,
        Map<UUID, TomadorModalidade> modalidadesMap,
        Map<UUID, long[]> modalidadeAgg,          // modalidadeId → [count, total]
        Map<UUID, Map<UUID, Long>> grupoSetorTotal, // grupoId → setorId → total
        Map<UUID, Map<UUID, Long>> agrupado        // grupoId → medicoId → total
    ) {}

    private AggregationResult computeAggregation(UUID tomadorId, String competencia) {
        List<FrequenciaMedica> frequencias = frequenciaRepo
            .findByTomadorIdAndCompetencia(tomadorId, competencia)
            .stream()
            .filter(f -> !"FATURADA".equals(f.getStatus()))
            .toList();

        if (frequencias.isEmpty()) {
            return new AggregationResult(List.of(), Map.of(), Map.of(), Map.of(),
                new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        List<UUID> freqIds = frequencias.stream().map(FrequenciaMedica::getId).toList();
        List<FrequenciaItem> todosItens = itemRepo.findByFrequenciaIdIn(freqIds);

        // freqId → FrequenciaMedica (para lookup rápido)
        Map<UUID, FrequenciaMedica> freqMap = frequencias.stream()
            .collect(Collectors.toMap(FrequenciaMedica::getId, Function.identity()));

        // Batch load setores
        Set<UUID> setorIds = frequencias.stream()
            .map(FrequenciaMedica::getServicoOperacionalId)
            .collect(Collectors.toSet());
        Map<UUID, TomadorServicoOperacional> setoresMap = setorRepo.findAllById(setorIds).stream()
            .collect(Collectors.toMap(TomadorServicoOperacional::getId, Function.identity()));

        // Batch load modalidades
        Set<UUID> modalidadeIds = todosItens.stream()
            .map(FrequenciaItem::getModalidadeId)
            .collect(Collectors.toSet());
        Map<UUID, TomadorModalidade> modalidadesMap = modalidadeRepo.findAllById(modalidadeIds).stream()
            .collect(Collectors.toMap(TomadorModalidade::getId, Function.identity()));

        // Accumulators — single pass sobre todos os itens
        Map<UUID, long[]> modalidadeAgg    = new LinkedHashMap<>();
        Map<UUID, Map<UUID, Long>> grupoSetorTotal = new LinkedHashMap<>();
        Map<UUID, Map<UUID, Long>> agrupado = new LinkedHashMap<>();

        for (FrequenciaItem item : todosItens) {
            FrequenciaMedica freq = freqMap.get(item.getFrequenciaId());
            if (freq == null) continue;

            TomadorServicoOperacional setor = setoresMap.get(freq.getServicoOperacionalId());
            if (setor == null) continue;
            UUID grupoId = setor.getGrupoId();

            long ocorrenciaValor = item.getOcorrenciaValorCentavos() != null ? item.getOcorrenciaValorCentavos() : 0L;
            long itemTotal = item.getValorUnitarioCentavos() + item.getDeslocamentoCentavos() + ocorrenciaValor;

            // Global modalidade aggregation
            modalidadeAgg.computeIfAbsent(item.getModalidadeId(), k -> new long[]{0L, 0L});
            long[] ct = modalidadeAgg.get(item.getModalidadeId());
            ct[0]++;
            ct[1] += itemTotal;

            // Setor breakdown per grupo
            grupoSetorTotal.computeIfAbsent(grupoId, k -> new LinkedHashMap<>())
                .merge(freq.getServicoOperacionalId(), itemTotal, Long::sum);

            // Medico total per grupo
            agrupado.computeIfAbsent(grupoId, k -> new LinkedHashMap<>())
                .merge(freq.getMedicoId(), itemTotal, Long::sum);
        }

        // PINSAUDE-13.23: Diarista não paga por item (cada item vale R$0 — ver
        // FrequenciaService.calcularValorItem), então o loop acima nunca soma o valor mensal
        // fixo dele. Soma-se aqui, UMA ÚNICA VEZ por frequência/modalidade Diarista distinta,
        // independente de quantos itens/dias foram lançados no mês. O count (ct[0]) de cada
        // modalidade já foi corretamente incrementado por item no loop acima (reflete dias
        // trabalhados) — aqui só o TOTAL em dinheiro é ajustado.
        Map<UUID, Set<UUID>> modalidadesDiaristaPorFrequencia = todosItens.stream()
            .filter(item -> {
                TomadorModalidade m = modalidadesMap.get(item.getModalidadeId());
                return m != null && "DIARISTA".equals(m.getTipo());
            })
            .collect(Collectors.groupingBy(FrequenciaItem::getFrequenciaId,
                Collectors.mapping(FrequenciaItem::getModalidadeId, Collectors.toSet())));

        for (Map.Entry<UUID, Set<UUID>> entry : modalidadesDiaristaPorFrequencia.entrySet()) {
            FrequenciaMedica freq = freqMap.get(entry.getKey());
            if (freq == null) continue;
            TomadorServicoOperacional setor = setoresMap.get(freq.getServicoOperacionalId());
            if (setor == null) continue;
            UUID grupoId = setor.getGrupoId();

            for (UUID modalidadeId : entry.getValue()) {
                TomadorModalidade modalidade = modalidadesMap.get(modalidadeId);
                if (modalidade == null) continue;
                long valorMensal = modalidade.getValorCentavos();

                modalidadeAgg.computeIfAbsent(modalidadeId, k -> new long[]{0L, 0L});
                modalidadeAgg.get(modalidadeId)[1] += valorMensal;

                grupoSetorTotal.computeIfAbsent(grupoId, k -> new LinkedHashMap<>())
                    .merge(freq.getServicoOperacionalId(), valorMensal, Long::sum);

                agrupado.computeIfAbsent(grupoId, k -> new LinkedHashMap<>())
                    .merge(freq.getMedicoId(), valorMensal, Long::sum);
            }
        }

        Set<UUID> grupoIds = agrupado.keySet();
        Map<UUID, TomadorGrupoFaturamento> gruposMap = grupoRepo.findAllById(grupoIds).stream()
            .collect(Collectors.toMap(TomadorGrupoFaturamento::getId, Function.identity()));

        return new AggregationResult(
            frequencias, setoresMap, gruposMap, modalidadesMap,
            modalidadeAgg, grupoSetorTotal, agrupado
        );
    }

    public static String interpolarDescricao(String template, String competencia) {
        if (template == null) return "";
        String[] parts = competencia.split("-");
        int ano = Integer.parseInt(parts[0]);
        int mes = Integer.parseInt(parts[1]);
        String mesNome = MESES[mes - 1];
        return template.replace("{competencia}", mesNome + " DE " + ano);
    }
}
