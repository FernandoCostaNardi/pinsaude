package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.*;
import br.com.pinsaude.onboarding.port.ContratoAssinaturaPort;
import br.com.pinsaude.onboarding.repository.*;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MedicoService {

    private static final Logger log = LoggerFactory.getLogger(MedicoService.class);

    private final MedicoRepository medicoRepo;
    private final VinculoMedicoEmpresaRepository vinculoRepo;
    private final DadosBancariosMedicoRepository dadosBancariosRepo;
    private final DocumentoMedicoRepository documentoRepo;
    private final ChecklistCondutaRepository checklistRepo;
    private final HistoricoMedicoRepository historicoRepo;
    private final ConviteMedicoRepository conviteRepo;
    private final ContratoAssinaturaRepository contratoRepo;
    private final CryptoService cryptoService;
    private final StorageService storageService;
    private final ConviteService conviteService;
    private final ContratoAssinaturaPort contratoPort;
    private final NotificacaoService notificacaoService;
    private final EmpresaRepository empresaRepo;
    private final HistoricoTaxaPinRepository historicoTaxaPinRepo;
    private final KeycloakAdminService keycloakAdminService;
    private final DadosCivisMedicoRepository dadosCivisRepo;
    private final DeclaracoesLgpdMedicoRepository declaracoesLgpdRepo;

    private static final BigDecimal TAXA_PIN_DEFAULT = new BigDecimal("0.1500");

    public MedicoService(
            MedicoRepository medicoRepo,
            VinculoMedicoEmpresaRepository vinculoRepo,
            DadosBancariosMedicoRepository dadosBancariosRepo,
            DocumentoMedicoRepository documentoRepo,
            ChecklistCondutaRepository checklistRepo,
            HistoricoMedicoRepository historicoRepo,
            ConviteMedicoRepository conviteRepo,
            ContratoAssinaturaRepository contratoRepo,
            CryptoService cryptoService,
            StorageService storageService,
            ConviteService conviteService,
            ContratoAssinaturaPort contratoPort,
            NotificacaoService notificacaoService,
            EmpresaRepository empresaRepo,
            HistoricoTaxaPinRepository historicoTaxaPinRepo,
            KeycloakAdminService keycloakAdminService,
            DadosCivisMedicoRepository dadosCivisRepo,
            DeclaracoesLgpdMedicoRepository declaracoesLgpdRepo) {
        this.medicoRepo = medicoRepo;
        this.vinculoRepo = vinculoRepo;
        this.dadosBancariosRepo = dadosBancariosRepo;
        this.documentoRepo = documentoRepo;
        this.checklistRepo = checklistRepo;
        this.historicoRepo = historicoRepo;
        this.conviteRepo = conviteRepo;
        this.contratoRepo = contratoRepo;
        this.cryptoService = cryptoService;
        this.storageService = storageService;
        this.conviteService = conviteService;
        this.contratoPort = contratoPort;
        this.notificacaoService = notificacaoService;
        this.empresaRepo = empresaRepo;
        this.historicoTaxaPinRepo = historicoTaxaPinRepo;
        this.keycloakAdminService = keycloakAdminService;
        this.dadosCivisRepo = dadosCivisRepo;
        this.declaracoesLgpdRepo = declaracoesLgpdRepo;
    }

    public List<MedicoResponse> listarFilaAprovacao() {
        var pageable = org.springframework.data.domain.Pageable.unpaged();
        return medicoRepo.findAllByStatus(StatusMedico.RASCUNHO.name(), pageable)
            .getContent().stream()
            .sorted(java.util.Comparator.comparing(Medico::getCreatedAt))
            .map(this::toFullResponse)
            .toList();
    }

    public MedicoListResponse listar(int page, int size, String status) {
        var pageable = PageRequest.of(page, size);
        var result = StatusMedico.INATIVO.name().equals(status)
            ? medicoRepo.findAllByStatus(StatusMedico.INATIVO.name(), pageable)
            : medicoRepo.findAllByStatusNot(StatusMedico.INATIVO.name(), pageable);

        List<UUID> ids = result.getContent().stream().map(Medico::getId).toList();
        Map<UUID, UUID> medicoEmpresa = vinculoRepo.findByIdMedicoIdIn(ids).stream()
            .collect(Collectors.toMap(
                v -> v.getId().getMedicoId(),
                v -> v.getId().getEmpresaId(),
                (a, b) -> a
            ));

        return MedicoListResponse.from(result.map(m ->
            MedicoSummaryResponse.from(m, medicoEmpresa.get(m.getId()))));
    }

    public MedicoResponse buscarPorId(UUID id) {
        Medico medico = findOrThrow(id);
        return toFullResponse(medico);
    }

    @Transactional
    public MedicoResponse criar(MedicoRequest req) {
        String cpfNormalizado = req.cpf().replaceAll("\\D", "");
        String cpfHash = hashCpf(cpfNormalizado);

        if (medicoRepo.existsByCpfHash(cpfHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe um médico cadastrado com este CPF");
        }
        if (medicoRepo.existsByCrmAndCrmUf(req.crm(), req.crmUf().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe um médico com este CRM para a UF informada");
        }

        var medico = new Medico();
        medico.setCpfCriptografado(cryptoService.encrypt(cpfNormalizado));
        medico.setCpfHash(cpfHash);
        medico.setNome(req.nome());
        medico.setCrm(req.crm());
        medico.setCrmUf(req.crmUf().toUpperCase());
        medico.setEspecialidade(req.especialidade());
        medico.setEmail(req.email());
        medico.setTelefone(req.telefone());
        medico.setTaxaPinPct(req.taxaPinPct() != null ? req.taxaPinPct() : TAXA_PIN_DEFAULT);
        medico.setStatus(StatusMedico.RASCUNHO);
        medico = medicoRepo.save(medico);

        if (req.empresaId() != null) {
            var vinculoId = new VinculoMedicoEmpresaId(medico.getId(), req.empresaId());
            vinculoRepo.save(new VinculoMedicoEmpresa(vinculoId, StatusSocietario.ATIVO));
        }

        checklistRepo.save(new ChecklistConduta(medico.getId()));
        registrarHistorico(medico.getId(), TipoAcaoMedico.CADASTRO, "Médico cadastrado no sistema");

        return toFullResponse(medico);
    }

    @Transactional
    public MedicoResponse atualizar(UUID id, MedicoRequest req) {
        Medico medico = findOrThrow(id);

        String cpfNormalizado = req.cpf().replaceAll("\\D", "");
        String cpfHash = hashCpf(cpfNormalizado);

        boolean cpfMudou = !cpfHash.equals(medico.getCpfHash());
        if (cpfMudou && medicoRepo.existsByCpfHash(cpfHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe um médico cadastrado com este CPF");
        }

        boolean crmMudou = !medico.getCrm().equals(req.crm())
            || !medico.getCrmUf().equalsIgnoreCase(req.crmUf());
        if (crmMudou && medicoRepo.existsByCrmAndCrmUf(req.crm(), req.crmUf().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe um médico com este CRM para a UF informada");
        }

        medico.setCpfCriptografado(cryptoService.encrypt(cpfNormalizado));
        medico.setCpfHash(cpfHash);
        medico.setNome(req.nome());
        medico.setCrm(req.crm());
        medico.setCrmUf(req.crmUf().toUpperCase());
        medico.setEspecialidade(req.especialidade());
        medico.setEmail(req.email());
        medico.setTelefone(req.telefone());

        BigDecimal taxaAtual = medico.getTaxaPinPct();
        BigDecimal taxaNova  = req.taxaPinPct() != null ? req.taxaPinPct() : taxaAtual;
        if (taxaNova.compareTo(taxaAtual) != 0) {
            historicoTaxaPinRepo.save(new HistoricoTaxaPin(id, taxaAtual, taxaNova, getCurrentUser()));
            medico.setTaxaPinPct(taxaNova);
            registrarHistorico(id, TipoAcaoMedico.ATUALIZACAO_DADOS,
                String.format("Taxa Pin renegociada: %.2f%% → %.2f%%",
                    taxaAtual.multiply(new BigDecimal("100")),
                    taxaNova.multiply(new BigDecimal("100"))));
        }

        var saved = toFullResponse(medicoRepo.save(medico));
        registrarHistorico(id, TipoAcaoMedico.ATUALIZACAO_DADOS, "Dados pessoais atualizados");
        return saved;
    }

    @Transactional
    public MedicoResponse ativar(UUID id) {
        Medico medico = findOrThrow(id);
        validarPreRequisitosAtivacao(id, medico);
        medico.setStatus(StatusMedico.ATIVO);
        medicoRepo.save(medico);
        registrarHistorico(id, TipoAcaoMedico.ATIVACAO, "Médico ativado manualmente");
        notificacaoService.notificarMedicoAtivado(medico);
        liberarAcessoKeycloak(medico);
        return toFullResponse(medico);
    }

    @Transactional
    public ContratoAssinaturaResponse assinarContratoManual(UUID medicoId) {
        Medico medico = findOrThrow(medicoId);
        ContratoAssinatura contrato = contratoRepo.findTopByMedicoIdOrderByCreatedAtDesc(medicoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Nenhum contrato encontrado para este médico"));
        contrato.setStatus("ASSINADO");
        contrato.setAssinadoEm(OffsetDateTime.now());
        contratoRepo.save(contrato);
        registrarHistorico(medicoId, TipoAcaoMedico.ENVIO_CONTRATO,
            "Contrato marcado como assinado manualmente");
        verificarAtivacaoAutomatica(medico);
        return ContratoAssinaturaResponse.from(contrato);
    }

    @Transactional
    public MedicoResponse inativar(UUID id) {
        Medico medico = findOrThrow(id);
        medico.setStatus(StatusMedico.INATIVO);
        var saved = toFullResponse(medicoRepo.save(medico));
        registrarHistorico(id, TipoAcaoMedico.INATIVACAO, "Médico inativado");
        return saved;
    }

    @Transactional
    public EnviarConviteResponse enviarConvite(UUID medicoId) {
        Medico medico = findOrThrow(medicoId);
        var convite = conviteService.enviarConvite(medico);
        registrarHistorico(medicoId, TipoAcaoMedico.ENVIO_CONVITE,
            "Convite enviado para " + medico.getEmail());
        return EnviarConviteResponse.from(convite);
    }

    @Transactional
    public ContratoAssinaturaResponse enviarContrato(UUID medicoId) {
        Medico medico = findOrThrow(medicoId);
        String emailMedico = medico.getEmail();
        if (emailMedico == null || emailMedico.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Médico não possui e-mail cadastrado para envio do contrato");
        }

        ContratoAssinatura contrato;
        try {
            contrato = contratoPort.enviar(medico, emailMedico);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Erro ao enviar contrato ao Clicksign para medico={}: {}", medicoId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Falha na comunicação com o Clicksign: " + e.getMessage());
        }

        contrato = contratoRepo.save(contrato);
        registrarHistorico(medicoId, TipoAcaoMedico.ENVIO_CONTRATO,
            "Contrato enviado ao Clicksign (doc=" + contrato.getDocumentoKey() + ")");
        return ContratoAssinaturaResponse.from(contrato);
    }

    @Transactional
    public MedicoResponse atualizarJuntaComercial(UUID medicoId, AtualizarJuntaComercialRequest req) {
        Medico medico = findOrThrow(medicoId);
        String statusAnterior = medico.getStatusJuntaComercial();
        medico.setStatusJuntaComercial(req.status());
        medico = medicoRepo.save(medico);

        String descricao = "Status Junta Comercial: " + statusAnterior + " → " + req.status();
        if (req.observacao() != null && !req.observacao().isBlank()) {
            descricao += " | " + req.observacao();
        }
        registrarHistorico(medicoId, TipoAcaoMedico.ATUALIZACAO_JUNTA_COMERCIAL, descricao);

        if ("APROVADO".equals(req.status())) {
            verificarAtivacaoAutomatica(medico);
        }

        return toFullResponse(medico);
    }

    @Transactional
    public void processarWebhookClicksign(String documentoKey, String eventoNome) {
        var contratoOpt = contratoRepo.findByDocumentoKey(documentoKey);
        if (contratoOpt.isEmpty()) {
            log.warn("Webhook Clicksign recebido para documentoKey={} sem contrato cadastrado", documentoKey);
            return;
        }

        ContratoAssinatura contrato = contratoOpt.get();
        String novoStatus = mapearStatusClicksign(eventoNome);
        contrato.setStatus(novoStatus);
        if ("ASSINADO".equals(novoStatus)) {
            contrato.setAssinadoEm(OffsetDateTime.now());
        }
        contratoRepo.save(contrato);

        registrarHistorico(contrato.getMedicoId(), TipoAcaoMedico.ENVIO_CONTRATO,
            "Webhook Clicksign: contrato " + novoStatus + " (doc=" + documentoKey + ")");

        if ("ASSINADO".equals(novoStatus)) {
            Medico medico = medicoRepo.findById(contrato.getMedicoId()).orElse(null);
            if (medico != null) {
                verificarAtivacaoAutomatica(medico);
            }
        }
    }

    @Transactional
    public DadosBancariosMedicoResponse atualizarDadosBancarios(UUID medicoId, DadosBancariosMedicoRequest req) {
        if (!Boolean.TRUE.equals(req.confirmarAlteracao())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Alteração de dados bancários requer confirmação explícita. " +
                "Envie confirmarAlteracao: true no corpo da requisição.");
        }

        findOrThrow(medicoId);

        var dados = dadosBancariosRepo.findByMedicoId(medicoId)
            .orElseGet(() -> {
                var novo = new DadosBancariosMedico();
                novo.setMedicoId(medicoId);
                return novo;
            });

        dados.setTipoRecebimento(req.tipoRecebimento() != null ? req.tipoRecebimento() : "PIX");
        if ("TED".equals(req.tipoRecebimento())) {
            dados.setTipoPix(null);
            dados.setChavePIXCriptografada(null);
            dados.setCpfsAdicionaisSplit(null);
            dados.setBancoCodigo(req.bancoCodigo());
            dados.setBancoNome(req.bancoNome());
            dados.setAgencia(req.agencia());
            dados.setConta(req.conta());
            dados.setTipoConta(req.tipoConta());
        } else {
            dados.setTipoPix(req.tipoPix());
            dados.setChavePIXCriptografada(
                req.chavePix() != null ? cryptoService.encrypt(req.chavePix()) : null);
            dados.setCpfsAdicionaisSplit(req.cpfsAdicionaisSplit());
            dados.setBancoCodigo(null);
            dados.setBancoNome(null);
            dados.setAgencia(null);
            dados.setConta(null);
            dados.setTipoConta(null);
        }
        dados = dadosBancariosRepo.save(dados);
        registrarHistorico(medicoId, TipoAcaoMedico.ATUALIZACAO_DADOS_BANCARIOS, "Dados bancários atualizados");

        String chavePIXDecriptografada = dados.getChavePIXCriptografada() != null
            ? cryptoService.decrypt(dados.getChavePIXCriptografada()) : null;
        return DadosBancariosMedicoResponse.from(dados, chavePIXDecriptografada);
    }

    public List<DocumentoMedicoResponse> listarDocumentos(UUID medicoId) {
        findOrThrow(medicoId);
        return documentoRepo.findByMedicoId(medicoId).stream()
            .map(DocumentoMedicoResponse::from)
            .toList();
    }

    @Transactional
    public DocumentoMedicoResponse uploadDocumento(UUID medicoId, TipoDocumentoMedico tipo, MultipartFile arquivo) {
        findOrThrow(medicoId);
        if (arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo não pode estar vazio");
        }

        String caminho = storageService.upload(medicoId, tipo.name(), arquivo);

        var doc = new DocumentoMedico();
        doc.setMedicoId(medicoId);
        doc.setTipo(tipo);
        doc.setNomeArquivo(arquivo.getOriginalFilename());
        doc.setCaminhoStorage(caminho);
        doc.setStatusValidacao(StatusValidacaoDocumento.PENDENTE);
        doc = documentoRepo.save(doc);
        registrarHistorico(medicoId, TipoAcaoMedico.UPLOAD_DOCUMENTO,
            "Documento enviado: " + tipo.name());

        return DocumentoMedicoResponse.from(doc);
    }

    @Transactional
    public DocumentoMedicoResponse validarDocumento(UUID medicoId, UUID docId, ValidarDocumentoRequest req) {
        findOrThrow(medicoId);
        DocumentoMedico doc = documentoRepo.findById(docId)
            .filter(d -> medicoId.equals(d.getMedicoId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Documento não encontrado: " + docId));
        if (StatusValidacaoDocumento.REPROVADO == req.statusValidacao()
                && (req.motivoReprovacao() == null || req.motivoReprovacao().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Motivo de reprovação é obrigatório ao reprovar um documento");
        }
        boolean reprovado = StatusValidacaoDocumento.REPROVADO == req.statusValidacao();
        doc.setStatusValidacao(req.statusValidacao());
        doc.setMotivoReprovacao(reprovado ? req.motivoReprovacao() : null);
        var saved = DocumentoMedicoResponse.from(documentoRepo.save(doc));
        registrarHistorico(medicoId, TipoAcaoMedico.VALIDACAO_DOCUMENTO,
            "Documento " + req.statusValidacao().name() + ": " + doc.getTipo().name());
        if (reprovado) {
            Medico medico = findOrThrow(medicoId);
            notificacaoService.notificarDocumentoReprovado(medico, doc.getTipo().name(), req.motivoReprovacao());
        }
        return saved;
    }

    @Transactional
    public void deletarDocumento(UUID medicoId, UUID docId) {
        findOrThrow(medicoId);
        DocumentoMedico doc = documentoRepo.findById(docId)
            .filter(d -> medicoId.equals(d.getMedicoId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Documento não encontrado: " + docId));
        storageService.delete(doc.getCaminhoStorage());
        documentoRepo.delete(doc);
        registrarHistorico(medicoId, TipoAcaoMedico.EXCLUSAO_DOCUMENTO,
            "Documento removido: " + doc.getTipo().name());
    }

    public String getDocumentoUrl(UUID medicoId, UUID docId) {
        findOrThrow(medicoId);
        DocumentoMedico doc = documentoRepo.findById(docId)
            .filter(d -> medicoId.equals(d.getMedicoId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Documento não encontrado: " + docId));
        return storageService.getPresignedUrl(doc.getCaminhoStorage());
    }

    public ResponseEntity<StreamingResponseBody> downloadDocumento(UUID medicoId, UUID docId) {
        findOrThrow(medicoId);
        DocumentoMedico doc = documentoRepo.findById(docId)
            .filter(d -> medicoId.equals(d.getMedicoId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Documento não encontrado: " + docId));

        var stream = storageService.getObjectStream(doc.getCaminhoStorage());
        StreamingResponseBody body = out -> {
            try (stream) { stream.transferTo(out); }
        };
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(resolveContentType(doc.getNomeArquivo())))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getNomeArquivo() + "\"")
            .body(body);
    }

    private String resolveContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String f = filename.toLowerCase();
        if (f.endsWith(".pdf"))  return "application/pdf";
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".png"))  return "image/png";
        return "application/octet-stream";
    }

    @Transactional
    public ChecklistCondutaResponse atualizarChecklist(UUID medicoId, ChecklistCondutaRequest req) {
        findOrThrow(medicoId);
        var checklist = checklistRepo.findById(medicoId)
            .orElseGet(() -> new ChecklistConduta(medicoId));

        checklist.setNumeroConselhoVerificado(req.numeroConselhoVerificado());
        checklist.setRegistrosDisciplinares(req.registrosDisciplinares());
        checklist.setProcessosMedicos(req.processosMedicos());
        if (req.verificadoPor() != null) {
            checklist.setVerificadoPor(req.verificadoPor());
            checklist.setVerificadoEm(OffsetDateTime.now());
        }

        var saved = ChecklistCondutaResponse.from(checklistRepo.save(checklist));
        registrarHistorico(medicoId, TipoAcaoMedico.ATUALIZACAO_CHECKLIST,
            "Checklist de conduta atualizado");
        return saved;
    }

    public List<HistoricoMedicoResponse> listarHistorico(UUID medicoId) {
        findOrThrow(medicoId);
        return historicoRepo.findByMedicoIdOrderByCreatedAtDesc(medicoId).stream()
            .map(HistoricoMedicoResponse::from)
            .toList();
    }

    public List<HistoricoTaxaPinResponse> listarHistoricoTaxaPin(UUID medicoId) {
        findOrThrow(medicoId);
        return historicoTaxaPinRepo.findByMedicoIdOrderByAlteradoEmDesc(medicoId).stream()
            .map(HistoricoTaxaPinResponse::from)
            .toList();
    }

    // ---- Auto-ativação ----

    private void verificarAtivacaoAutomatica(Medico medico) {
        if (medico.getStatus() == StatusMedico.ATIVO) return;

        try {
            validarPreRequisitosAtivacao(medico.getId(), medico);
            medico.setStatus(StatusMedico.ATIVO);
            medicoRepo.save(medico);
            registrarHistorico(medico.getId(), TipoAcaoMedico.ATIVACAO_AUTOMATICA,
                "Médico ativado automaticamente — todos os requisitos de onboarding cumpridos");
            log.info("Médico {} ativado automaticamente", medico.getId());
            liberarAcessoKeycloak(medico);
        } catch (ResponseStatusException e) {
            log.debug("Auto-ativação não disparada para médico {}: {}", medico.getId(), e.getReason());
        }
    }

    // Habilita o usuário Keycloak criado (desabilitado) ao final do auto-cadastro público
    // (EPIC-14.3/14.4) e atribui a role medico. Médicos cadastrados manualmente (sem
    // keycloakUserId) não passam por aqui — o acesso deles continua sendo criado à parte
    // pela tela de Usuários (services/gestao). Falhas aqui são logadas, não bloqueiam a
    // ativação em si — o médico já está corretamente ATIVO no onboarding independente do
    // Keycloak; um operador pode liberar manualmente no Keycloak se isso falhar.
    private void liberarAcessoKeycloak(Medico medico) {
        if (medico.getKeycloakUserId() == null) return;
        try {
            keycloakAdminService.assignRole(medico.getKeycloakUserId(), "medico");
            keycloakAdminService.updateUserEnabled(medico.getKeycloakUserId(), true);
            log.info("Acesso Keycloak liberado para médico {}", medico.getId());
        } catch (Exception e) {
            log.error("Falha ao liberar acesso Keycloak para médico {} (keycloakUserId={}): {}",
                medico.getId(), medico.getKeycloakUserId(), e.getMessage());
        }
    }

    private void validarPreRequisitosAtivacao(UUID id, Medico medico) {
        ChecklistConduta checklist = checklistRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Checklist de conduta não encontrado"));
        if (!checklist.isCompleto()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ativação bloqueada: checklist de conduta incompleto. " +
                "Verifique: número do conselho, registros disciplinares e processos médicos.");
        }

        List<DocumentoMedico> documentos = documentoRepo.findByMedicoId(id);
        if (documentos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ativação bloqueada: nenhum documento foi enviado.");
        }
        boolean todosAprovados = documentos.stream()
            .allMatch(d -> StatusValidacaoDocumento.APROVADO == d.getStatusValidacao());
        if (!todosAprovados) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ativação bloqueada: todos os documentos enviados devem estar aprovados.");
        }

        boolean contratoAssinado = contratoRepo.findTopByMedicoIdOrderByCreatedAtDesc(id)
            .map(c -> "ASSINADO".equals(c.getStatus()))
            .orElse(false);
        if (!contratoAssinado) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ativação bloqueada: contrato de adesão ainda não foi assinado.");
        }

        if (!"APROVADO".equals(medico.getStatusJuntaComercial())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ativação bloqueada: registro na Junta Comercial ainda não aprovado " +
                "(status atual: " + medico.getStatusJuntaComercial() + ").");
        }
    }

    private String mapearStatusClicksign(String eventoNome) {
        return switch (eventoNome != null ? eventoNome.toLowerCase() : "") {
            case "sign", "signed"     -> "ASSINADO";
            case "view", "viewed"     -> "VISUALIZADO";
            case "refuse", "refused"  -> "RECUSADO";
            case "upload", "uploaded" -> "ENVIADO";
            default                   -> "ENVIADO";
        };
    }

    // ---- Helpers ----

    private void registrarHistorico(UUID medicoId, TipoAcaoMedico tipo, String descricao) {
        var h = new HistoricoMedico();
        h.setMedicoId(medicoId);
        h.setTipoAcao(tipo.name());
        h.setDescricao(descricao);
        h.setUsuario(getCurrentUser());
        historicoRepo.save(h);
    }

    private String getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            String email = jwt.getToken().getClaimAsString("email");
            if (email != null) return email;
            String preferred = jwt.getToken().getClaimAsString("preferred_username");
            if (preferred != null) return preferred;
        }
        return auth != null ? auth.getName() : "sistema";
    }

    private Medico findOrThrow(UUID id) {
        return medicoRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Médico não encontrado: " + id));
    }

    private String hashCpf(String cpfDigitsOnly) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(cpfDigitsOnly.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private MedicoResponse toFullResponse(Medico medico) {
        String cpfDecriptografado = cryptoService.decrypt(medico.getCpfCriptografado());

        List<VinculoMedicoEmpresa> vinculos = vinculoRepo.findByIdMedicoId(medico.getId());
        List<java.util.UUID> empresaIds = vinculos.stream()
            .map(v -> v.getId().getEmpresaId()).toList();
        Map<UUID, Empresa> empresaMap = empresaRepo.findAllById(empresaIds).stream()
            .collect(Collectors.toMap(Empresa::getId, e -> e));
        List<VinculoEmpresaResponse> empresas = vinculos.stream()
            .map(v -> {
                Empresa e = empresaMap.get(v.getId().getEmpresaId());
                return e != null ? VinculoEmpresaResponse.from(v, e) : null;
            })
            .filter(java.util.Objects::nonNull)
            .toList();

        DadosBancariosMedicoResponse dadosBancarios = dadosBancariosRepo
            .findByMedicoId(medico.getId())
            .map(d -> DadosBancariosMedicoResponse.from(d,
                d.getChavePIXCriptografada() != null
                    ? cryptoService.decrypt(d.getChavePIXCriptografada()) : null))
            .orElse(null);

        List<DocumentoMedicoResponse> documentos = documentoRepo
            .findByMedicoId(medico.getId()).stream()
            .map(DocumentoMedicoResponse::from)
            .toList();

        ChecklistCondutaResponse checklist = checklistRepo
            .findById(medico.getId())
            .map(ChecklistCondutaResponse::from)
            .orElse(null);

        ContratoAssinaturaResponse contratoAssinatura = contratoRepo
            .findTopByMedicoIdOrderByCreatedAtDesc(medico.getId())
            .map(ContratoAssinaturaResponse::from)
            .orElse(null);

        EnviarConviteResponse ultimoConvite = conviteRepo
            .findTopByMedicoIdOrderByCreatedAtDesc(medico.getId())
            .map(EnviarConviteResponse::from)
            .orElse(null);

        // Presentes apenas para médicos vindos do auto-cadastro público (EPIC-14.1/14.2) —
        // cadastros manuais (origemCadastro = MANUAL) não têm essas linhas.
        DadosCivisMedicoResponse dadosCivis = dadosCivisRepo
            .findById(medico.getId())
            .map(DadosCivisMedicoResponse::from)
            .orElse(null);

        DeclaracaoLgpdResponse declaracoesLgpd = declaracoesLgpdRepo
            .findById(medico.getId())
            .map(DeclaracaoLgpdResponse::from)
            .orElse(null);

        return MedicoResponse.from(medico, cpfDecriptografado, empresas,
            dadosBancarios, documentos, checklist, contratoAssinatura, ultimoConvite,
            dadosCivis, declaracoesLgpd);
    }

    @Transactional
    public List<VinculoEmpresaResponse> listarVinculos(UUID medicoId) {
        findOrThrow(medicoId);
        List<VinculoMedicoEmpresa> vinculos = vinculoRepo.findByIdMedicoId(medicoId);
        List<UUID> empresaIds = vinculos.stream().map(v -> v.getId().getEmpresaId()).toList();
        Map<UUID, Empresa> empresaMap = empresaRepo.findAllById(empresaIds).stream()
            .collect(Collectors.toMap(Empresa::getId, e -> e));
        return vinculos.stream()
            .map(v -> {
                Empresa e = empresaMap.get(v.getId().getEmpresaId());
                return e != null ? VinculoEmpresaResponse.from(v, e) : null;
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    @Transactional
    public VinculoEmpresaResponse adicionarVinculo(UUID medicoId, UUID empresaId) {
        findOrThrow(medicoId);
        Empresa empresa = empresaRepo.findById(empresaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Empresa não encontrada: " + empresaId));
        if (vinculoRepo.existsByIdMedicoIdAndIdEmpresaId(medicoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Médico já está vinculado a esta empresa");
        }
        var vinculo = vinculoRepo.save(new VinculoMedicoEmpresa(
            new VinculoMedicoEmpresaId(medicoId, empresaId), StatusSocietario.ATIVO));
        registrarHistorico(medicoId, TipoAcaoMedico.ATUALIZACAO_DADOS,
            "Vínculo adicionado com empresa: " + empresa.getRazaoSocial());
        return VinculoEmpresaResponse.from(vinculo, empresa);
    }

    @Transactional
    public void removerVinculo(UUID medicoId, UUID empresaId) {
        findOrThrow(medicoId);
        if (!vinculoRepo.existsByIdMedicoIdAndIdEmpresaId(medicoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Vínculo não encontrado para este médico e empresa");
        }
        long total = vinculoRepo.findByIdMedicoId(medicoId).size();
        if (total <= 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é possível remover o único vínculo de empresa do médico");
        }
        vinculoRepo.deleteById(new VinculoMedicoEmpresaId(medicoId, empresaId));
        Empresa empresa = empresaRepo.findById(empresaId).orElse(null);
        registrarHistorico(medicoId, TipoAcaoMedico.ATUALIZACAO_DADOS,
            "Vínculo removido com empresa: " + (empresa != null ? empresa.getRazaoSocial() : empresaId));
    }
}
