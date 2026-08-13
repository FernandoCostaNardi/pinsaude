package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.FrequenciaItem;
import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import br.com.pinsaude.faturamento.dto.FrequenciaItemRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaItemResponse;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaResponse;
import br.com.pinsaude.faturamento.repository.FrequenciaItemRepository;
import br.com.pinsaude.faturamento.repository.FrequenciaMedicaRepository;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.TomadorModalidadeRepository;
import br.com.pinsaude.faturamento.repository.TomadorOcorrenciaRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoOperacionalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FrequenciaService {

    private final FrequenciaMedicaRepository frequenciaRepo;
    private final FrequenciaItemRepository itemRepo;
    private final TomadorServicoOperacionalRepository setorRepo;
    private final TomadorModalidadeRepository modalidadeRepo;
    private final StorageService storageService;
    private final MedicoTomadorRepository medicoTomadorRepo;
    private final TomadorOcorrenciaRepository ocorrenciaRepo;

    public FrequenciaService(FrequenciaMedicaRepository frequenciaRepo,
                             FrequenciaItemRepository itemRepo,
                             TomadorServicoOperacionalRepository setorRepo,
                             TomadorModalidadeRepository modalidadeRepo,
                             StorageService storageService,
                             MedicoTomadorRepository medicoTomadorRepo,
                             TomadorOcorrenciaRepository ocorrenciaRepo) {
        this.frequenciaRepo = frequenciaRepo;
        this.itemRepo       = itemRepo;
        this.setorRepo      = setorRepo;
        this.modalidadeRepo = modalidadeRepo;
        this.storageService = storageService;
        this.medicoTomadorRepo = medicoTomadorRepo;
        this.ocorrenciaRepo = ocorrenciaRepo;
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

        // PINSAUDE-13.26: modalidade (e opcionalmente ocorrência) escolhidas uma única vez aqui —
        // nenhum lançamento de plantão pergunta mais isso (ver adicionarItem/atualizarItem).
        TomadorModalidade modalidade = modalidadeRepo.findById(req.modalidadeId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Modalidade não encontrada: " + req.modalidadeId()));
        if (!modalidade.getTomadorId().equals(req.tomadorId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Modalidade não pertence ao tomador informado");
        }
        if (!modalidade.getTipo().equals(req.tipoMedico())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Modalidade do tipo " + modalidade.getTipo() + " não pode ser usada numa "
                    + "frequência com Tipo de Escala " + req.tipoMedico());
        }
        TomadorOcorrencia ocorrencia = resolverOcorrencia(req.ocorrenciaId());
        if (ocorrencia != null && !ocorrencia.getTomadorId().equals(req.tomadorId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ocorrência não pertence ao tomador informado");
        }

        String tenant = SecurityUtils.currentCnpjTenant();

        FrequenciaMedica f = new FrequenciaMedica();
        f.setCnpjIdTenant(tenant != null ? tenant : "");
        f.setTomadorId(req.tomadorId());
        f.setMedicoId(req.medicoId());
        f.setServicoOperacionalId(req.servicoOperacionalId());
        f.setCompetencia(req.competencia());
        f.setTipoMedico(req.tipoMedico());
        f.setModalidadeId(req.modalidadeId());
        f.setOcorrenciaId(req.ocorrenciaId());
        f.setStatus("RASCUNHO");
        frequenciaRepo.save(f);

        Map<UUID, TomadorModalidade> modalidadesMap = Map.of(modalidade.getId(), modalidade);
        Map<UUID, TomadorOcorrencia> ocorrenciasMap = ocorrencia != null ? Map.of(ocorrencia.getId(), ocorrencia) : Map.of();
        return FrequenciaMedicaResponse.from(f, setor, List.of(), modalidadesMap, ocorrenciasMap);
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

        // PINSAUDE-13.26: inclui também o modalidadeId/ocorrenciaId fixo de cada frequência no
        // batch — não só os referenciados pelos itens — pra resolver modalidadeNome/ocorrenciaNome
        // no header mesmo quando a frequência ainda não tem nenhum item lançado.
        List<UUID> modalidadeIds = Stream.concat(
                itensPorFrequencia.values().stream().flatMap(List::stream).map(FrequenciaItem::getModalidadeId),
                filtered.stream().map(FrequenciaMedica::getModalidadeId).filter(i -> i != null)
            ).distinct().toList();
        Map<UUID, TomadorModalidade> modalidadesMap = modalidadeRepo.findAllById(modalidadeIds).stream()
            .collect(Collectors.toMap(TomadorModalidade::getId, Function.identity()));

        List<UUID> ocorrenciaIds = Stream.concat(
                itensPorFrequencia.values().stream().flatMap(List::stream).map(FrequenciaItem::getOcorrenciaId),
                filtered.stream().map(FrequenciaMedica::getOcorrenciaId)
            ).filter(i -> i != null).distinct().toList();
        Map<UUID, TomadorOcorrencia> ocorrenciasMap = ocorrenciaRepo.findAllById(ocorrenciaIds).stream()
            .collect(Collectors.toMap(TomadorOcorrencia::getId, Function.identity()));

        return filtered.stream().map(f -> {
            List<FrequenciaItemResponse> itemResponses = itensPorFrequencia
                .getOrDefault(f.getId(), List.of()).stream()
                .sorted((a, b) -> a.getDataExecucao().compareTo(b.getDataExecucao()))
                .map(i -> FrequenciaItemResponse.from(i, modalidadesMap.get(i.getModalidadeId()),
                    ocorrenciasMap.get(i.getOcorrenciaId())))
                .toList();
            return FrequenciaMedicaResponse.from(f, setoresMap.get(f.getServicoOperacionalId()), itemResponses,
                modalidadesMap, ocorrenciasMap);
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

        UUID modalidadeId = resolverModalidadeIdParaItem(f, req.modalidadeId());
        UUID ocorrenciaId = f.getModalidadeId() != null ? f.getOcorrenciaId() : req.ocorrenciaId();
        TomadorModalidade modalidade = modalidadeRepo.findById(modalidadeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Modalidade não encontrada: " + modalidadeId));
        validarCouplingTipoEscala(f, modalidade);
        TomadorOcorrencia ocorrencia = resolverOcorrencia(ocorrenciaId);

        BigDecimal horasTrabalhadas = calcularHorasTrabalhadas(modalidade, req);
        boolean diarista = "DIARISTA".equals(modalidade.getTipo());

        FrequenciaItem item = new FrequenciaItem();
        item.setFrequenciaId(frequenciaId);
        item.setModalidadeId(modalidadeId);
        item.setDataExecucao(req.dataExecucao());
        item.setOcorrencia(req.ocorrencia());
        item.setOcorrenciaId(ocorrenciaId);
        item.setHorasTrabalhadas(horasTrabalhadas);
        item.setHoraInicio(diarista ? req.horaInicio() : null);
        item.setHoraFim(diarista ? req.horaFim() : null);
        // Snapshot de preço no momento do lançamento
        item.setValorUnitarioCentavos(calcularValorItem(modalidade));
        item.setDeslocamentoCentavos(modalidade.getDeslocamentoCentavos());
        item.setOcorrenciaValorCentavos(calcularValorOcorrencia(ocorrencia, modalidade.getValorCentavos()));
        itemRepo.save(item);

        return FrequenciaItemResponse.from(item, modalidade, ocorrencia);
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

        UUID modalidadeId = resolverModalidadeIdParaItem(f, req.modalidadeId());
        UUID ocorrenciaId = f.getModalidadeId() != null ? f.getOcorrenciaId() : req.ocorrenciaId();
        TomadorModalidade modalidade = modalidadeRepo.findById(modalidadeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Modalidade não encontrada: " + modalidadeId));
        validarCouplingTipoEscala(f, modalidade);
        TomadorOcorrencia ocorrencia = resolverOcorrencia(ocorrenciaId);

        BigDecimal horasTrabalhadas = calcularHorasTrabalhadas(modalidade, req);
        boolean diarista = "DIARISTA".equals(modalidade.getTipo());

        item.setModalidadeId(modalidadeId);
        item.setDataExecucao(req.dataExecucao());
        item.setOcorrencia(req.ocorrencia());
        item.setOcorrenciaId(ocorrenciaId);
        item.setHorasTrabalhadas(horasTrabalhadas);
        item.setHoraInicio(diarista ? req.horaInicio() : null);
        item.setHoraFim(diarista ? req.horaFim() : null);
        item.setValorUnitarioCentavos(calcularValorItem(modalidade));
        item.setDeslocamentoCentavos(modalidade.getDeslocamentoCentavos());
        item.setOcorrenciaValorCentavos(calcularValorOcorrencia(ocorrencia, modalidade.getValorCentavos()));
        itemRepo.save(item);

        return FrequenciaItemResponse.from(item, modalidade, ocorrencia);
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

    // PINSAUDE-13.23: Plantonista paga valor flat por lançamento (comportamento inalterado desde
    // antes do EPIC-13.19). Diarista paga um valor mensal fixo somado uma única vez pela
    // frequência (ver FrequenciaMedicaResponse.valorMensalDiaristaUnico) — cada item individual
    // vale R$0 e serve só pra registrar presença/horas trabalhadas naquele dia, usadas no
    // acompanhamento semanal (FrequenciaMedicaResponse.calcularProgressoSemanal).
    private long calcularValorItem(TomadorModalidade modalidade) {
        if ("DIARISTA".equals(modalidade.getTipo())) return 0L;
        return modalidade.getValorCentavos();
    }

    // PINSAUDE-13.25: modalidade DIARISTA não aceita mais horasTrabalhadas direto do cliente —
    // o médico digita a hora de entrada e saída daquele dia (também impressas no PDF, ver
    // frequenciaPdf.ts), e horasTrabalhadas é sempre derivado daqui, nunca do request. Turnos que
    // atravessam a meia-noite (ex: 19:00 às 07:00) são detectados quando horaFim <= horaInicio,
    // somando 24h à duração. PLANTONISTA nunca chama este método (retorna null).
    private BigDecimal calcularHorasTrabalhadas(TomadorModalidade modalidade, FrequenciaItemRequest req) {
        if (!"DIARISTA".equals(modalidade.getTipo())) return null;
        if (req.horaInicio() == null || req.horaFim() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Informe o horário de entrada e saída para lançar um item desta modalidade (Diarista)");
        }
        if (req.horaInicio().equals(req.horaFim())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Horário de saída deve ser diferente do horário de entrada");
        }
        Duration duracao = Duration.between(req.horaInicio(), req.horaFim());
        if (duracao.isNegative()) duracao = duracao.plusHours(24);
        return BigDecimal.valueOf(duracao.toMinutes())
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    // PINSAUDE-13.23: uma frequência só pode lançar itens de modalidades cujo tipo bata com o
    // Tipo de Escala dela (Plantonista só Plantonista, Diarista só Diarista). tipoMedico nulo
    // (registro legado anterior ao EPIC-13.11) faz bypass — sem essa restrição pra não quebrar
    // dados antigos que nunca tiveram esse campo preenchido.
    private void validarCouplingTipoEscala(FrequenciaMedica f, TomadorModalidade modalidade) {
        if (f.getTipoMedico() == null) return;
        if (!f.getTipoMedico().equals(modalidade.getTipo())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Modalidade do tipo " + modalidade.getTipo() + " não pode ser lançada numa "
                    + "frequência com Tipo de Escala " + f.getTipoMedico());
        }
    }

    // PINSAUDE-13.26: quando a frequência tem modalidade fixa (escolhida na criação), todo item
    // usa sempre essa modalidade — o valor vindo do request do item é ignorado. Só frequências
    // legadas (modalidadeId nulo, criadas antes desta mudança) continuam exigindo modalidadeId
    // no request de cada lançamento, exatamente como antes.
    private UUID resolverModalidadeIdParaItem(FrequenciaMedica f, UUID reqModalidadeId) {
        if (f.getModalidadeId() != null) return f.getModalidadeId();
        if (reqModalidadeId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Informe a modalidade para este lançamento");
        }
        return reqModalidadeId;
    }

    private TomadorOcorrencia resolverOcorrencia(UUID ocorrenciaId) {
        if (ocorrenciaId == null) return null;
        return ocorrenciaRepo.findById(ocorrenciaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Ocorrência não encontrada: " + ocorrenciaId));
    }

    // ocorrencia_valor = round(valorModalidadeCentavos × %/100) + valorFixo — soma os dois campos
    // quando ambos estão preenchidos (ex: "10% + R$ 50,00"). SEM_VALOR ou nenhuma ocorrência = 0.
    // % incide sobre o valor CADASTRADO da modalidade (valorCentavos), não sobre o valor
    // proporcional já calculado do item — mesma leitura usada desde o plano original do EPIC-13.19.
    private Long calcularValorOcorrencia(TomadorOcorrencia ocorrencia, long valorModalidadeCentavos) {
        if (ocorrencia == null) return null;
        long total = 0L;
        if (ocorrencia.getValorPercentual() != null) {
            total += BigDecimal.valueOf(valorModalidadeCentavos)
                .multiply(ocorrencia.getValorPercentual())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
        }
        if (ocorrencia.getValorCentavos() != null) {
            total += ocorrencia.getValorCentavos();
        }
        return total;
    }

    private FrequenciaMedica findOrThrow(UUID id) {
        return frequenciaRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Frequência não encontrada: " + id));
    }

    private FrequenciaMedicaResponse toResponse(FrequenciaMedica f) {
        TomadorServicoOperacional setor = setorRepo.findById(f.getServicoOperacionalId()).orElse(null);
        List<FrequenciaItem> itens = itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(f.getId());

        // PINSAUDE-13.26: inclui o modalidadeId/ocorrenciaId fixo da frequência no batch, além
        // dos referenciados pelos itens — necessário mesmo com 0 itens (frequência recém-criada).
        List<UUID> modalidadeIds = Stream.concat(
                itens.stream().map(FrequenciaItem::getModalidadeId),
                Stream.ofNullable(f.getModalidadeId())
            ).distinct().toList();
        // Collectors.toMap produz um HashMap regular (tolera .get(null) retornando null) — nunca
        // usar Map.of() aqui, cujo .get(null) lança NPE e quebraria itens sem ocorrência (a
        // maioria) quando o batch de ocorrenciaIds estiver vazio.
        Map<UUID, TomadorModalidade> modalidadesMap = modalidadeRepo.findAllById(modalidadeIds).stream()
            .collect(Collectors.toMap(TomadorModalidade::getId, Function.identity()));

        List<UUID> ocorrenciaIds = Stream.concat(
                itens.stream().map(FrequenciaItem::getOcorrenciaId),
                Stream.ofNullable(f.getOcorrenciaId())
            ).filter(i -> i != null).distinct().toList();
        Map<UUID, TomadorOcorrencia> ocorrenciasMap = ocorrenciaRepo.findAllById(ocorrenciaIds).stream()
            .collect(Collectors.toMap(TomadorOcorrencia::getId, Function.identity()));

        List<FrequenciaItemResponse> itemResponses = itens.stream()
            .map(i -> FrequenciaItemResponse.from(i, modalidadesMap.get(i.getModalidadeId()),
                ocorrenciasMap.get(i.getOcorrenciaId())))
            .toList();

        return FrequenciaMedicaResponse.from(f, setor, itemResponses, modalidadesMap, ocorrenciasMap);
    }

    // PINSAUDE-13.26: exclusão só permitida em RASCUNHO (antes de gerar PDF) — decisão
    // conservadora para não arriscar apagar algo já compartilhado/assinado. Itens são apagados
    // em cascata pela FK (ON DELETE CASCADE em frequencia_itens.frequencia_id).
    @Transactional
    public void excluir(UUID id) {
        FrequenciaMedica f = findOrThrow(id);
        if (!"RASCUNHO".equals(f.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Só é possível excluir uma frequência em Rascunho — status atual: " + f.getStatus());
        }
        frequenciaRepo.delete(f);
    }
}
