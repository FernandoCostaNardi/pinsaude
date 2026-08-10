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
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.TomadorModalidadeRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoOperacionalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final StorageService storageService;
    private final MedicoTomadorRepository medicoTomadorRepo;

    public FrequenciaService(FrequenciaMedicaRepository frequenciaRepo,
                             FrequenciaItemRepository itemRepo,
                             TomadorServicoOperacionalRepository setorRepo,
                             TomadorModalidadeRepository modalidadeRepo,
                             StorageService storageService,
                             MedicoTomadorRepository medicoTomadorRepo) {
        this.frequenciaRepo = frequenciaRepo;
        this.itemRepo       = itemRepo;
        this.setorRepo      = setorRepo;
        this.modalidadeRepo = modalidadeRepo;
        this.storageService = storageService;
        this.medicoTomadorRepo = medicoTomadorRepo;
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

        if (!setor.getTomadorId().equals(req.tomadorId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Setor operacional não pertence ao tomador informado");
        }

        if (!medicoTomadorRepo.existsByTomadorIdAndMedicoId(req.tomadorId(), req.medicoId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Médico não está alocado a este tomador");
        }

        String tenant = SecurityUtils.currentCnpjTenant();

        FrequenciaMedica f = new FrequenciaMedica();
        f.setCnpjIdTenant(tenant != null ? tenant : "");
        f.setTomadorId(req.tomadorId());
        f.setMedicoId(req.medicoId());
        f.setServicoOperacionalId(req.servicoOperacionalId());
        f.setCompetencia(req.competencia());
        f.setTipoMedico(req.tipoMedico());
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
            return FrequenciaMedicaResponse.from(f, setoresMap.get(f.getServicoOperacionalId()), itemResponses, modalidadesMap);
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
        item.setHorasTrabalhadas(req.horasTrabalhadas());
        // Snapshot de preço no momento do lançamento — proporcional para modalidade META
        item.setValorUnitarioCentavos(calcularValorItem(modalidade, req.horasTrabalhadas()));
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
        item.setHorasTrabalhadas(req.horasTrabalhadas());
        item.setValorUnitarioCentavos(calcularValorItem(modalidade, req.horasTrabalhadas()));
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

    // ── Documento assinado ────────────────────────────────────────────────────

    @Transactional
    public FrequenciaMedicaResponse receberDocumentoAssinado(UUID id, MultipartFile arquivo) {
        FrequenciaMedica f = findOrThrow(id);

        // Permite upload inicial (AGUARDANDO_ASSINATURA) e substituição (ASSINADA_RECEBIDA, ENVIADA_TOMADOR)
        Set<String> permitidos = Set.of("AGUARDANDO_ASSINATURA", "ASSINADA_RECEBIDA", "ENVIADA_TOMADOR");
        if (!permitidos.contains(f.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Upload do documento assinado não é permitido no status atual: " + f.getStatus());
        }

        // Remover arquivo anterior se houver (re-upload)
        if (f.getDocumentoAssinadoKey() != null) {
            storageService.delete(f.getDocumentoAssinadoKey());
        }

        String objectKey = storageService.upload("frequencias/" + id, arquivo);
        f.setDocumentoAssinadoKey(objectKey);
        // Só transiciona status no upload inicial — substituição mantém o status atual
        if ("AGUARDANDO_ASSINATURA".equals(f.getStatus())) {
            f.setStatus("ASSINADA_RECEBIDA");
        }
        frequenciaRepo.save(f);

        return toResponse(f);
    }

    @Transactional(readOnly = true)
    public String getDocumentoUrl(UUID id) {
        FrequenciaMedica f = findOrThrow(id);
        if (f.getDocumentoAssinadoKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Esta frequência não possui documento assinado");
        }
        return storageService.getPresignedUrl(f.getDocumentoAssinadoKey());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // PLANTAO/MENSAL: valor flat da modalidade (comportamento inalterado desde antes do EPIC-13.19).
    // META/HORA: proporcional às horas lançadas — round(horasTrabalhadas × (valorCentavos ÷ metaHoras)).
    // META/DIA: cada item lançado equivale a 1 dia — round(valorCentavos ÷ metaDias).
    // A soma dos itens continua correta automaticamente (Fechamento só soma o snapshot já calculado
    // aqui) porque a valoração proporcional é linear — não há necessidade de redesenhar a agregação.
    private long calcularValorItem(TomadorModalidade modalidade, BigDecimal horasTrabalhadas) {
        if (!"META".equals(modalidade.getTipo())) {
            return modalidade.getValorCentavos();
        }
        String unidade = modalidade.getUnidadeCalculo();
        if (unidade == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Modalidade do tipo Meta está sem unidade de cálculo configurada");
        }
        if ("HORA".equals(unidade)) {
            if (horasTrabalhadas == null || horasTrabalhadas.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Informe as horas trabalhadas para lançar um item desta modalidade (meta por hora)");
            }
            BigDecimal metaHoras = modalidade.getMetaHoras();
            if (metaHoras == null || metaHoras.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Modalidade do tipo Meta por hora está sem meta de horas configurada");
            }
            BigDecimal valorPorHora = BigDecimal.valueOf(modalidade.getValorCentavos())
                .divide(metaHoras, 8, RoundingMode.HALF_UP);
            return horasTrabalhadas.multiply(valorPorHora).setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        // unidade == DIA — cada item lançado equivale a 1 dia dentro do bloco da meta
        Integer metaDias = modalidade.getMetaDias();
        if (metaDias == null || metaDias <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Modalidade do tipo Meta por dia está sem meta de dias configurada");
        }
        return BigDecimal.valueOf(modalidade.getValorCentavos())
            .divide(BigDecimal.valueOf(metaDias), 0, RoundingMode.HALF_UP)
            .longValueExact();
    }

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

        return FrequenciaMedicaResponse.from(f, setor, itemResponses, modalidadesMap);
    }
}
