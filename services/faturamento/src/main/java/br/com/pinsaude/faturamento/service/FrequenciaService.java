package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.FrequenciaItem;
import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import br.com.pinsaude.faturamento.dto.FrequenciaItemRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaItemResponse;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaResponse;
import br.com.pinsaude.faturamento.repository.FrequenciaItemRepository;
import br.com.pinsaude.faturamento.repository.FrequenciaMedicaRepository;
import br.com.pinsaude.faturamento.repository.TomadorModalidadeRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoOperacionalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FrequenciaService {

    private final FrequenciaMedicaRepository frequenciaRepo;
    private final FrequenciaItemRepository itemRepo;
    private final TomadorServicoOperacionalRepository setorRepo;
    private final TomadorModalidadeRepository modalidadeRepo;

    public FrequenciaService(FrequenciaMedicaRepository frequenciaRepo,
                             FrequenciaItemRepository itemRepo,
                             TomadorServicoOperacionalRepository setorRepo,
                             TomadorModalidadeRepository modalidadeRepo) {
        this.frequenciaRepo = frequenciaRepo;
        this.itemRepo       = itemRepo;
        this.setorRepo      = setorRepo;
        this.modalidadeRepo = modalidadeRepo;
    }

    // ── Frequência CRUD ───────────────────────────────────────────────────────

    @Transactional
    public FrequenciaMedicaResponse criar(FrequenciaMedicaRequest req) {
        if (frequenciaRepo.existsByMedicoIdAndServicoOperacionalIdAndCompetencia(
                req.medicoId(), req.servicoOperacionalId(), req.competencia())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe uma frequência para este médico neste setor na competência " + req.competencia());
        }

        TomadorServicoOperacional setor = setorRepo.findById(req.servicoOperacionalId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Setor operacional não encontrado: " + req.servicoOperacionalId()));

        String tenant = SecurityUtils.currentCnpjTenant();

        FrequenciaMedica f = new FrequenciaMedica();
        f.setCnpjIdTenant(tenant != null ? tenant : "");
        f.setTomadorId(req.tomadorId());
        f.setMedicoId(req.medicoId());
        f.setServicoOperacionalId(req.servicoOperacionalId());
        f.setCompetencia(req.competencia());
        f.setEspecialidade(req.especialidade());
        f.setStatus("RASCUNHO");
        frequenciaRepo.save(f);

        return FrequenciaMedicaResponse.from(f, setor, List.of());
    }

    @Transactional(readOnly = true)
    public List<FrequenciaMedicaResponse> listar(UUID medicoId, UUID tomadorId,
                                                  UUID setorId, String competencia,
                                                  String status) {
        List<FrequenciaMedica> all = medicoId != null
            ? frequenciaRepo.findByMedicoIdOrderByCompetenciaDescCreatedAtDesc(medicoId)
            : tomadorId != null
                ? frequenciaRepo.findByTomadorIdOrderByCompetenciaDescCreatedAtDesc(tomadorId)
                : setorId != null
                    ? frequenciaRepo.findByServicoOperacionalIdOrderByCompetenciaDescCreatedAtDesc(setorId)
                    : frequenciaRepo.findAllByOrderByCompetenciaDescCreatedAtDesc();

        List<FrequenciaMedica> filtered = all.stream()
            .filter(f -> competencia == null || competencia.isBlank() || competencia.equals(f.getCompetencia()))
            .filter(f -> status == null || status.isBlank() || status.equals(f.getStatus()))
            .filter(f -> medicoId == null || medicoId.equals(f.getMedicoId()))
            .filter(f -> tomadorId == null || tomadorId.equals(f.getTomadorId()))
            .filter(f -> setorId == null || setorId.equals(f.getServicoOperacionalId()))
            .toList();

        if (filtered.isEmpty()) return List.of();

        // Batch load setores e itens
        List<UUID> setorIds = filtered.stream().map(FrequenciaMedica::getServicoOperacionalId).distinct().toList();
        Map<UUID, TomadorServicoOperacional> setoresMap = setorRepo.findAllById(setorIds).stream()
            .collect(Collectors.toMap(TomadorServicoOperacional::getId, Function.identity()));

        List<UUID> freqIds = filtered.stream().map(FrequenciaMedica::getId).toList();
        Map<UUID, List<FrequenciaItem>> itensPorFrequencia = itemRepo.findAll().stream()
            .filter(i -> freqIds.contains(i.getFrequenciaId()))
            .collect(Collectors.groupingBy(FrequenciaItem::getFrequenciaId));

        List<UUID> modalidadeIds = itensPorFrequencia.values().stream()
            .flatMap(List::stream).map(FrequenciaItem::getModalidadeId).distinct().toList();
        Map<UUID, TomadorModalidade> modalidadesMap = modalidadeRepo.findAllById(modalidadeIds).stream()
            .collect(Collectors.toMap(TomadorModalidade::getId, Function.identity()));

        return filtered.stream().map(f -> {
            List<FrequenciaItemResponse> itemResponses = itensPorFrequencia
                .getOrDefault(f.getId(), List.of()).stream()
                .sorted((a, b) -> a.getDataExecucao().compareTo(b.getDataExecucao()))
                .map(i -> FrequenciaItemResponse.from(i, modalidadesMap.get(i.getModalidadeId())))
                .toList();
            return FrequenciaMedicaResponse.from(f, setoresMap.get(f.getServicoOperacionalId()), itemResponses);
        }).toList();
    }

    @Transactional(readOnly = true)
    public FrequenciaMedicaResponse buscarPorId(UUID id) {
        FrequenciaMedica f = findOrThrow(id);
        return toResponse(f);
    }

    // ── Itens CRUD ────────────────────────────────────────────────────────────

    @Transactional
    public FrequenciaItemResponse adicionarItem(UUID frequenciaId, FrequenciaItemRequest req) {
        FrequenciaMedica f = findOrThrow(frequenciaId);
        if ("FATURADA".equals(f.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é possível adicionar itens em frequência já faturada");
        }

        TomadorModalidade modalidade = modalidadeRepo.findById(req.modalidadeId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Modalidade não encontrada: " + req.modalidadeId()));

        FrequenciaItem item = new FrequenciaItem();
        item.setFrequenciaId(frequenciaId);
        item.setModalidadeId(req.modalidadeId());
        item.setDataExecucao(req.dataExecucao());
        item.setOcorrencia(req.ocorrencia());
        // Snapshot de preço no momento do lançamento
        item.setValorUnitarioCentavos(modalidade.getValorCentavos());
        item.setDeslocamentoCentavos(modalidade.getDeslocamentoCentavos());
        itemRepo.save(item);

        return FrequenciaItemResponse.from(item, modalidade);
    }

    @Transactional
    public FrequenciaItemResponse atualizarItem(UUID frequenciaId, UUID itemId, FrequenciaItemRequest req) {
        FrequenciaMedica f = findOrThrow(frequenciaId);
        if ("FATURADA".equals(f.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é possível editar itens em frequência já faturada");
        }

        FrequenciaItem item = itemRepo.findById(itemId)
            .filter(i -> frequenciaId.equals(i.getFrequenciaId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Item não encontrado: " + itemId));

        TomadorModalidade modalidade = modalidadeRepo.findById(req.modalidadeId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Modalidade não encontrada: " + req.modalidadeId()));

        item.setModalidadeId(req.modalidadeId());
        item.setDataExecucao(req.dataExecucao());
        item.setOcorrencia(req.ocorrencia());
        item.setValorUnitarioCentavos(modalidade.getValorCentavos());
        item.setDeslocamentoCentavos(modalidade.getDeslocamentoCentavos());
        itemRepo.save(item);

        return FrequenciaItemResponse.from(item, modalidade);
    }

    @Transactional
    public void removerItem(UUID frequenciaId, UUID itemId) {
        FrequenciaMedica f = findOrThrow(frequenciaId);
        if ("FATURADA".equals(f.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é possível remover itens de frequência já faturada");
        }

        FrequenciaItem item = itemRepo.findById(itemId)
            .filter(i -> frequenciaId.equals(i.getFrequenciaId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Item não encontrado: " + itemId));

        itemRepo.delete(item);
    }

    // ── Gerar PDF ─────────────────────────────────────────────────────────────

    @Transactional
    public FrequenciaMedicaResponse gerarPdf(UUID id) {
        FrequenciaMedica f = findOrThrow(id);

        // Estados permitidos para gerar PDF: RASCUNHO, PDF_GERADO e AGUARDANDO_ASSINATURA (idempotente)
        // Estados além de AGUARDANDO_ASSINATURA já representam etapas posteriores
        Set<String> permitidos = Set.of("RASCUNHO", "PDF_GERADO", "AGUARDANDO_ASSINATURA");
        if (!permitidos.contains(f.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é possível gerar PDF de frequência com status: " + f.getStatus()
                    + ". Apenas RASCUNHO e PDF_GERADO permitem transição para AGUARDANDO_ASSINATURA.");
        }

        if (!"AGUARDANDO_ASSINATURA".equals(f.getStatus())) {
            f.setStatus("AGUARDANDO_ASSINATURA");
            frequenciaRepo.save(f);
        }

        return toResponse(f);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FrequenciaMedica findOrThrow(UUID id) {
        return frequenciaRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Frequência não encontrada: " + id));
    }

    private FrequenciaMedicaResponse toResponse(FrequenciaMedica f) {
        TomadorServicoOperacional setor = setorRepo.findById(f.getServicoOperacionalId()).orElse(null);
        List<FrequenciaItem> itens = itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(f.getId());

        if (itens.isEmpty()) {
            return FrequenciaMedicaResponse.from(f, setor, List.of());
        }

        List<UUID> modalidadeIds = itens.stream().map(FrequenciaItem::getModalidadeId).distinct().toList();
        Map<UUID, TomadorModalidade> modalidadesMap = modalidadeRepo.findAllById(modalidadeIds).stream()
            .collect(Collectors.toMap(TomadorModalidade::getId, Function.identity()));

        List<FrequenciaItemResponse> itemResponses = itens.stream()
            .map(i -> FrequenciaItemResponse.from(i, modalidadesMap.get(i.getModalidadeId())))
            .toList();

        return FrequenciaMedicaResponse.from(f, setor, itemResponses);
    }
}
