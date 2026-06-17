package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.*;
import br.com.pinsaude.onboarding.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MedicoService {

    private final MedicoRepository medicoRepo;
    private final VinculoMedicoEmpresaRepository vinculoRepo;
    private final DadosBancariosMedicoRepository dadosBancariosRepo;
    private final DocumentoMedicoRepository documentoRepo;
    private final ChecklistCondutaRepository checklistRepo;
    private final HistoricoMedicoRepository historicoRepo;
    private final CryptoService cryptoService;
    private final StorageService storageService;

    public MedicoService(
            MedicoRepository medicoRepo,
            VinculoMedicoEmpresaRepository vinculoRepo,
            DadosBancariosMedicoRepository dadosBancariosRepo,
            DocumentoMedicoRepository documentoRepo,
            ChecklistCondutaRepository checklistRepo,
            HistoricoMedicoRepository historicoRepo,
            CryptoService cryptoService,
            StorageService storageService) {
        this.medicoRepo = medicoRepo;
        this.vinculoRepo = vinculoRepo;
        this.dadosBancariosRepo = dadosBancariosRepo;
        this.documentoRepo = documentoRepo;
        this.checklistRepo = checklistRepo;
        this.historicoRepo = historicoRepo;
        this.cryptoService = cryptoService;
        this.storageService = storageService;
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
        if (medicoRepo.existsByCrmAndCrmUf(req.crm(), req.crmUf().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe um médico com este CRM para a UF informada");
        }

        var medico = new Medico();
        medico.setCpfCriptografado(cryptoService.encrypt(req.cpf().replaceAll("\\D", "")));
        medico.setNome(req.nome());
        medico.setCrm(req.crm());
        medico.setCrmUf(req.crmUf().toUpperCase());
        medico.setEspecialidade(req.especialidade());
        medico.setEmail(req.email());
        medico.setTelefone(req.telefone());
        medico.setStatus(StatusMedico.RASCUNHO);
        medico = medicoRepo.save(medico);

        var vinculoId = new VinculoMedicoEmpresaId(medico.getId(), req.empresaId());
        vinculoRepo.save(new VinculoMedicoEmpresa(vinculoId, StatusSocietario.ATIVO));

        checklistRepo.save(new ChecklistConduta(medico.getId()));
        registrarHistorico(medico.getId(), TipoAcaoMedico.CADASTRO, "Médico cadastrado no sistema");

        return toFullResponse(medico);
    }

    @Transactional
    public MedicoResponse atualizar(UUID id, MedicoRequest req) {
        Medico medico = findOrThrow(id);

        boolean crmMudou = !medico.getCrm().equals(req.crm())
            || !medico.getCrmUf().equalsIgnoreCase(req.crmUf());
        if (crmMudou && medicoRepo.existsByCrmAndCrmUf(req.crm(), req.crmUf().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe um médico com este CRM para a UF informada");
        }

        medico.setCpfCriptografado(cryptoService.encrypt(req.cpf().replaceAll("\\D", "")));
        medico.setNome(req.nome());
        medico.setCrm(req.crm());
        medico.setCrmUf(req.crmUf().toUpperCase());
        medico.setEspecialidade(req.especialidade());
        medico.setEmail(req.email());
        medico.setTelefone(req.telefone());

        if (req.empresaId() != null && !vinculoRepo.existsByIdMedicoIdAndIdEmpresaId(id, req.empresaId())) {
            vinculoRepo.deleteByIdMedicoId(id);
            vinculoRepo.save(new VinculoMedicoEmpresa(
                new VinculoMedicoEmpresaId(id, req.empresaId()), StatusSocietario.ATIVO));
        }

        var saved = toFullResponse(medicoRepo.save(medico));
        registrarHistorico(id, TipoAcaoMedico.ATUALIZACAO_DADOS, "Dados pessoais atualizados");
        return saved;
    }

    @Transactional
    public MedicoResponse ativar(UUID id) {
        Medico medico = findOrThrow(id);
        ChecklistConduta checklist = checklistRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Checklist de conduta não encontrado"));

        if (!checklist.isCompleto()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ativação bloqueada: checklist de conduta incompleto. " +
                "Verifique: número do conselho, registros disciplinares e processos médicos.");
        }

        List<TipoDocumentoMedico> obrigatorios = List.of(
            TipoDocumentoMedico.CRM, TipoDocumentoMedico.DIPLOMA,
            TipoDocumentoMedico.IDENTIDADE, TipoDocumentoMedico.RESIDENCIA);
        List<DocumentoMedico> documentos = documentoRepo.findByMedicoId(id);
        boolean docsAprovados = obrigatorios.stream().allMatch(tipo ->
            documentos.stream().anyMatch(d ->
                d.getTipo() == tipo && StatusValidacaoDocumento.APROVADO == d.getStatusValidacao()));
        if (!docsAprovados) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ativação bloqueada: todos os documentos obrigatórios (CRM, Diploma, " +
                "Identidade e Comprovante de residência) devem estar aprovados.");
        }

        medico.setStatus(StatusMedico.ATIVO);
        var saved = toFullResponse(medicoRepo.save(medico));
        registrarHistorico(id, TipoAcaoMedico.ATIVACAO, "Médico ativado no sistema");
        return saved;
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
        doc.setStatusValidacao(req.statusValidacao());
        doc.setMotivoReprovacao(StatusValidacaoDocumento.REPROVADO == req.statusValidacao()
            ? req.motivoReprovacao() : null);
        var saved = DocumentoMedicoResponse.from(documentoRepo.save(doc));
        registrarHistorico(medicoId, TipoAcaoMedico.VALIDACAO_DOCUMENTO,
            "Documento " + req.statusValidacao().name() + ": " + doc.getTipo().name());
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

    private MedicoResponse toFullResponse(Medico medico) {
        String cpfDecriptografado = cryptoService.decrypt(medico.getCpfCriptografado());

        UUID empresaId = vinculoRepo.findByIdMedicoId(medico.getId()).stream()
            .findFirst()
            .map(v -> v.getId().getEmpresaId())
            .orElse(null);

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

        return MedicoResponse.from(medico, cpfDecriptografado, empresaId, dadosBancarios, documentos, checklist);
    }
}
