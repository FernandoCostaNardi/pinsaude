package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.FrequenciaItem;
import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorGrupoFaturamento;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import br.com.pinsaude.faturamento.domain.TipoEscala;
import br.com.pinsaude.faturamento.dto.FrequenciaItemRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaItemResponse;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaEditRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaResponse;
import br.com.pinsaude.faturamento.repository.FrequenciaItemRepository;
import br.com.pinsaude.faturamento.repository.FrequenciaMedicaRepository;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoFaturamentoRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoSetorRepository;
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
    private final TomadorGrupoFaturamentoRepository grupoRepo;
    private final TomadorGrupoSetorRepository grupoSetorRepo;

    public FrequenciaService(FrequenciaMedicaRepository frequenciaRepo,
                             FrequenciaItemRepository itemRepo,
                             TomadorServicoOperacionalRepository setorRepo,
                             TomadorModalidadeRepository modalidadeRepo,
                             StorageService storageService,
                             MedicoTomadorRepository medicoTomadorRepo,
                             TomadorOcorrenciaRepository ocorrenciaRepo,
                             TomadorGrupoFaturamentoRepository grupoRepo,
                             TomadorGrupoSetorRepository grupoSetorRepo) {
        this.frequenciaRepo = frequenciaRepo;
        this.itemRepo       = itemRepo;
        this.setorRepo      = setorRepo;
        this.modalidadeRepo = modalidadeRepo;
        this.storageService = storageService;
        this.medicoTomadorRepo = medicoTomadorRepo;
        this.ocorrenciaRepo = ocorrenciaRepo;
        this.grupoRepo = grupoRepo;
        this.grupoSetorRepo = grupoSetorRepo;
    }

    // Grupo precisa pertencer ao mesmo tomador e o setor precisa estar vinculado a esse grupo
    // (catálogo de setores reutilizável entre grupos — ver PINSAUDE). Usado por criar()/atualizar().
    private void validarGrupoESetor(UUID tomadorId, UUID grupoId, UUID setorId) {
        TomadorGrupoFaturamento grupo = grupoRepo.findById(grupoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Grupo de faturamento não encontrado: " + grupoId));
        if (!grupo.getTomadorId().equals(tomadorId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Grupo de faturamento não pertence ao tomador informado");
        }
        if (!grupoSetorRepo.existsByGrupoIdAndSetorId(grupoId, setorId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Setor operacional não pertence ao grupo de faturamento informado");
        }
    }

    // ── Frequência CRUD ───────────────────────────────────────────────────────

    @Transactional
    public FrequenciaMedicaResponse criar(FrequenciaMedicaRequest req) {
        boolean modalidadeFixa = TipoEscala.isModalidadeFixa(req.tipoMedico());

        // Modalidade (e ocorrência) só são fixadas na frequência pros tipos "fixos" (DIARISTA,
        // EVOLUCIONISTA — ver TipoEscala.TIPOS_MODALIDADE_FIXA, PINSAUDE-13.26). Tipos "por
        // lançamento" (PLANTONISTA, EVOLUCIONISTA_FDS) e "por serviço" (SERVICOS) continuam
        // escolhendo modalidade/ocorrência a cada lançamento (ajuste pós-13.26, ver CLAUDE.md) —
        // não faz sentido receber nenhum dos dois aqui nesse caso. `modalidadeFixa` já é `false`
        // pra SERVICOS (não está em TIPOS_MODALIDADE_FIXA), então cai neste ramo sem precisar de
        // nenhuma ramificação extra.
        if (modalidadeFixa) {
            if (req.modalidadeId() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Modalidade é obrigatória para Tipo de Escala " + TipoEscala.label(req.tipoMedico()));
            }
        } else {
            if (req.modalidadeId() != null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Modalidade não deve ser informada para Tipo de Escala " + TipoEscala.label(req.tipoMedico())
                        + " — é escolhida a cada plantão lançado");
            }
            if (req.ocorrenciaId() != null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ocorrência não deve ser informada para Tipo de Escala " + TipoEscala.label(req.tipoMedico())
                        + " — é escolhida a cada plantão lançado");
            }
        }

        // Tipos fixos: 1 frequência por médico+setor+competência+modalidade (o médico pode ter
        // mais de um cargo/valor mensal diferente no mesmo mês, cada um em sua própria
        // frequência). Tipos "por lançamento" (PLANTONISTA, EVOLUCIONISTA_FDS): sem checagem de
        // duplicidade — o médico pode abrir quantas frequências ("folhas") precisar pro mesmo
        // médico+setor+competência (ex.: uma pra semana, outra pro fim de semana, cada uma
        // virando um PDF separado entregue ao hospital) — ver V34/V40/V41.
        if (modalidadeFixa && frequenciaRepo.existsByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
                req.medicoId(), req.servicoOperacionalId(), req.competencia(), req.tipoMedico(), req.modalidadeId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe uma frequência para este médico, neste setor, nesta competência, com esta modalidade");
        }

        TomadorServicoOperacional setor = setorRepo.findById(req.servicoOperacionalId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Setor operacional não encontrado: " + req.servicoOperacionalId()));

        if (!setor.getTomadorId().equals(req.tomadorId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Setor operacional não pertence ao tomador informado");
        }

        validarGrupoESetor(req.tomadorId(), req.grupoId(), req.servicoOperacionalId());

        if (!medicoTomadorRepo.existsByTomadorIdAndMedicoId(req.tomadorId(), req.medicoId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Médico não está alocado a este tomador");
        }

        TomadorModalidade modalidade = null;
        TomadorOcorrencia ocorrencia = null;
        if (modalidadeFixa) {
            modalidade = modalidadeRepo.findById(req.modalidadeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Modalidade não encontrada: " + req.modalidadeId()));
            if (!modalidade.getTomadorId().equals(req.tomadorId())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Modalidade não pertence ao tomador informado");
            }
            if (!modalidade.suportaTipo(req.tipoMedico())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Modalidade do tipo " + String.join("/", modalidade.getTipos()) + " não pode ser usada numa "
                        + "frequência com Tipo de Escala " + req.tipoMedico());
            }
            ocorrencia = resolverOcorrencia(req.ocorrenciaId());
            if (ocorrencia != null && !ocorrencia.getTomadorId().equals(req.tomadorId())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ocorrência não pertence ao tomador informado");
            }
        }

        String tenant = SecurityUtils.currentCnpjTenant();

        FrequenciaMedica f = new FrequenciaMedica();
        f.setCnpjIdTenant(tenant != null ? tenant : "");
        f.setTomadorId(req.tomadorId());
        f.setMedicoId(req.medicoId());
        f.setServicoOperacionalId(req.servicoOperacionalId());
        f.setGrupoId(req.grupoId());
        f.setCompetencia(req.competencia());
        f.setTipoMedico(req.tipoMedico());
        f.setModalidadeId(modalidadeFixa ? req.modalidadeId() : null);
        f.setOcorrenciaId(modalidadeFixa ? req.ocorrenciaId() : null);
        f.setStatus("RASCUNHO");
        frequenciaRepo.save(f);

        Map<UUID, TomadorModalidade> modalidadesMap = modalidade != null ? Map.of(modalidade.getId(), modalidade) : Map.of();
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

    // Edição pós-criação: só Competência e Setor Operacional são editáveis (Tomador, Tipo de
    // Escala, Modalidade e Ocorrência permanecem fixos — pedido explícito do cliente; erros
    // nesses outros campos continuam exigindo excluir e criar de novo). Permitida em qualquer
    // status exceto FATURADA — mesmo limite já usado para editar/remover um item individual.
    @Transactional
    public FrequenciaMedicaResponse atualizar(UUID id, FrequenciaMedicaEditRequest req) {
        FrequenciaMedica f = findOrThrow(id);
        if ("FATURADA".equals(f.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é possível editar uma frequência já faturada");
        }

        TomadorServicoOperacional setor = setorRepo.findById(req.servicoOperacionalId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Setor operacional não encontrado: " + req.servicoOperacionalId()));
        if (!setor.getTomadorId().equals(f.getTomadorId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Setor operacional não pertence ao tomador desta frequência");
        }

        validarGrupoESetor(f.getTomadorId(), req.grupoId(), req.servicoOperacionalId());

        // Conflito só é checado pros tipos "fixos" (chave inclui a modalidade fixa, que não muda
        // nesta edição) — Plantonista nunca teve checagem de duplicidade (ver criar()/V34).
        boolean chaveMudou = !req.competencia().equals(f.getCompetencia())
            || !req.servicoOperacionalId().equals(f.getServicoOperacionalId());
        if (chaveMudou && TipoEscala.isModalidadeFixa(f.getTipoMedico())) {
            frequenciaRepo.findByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
                    f.getMedicoId(), req.servicoOperacionalId(), req.competencia(), f.getTipoMedico(), f.getModalidadeId())
                .filter(outra -> !outra.getId().equals(id))
                .ifPresent(outra -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Já existe uma frequência para este médico, neste setor, nesta competência, com esta modalidade");
                });
        }

        f.setCompetencia(req.competencia());
        f.setServicoOperacionalId(req.servicoOperacionalId());
        f.setGrupoId(req.grupoId());
        frequenciaRepo.save(f);

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
        TomadorModalidade modalidade = modalidadeRepo.findById(modalidadeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Modalidade não encontrada: " + modalidadeId));
        validarCouplingTipoEscala(f, modalidade);
        // PINSAUDE-13.26 (ajuste): quando a frequência tem modalidade fixa, a ocorrência também é
        // fixa e seu valor é aplicado UMA ÚNICA VEZ sobre o valor da modalidade — não mais por
        // item (ver FrequenciaMedicaResponse.calcularValorOcorrenciaUnico). Só frequências
        // legadas (sem modalidade fixa) continuam com ocorrência escolhida por lançamento.
        TomadorOcorrencia ocorrencia = f.getModalidadeId() == null ? resolverOcorrencia(req.ocorrenciaId()) : null;

        BigDecimal horasTrabalhadas = calcularHorasTrabalhadas(modalidade, req);
        boolean modalidadeFixa = modalidade.isFixa();
        Integer quantidade = modalidade.isServico() ? validarQuantidade(req.quantidade(), modalidade) : null;

        FrequenciaItem item = new FrequenciaItem();
        item.setFrequenciaId(frequenciaId);
        item.setModalidadeId(modalidadeId);
        item.setDataExecucao(req.dataExecucao());
        item.setOcorrencia(req.ocorrencia());
        item.setOcorrenciaId(ocorrencia != null ? ocorrencia.getId() : null);
        item.setHorasTrabalhadas(horasTrabalhadas);
        item.setHoraInicio(modalidadeFixa ? req.horaInicio() : null);
        item.setHoraFim(modalidadeFixa ? req.horaFim() : null);
        item.setQuantidade(quantidade);
        // Snapshot de preço no momento do lançamento
        item.setValorUnitarioCentavos(calcularValorItem(modalidade, quantidade));
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
        TomadorModalidade modalidade = modalidadeRepo.findById(modalidadeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Modalidade não encontrada: " + modalidadeId));
        validarCouplingTipoEscala(f, modalidade);
        // PINSAUDE-13.26 (ajuste): ver comentário equivalente em adicionarItem — ocorrência fixa
        // não é mais resolvida/valorada por item.
        TomadorOcorrencia ocorrencia = f.getModalidadeId() == null ? resolverOcorrencia(req.ocorrenciaId()) : null;

        BigDecimal horasTrabalhadas = calcularHorasTrabalhadas(modalidade, req);
        boolean modalidadeFixa = modalidade.isFixa();
        Integer quantidade = modalidade.isServico() ? validarQuantidade(req.quantidade(), modalidade) : null;

        item.setModalidadeId(modalidadeId);
        item.setDataExecucao(req.dataExecucao());
        item.setOcorrencia(req.ocorrencia());
        item.setOcorrenciaId(ocorrencia != null ? ocorrencia.getId() : null);
        item.setHorasTrabalhadas(horasTrabalhadas);
        item.setHoraInicio(modalidadeFixa ? req.horaInicio() : null);
        item.setHoraFim(modalidadeFixa ? req.horaFim() : null);
        item.setQuantidade(quantidade);
        item.setValorUnitarioCentavos(calcularValorItem(modalidade, quantidade));
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
    // acompanhamento semanal (FrequenciaMedicaResponse.calcularProgressoSemanal). Serviços paga
    // quantidade × valorCentavos por lançamento — quantidade já validada (> 0) em
    // validarQuantidade antes desta chamada. Math.multiplyExact evita overflow silencioso (a
    // quantidade vem do usuário sem teto explícito).
    private long calcularValorItem(TomadorModalidade modalidade, Integer quantidade) {
        if (modalidade.isFixa()) return 0L;
        if (modalidade.isServico()) return Math.multiplyExact(modalidade.getValorCentavos(), quantidade.longValue());
        return modalidade.getValorCentavos();
    }

    // Modalidade SERVICOS exige uma quantidade > 0 de serviços realizados naquele lançamento —
    // análogo a horaInicio/horaFim exigidos por calcularHorasTrabalhadas para a família fixa.
    private Integer validarQuantidade(Integer quantidade, TomadorModalidade modalidade) {
        if (quantidade == null || quantidade <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Informe a quantidade de serviços realizados para lançar um item desta modalidade ("
                    + TipoEscala.label(modalidade.getTipos()[0]) + ")");
        }
        return quantidade;
    }

    // PINSAUDE-13.25: modalidades de tipo fixo (DIARISTA, EVOLUCIONISTA — ver TipoEscala) não
    // aceitam mais horasTrabalhadas direto do cliente — o médico digita a hora de entrada e saída
    // daquele dia (também impressas no PDF, ver frequenciaPdf.ts), e horasTrabalhadas é sempre
    // derivado daqui, nunca do request. Turnos que atravessam a meia-noite (ex: 19:00 às 07:00)
    // são detectados quando horaFim <= horaInicio, somando 24h à duração. Tipos "por lançamento"
    // (PLANTONISTA, EVOLUCIONISTA_FDS) nunca precisam desse cálculo (retorna null).
    private BigDecimal calcularHorasTrabalhadas(TomadorModalidade modalidade, FrequenciaItemRequest req) {
        if (!modalidade.isFixa()) return null;
        if (req.horaInicio() == null || req.horaFim() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Informe o horário de entrada e saída para lançar um item desta modalidade (" + TipoEscala.label(modalidade.getTipos()[0]) + ")");
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
        if (!modalidade.suportaTipo(f.getTipoMedico())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Modalidade do tipo " + String.join("/", modalidade.getTipos()) + " não pode ser lançada numa "
                    + "frequência com Tipo de Escala " + f.getTipoMedico());
        }
    }

    // PINSAUDE-13.26 (Diarista) / ajuste pós-implantação (Plantonista): quando a frequência tem
    // modalidade fixa (só acontece para Diarista, escolhida na criação), todo item usa sempre
    // essa modalidade — o valor vindo do request do item é ignorado. Frequências com
    // modalidadeId nulo — Plantonista (sempre, por design — ver criar()) ou legadas anteriores
    // ao PINSAUDE-13.26 — continuam exigindo modalidadeId no request de cada lançamento, podendo
    // variar turno/modalidade a cada plantão dentro da mesma frequência.
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

    // Exclusão permitida em qualquer status exceto FATURADA (já entrou no Fechamento/NFS-e —
    // apagar quebraria a rastreabilidade com a nota já emitida). Mesmo limite usado para editar
    // a frequência e para editar/remover um item individual. Itens são apagados em cascata pela
    // FK (ON DELETE CASCADE em frequencia_itens.frequencia_id); o documento assinado (se houver)
    // é removido do storage antes — nunca deixa arquivo órfão no MinIO.
    @Transactional
    public void excluir(UUID id) {
        FrequenciaMedica f = findOrThrow(id);
        if ("FATURADA".equals(f.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é possível excluir uma frequência já faturada");
        }
        if (f.getDocumentoAssinadoKey() != null) {
            storageService.delete(f.getDocumentoAssinadoKey());
        }
        frequenciaRepo.delete(f);
    }
}
