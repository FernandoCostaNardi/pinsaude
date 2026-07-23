package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.*;
import br.com.pinsaude.onboarding.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Serviço do fluxo público de auto-cadastro de médico (EPIC-14.2) — endpoints sem
 * autenticação, separado de MedicoService (que assume sempre um operador/gestão logado).
 * Só enxerga/edita médicos com origemCadastro = AUTO_CADASTRO, nunca cadastros manuais.
 */
@Service
public class CadastroPublicoService {

    private static final String USUARIO_SISTEMA = "auto-cadastro-publico";
    private static final String CANAL_INDICACAO = "Indicação";

    // Documentos obrigatórios para finalizar a candidatura — os demais tipos (RQE,
    // certidão de casamento, títulos/especialidades, residência) são opcionais e
    // validados apenas na triagem manual pelo operador (ver notas do EPIC-14.3).
    private static final List<TipoDocumentoMedico> DOCUMENTOS_OBRIGATORIOS =
        List.of(TipoDocumentoMedico.CRM, TipoDocumentoMedico.COMPROVANTE_ENDERECO);

    private final MedicoRepository medicoRepo;
    private final DadosCivisMedicoRepository dadosCivisRepo;
    private final DadosBancariosMedicoRepository dadosBancariosRepo;
    private final DocumentoMedicoRepository documentoRepo;
    private final DeclaracoesLgpdMedicoRepository declaracoesLgpdRepo;
    private final HistoricoMedicoRepository historicoRepo;
    private final CryptoService cryptoService;
    private final StorageService storageService;
    private final NotificacaoService notificacaoService;
    private final KeycloakAdminService keycloakAdminService;

    public CadastroPublicoService(
            MedicoRepository medicoRepo,
            DadosCivisMedicoRepository dadosCivisRepo,
            DadosBancariosMedicoRepository dadosBancariosRepo,
            DocumentoMedicoRepository documentoRepo,
            DeclaracoesLgpdMedicoRepository declaracoesLgpdRepo,
            HistoricoMedicoRepository historicoRepo,
            CryptoService cryptoService,
            StorageService storageService,
            NotificacaoService notificacaoService,
            KeycloakAdminService keycloakAdminService) {
        this.medicoRepo = medicoRepo;
        this.dadosCivisRepo = dadosCivisRepo;
        this.dadosBancariosRepo = dadosBancariosRepo;
        this.documentoRepo = documentoRepo;
        this.declaracoesLgpdRepo = declaracoesLgpdRepo;
        this.historicoRepo = historicoRepo;
        this.cryptoService = cryptoService;
        this.storageService = storageService;
        this.notificacaoService = notificacaoService;
        this.keycloakAdminService = keycloakAdminService;
    }

    @Transactional
    public CandidaturaPublicaResponse criar(CandidaturaPublicaRequest req) {
        String cpfNormalizado = req.cpf().replaceAll("\\D", "");
        String cpfHash = hashCpf(cpfNormalizado);
        validarDuplicidade(cpfHash, req.crm(), req.crmUf(), null);

        var medico = new Medico();
        medico.setCpfCriptografado(cryptoService.encrypt(cpfNormalizado));
        medico.setCpfHash(cpfHash);
        medico.setNome(req.nome());
        medico.setCrm(req.crm());
        medico.setCrmUf(req.crmUf().toUpperCase());
        medico.setEmail(req.email());
        medico.setTelefone(req.telefone());
        medico.setStatus(StatusMedico.RASCUNHO);
        medico.setOrigemCadastro("AUTO_CADASTRO");
        medico = medicoRepo.save(medico);

        var dadosCivis = new DadosCivisMedico(medico.getId());
        aplicarDadosCivis(dadosCivis, req);
        dadosCivis = dadosCivisRepo.save(dadosCivis);

        registrarHistorico(medico.getId(), "Candidatura recebida via formulário público de auto-cadastro");

        return CandidaturaPublicaResponse.from(medico, cpfNormalizado, dadosCivis);
    }

    @Transactional
    public CandidaturaPublicaResponse atualizar(UUID id, CandidaturaPublicaRequest req) {
        Medico medico = findEditavelOrThrow(id);

        String cpfNormalizado = req.cpf().replaceAll("\\D", "");
        String cpfHash = hashCpf(cpfNormalizado);
        validarDuplicidade(cpfHash, req.crm(), req.crmUf(), medico);

        medico.setCpfCriptografado(cryptoService.encrypt(cpfNormalizado));
        medico.setCpfHash(cpfHash);
        medico.setNome(req.nome());
        medico.setCrm(req.crm());
        medico.setCrmUf(req.crmUf().toUpperCase());
        medico.setEmail(req.email());
        medico.setTelefone(req.telefone());
        medico = medicoRepo.save(medico);

        var dadosCivis = dadosCivisRepo.findById(id).orElseGet(() -> new DadosCivisMedico(id));
        aplicarDadosCivis(dadosCivis, req);
        dadosCivis = dadosCivisRepo.save(dadosCivis);

        registrarHistorico(id, "Dados da candidatura atualizados pelo próprio médico");

        return CandidaturaPublicaResponse.from(medico, cpfNormalizado, dadosCivis);
    }

    public CandidaturaPublicaResponse buscar(UUID id) {
        Medico medico = findAutoCadastroOrThrow(id);
        DadosCivisMedico dadosCivis = dadosCivisRepo.findById(id).orElseGet(() -> new DadosCivisMedico(id));
        String cpf = cryptoService.decrypt(medico.getCpfCriptografado());
        return CandidaturaPublicaResponse.from(medico, cpf, dadosCivis);
    }

    @Transactional
    public DocumentoMedicoResponse uploadDocumento(UUID id, TipoDocumentoMedico tipo, MultipartFile arquivo) {
        findEditavelOrThrow(id);
        if (arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo não pode estar vazio");
        }

        // Sem limite de quantidade por tipo — decisão explícita do usuário (ver EPIC-14
        // no plano): o médico pode enviar quantos títulos/documentos quiser.
        String caminho = storageService.upload(id, tipo.name(), arquivo);

        var doc = new DocumentoMedico();
        doc.setMedicoId(id);
        doc.setTipo(tipo);
        doc.setNomeArquivo(arquivo.getOriginalFilename());
        doc.setCaminhoStorage(caminho);
        doc.setStatusValidacao(StatusValidacaoDocumento.PENDENTE);
        doc = documentoRepo.save(doc);

        registrarHistorico(id, TipoAcaoMedico.UPLOAD_DOCUMENTO,
            "Documento enviado pelo próprio médico: " + tipo.name());

        return DocumentoMedicoResponse.from(doc);
    }

    @Transactional
    public DadosBancariosMedicoResponse atualizarDadosBancarios(UUID id, CandidaturaDadosBancariosRequest req) {
        findEditavelOrThrow(id);

        var dados = dadosBancariosRepo.findByMedicoId(id)
            .orElseGet(() -> {
                var novo = new DadosBancariosMedico();
                novo.setMedicoId(id);
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

        registrarHistorico(id, TipoAcaoMedico.ATUALIZACAO_DADOS_BANCARIOS,
            "Dados bancários informados pelo próprio médico");

        String chavePIXDecriptografada = dados.getChavePIXCriptografada() != null
            ? cryptoService.decrypt(dados.getChavePIXCriptografada()) : null;
        return DadosBancariosMedicoResponse.from(dados, chavePIXDecriptografada);
    }

    @Transactional
    public DeclaracaoLgpdResponse registrarDeclaracaoLgpd(UUID id, DeclaracaoLgpdRequest req, String ipOrigem) {
        findEditavelOrThrow(id);

        var declaracao = declaracoesLgpdRepo.findById(id).orElseGet(() -> new DeclaracoesLgpdMedico(id));
        declaracao.setAceiteDeclaracaoVeracidade(req.aceiteDeclaracaoVeracidade());
        declaracao.setAutorizacaoUsoDados(req.autorizacaoUsoDados());
        declaracao.setAutorizacaoCompartilhamento(req.autorizacaoCompartilhamento());
        declaracao.setAvisoPrivacidadeLido(req.avisoPrivacidadeLido());
        declaracao.setAssinaturaNome(req.assinaturaNome());
        declaracao.setAssinadoEm(OffsetDateTime.now());
        declaracao.setIpOrigem(ipOrigem);
        declaracao = declaracoesLgpdRepo.save(declaracao);

        registrarHistorico(id, TipoAcaoMedico.ATUALIZACAO_DADOS,
            "Declarações LGPD e assinatura eletrônica registradas pelo próprio médico");

        return DeclaracaoLgpdResponse.from(declaracao);
    }

    @Transactional
    public FinalizarCandidaturaResponse finalizar(UUID id) {
        Medico medico = findEditavelOrThrow(id);
        validarCompletudeParaFinalizar(id);

        // Idempotente: se finalizar() for chamado de novo (retry de rede), não cria um
        // segundo usuário Keycloak — só tenta de novo se a chamada anterior falhou (o que
        // deixaria keycloakUserId ainda nulo, já que só persistimos após sucesso).
        if (medico.getKeycloakUserId() == null) {
            String keycloakUserId = criarUsuarioKeycloakDesabilitado(medico);
            medico.setKeycloakUserId(keycloakUserId);
            medico = medicoRepo.save(medico);
        }

        registrarHistorico(id, TipoAcaoMedico.CANDIDATURA_FINALIZADA,
            "Candidatura finalizada e enviada para triagem");
        notificacaoService.notificarCandidaturaRecebida(medico);

        return new FinalizarCandidaturaResponse(id, medico.getStatus().name(),
            "Candidatura recebida com sucesso! Você receberá um e-mail assim que a análise for concluída.");
    }

    // ---- Helpers ----

    private String criarUsuarioKeycloakDesabilitado(Medico medico) {
        try {
            return keycloakAdminService.createUserDesabilitado(medico.getEmail(), medico.getNome(), null);
        } catch (Exception e) {
            // Falha aqui não deve deixar o Medico num estado inconsistente: como só
            // persistimos keycloakUserId DEPOIS do sucesso, e nada mais foi salvo ainda
            // nesta chamada (historico/e-mail vêm depois), a transação não tem nada para
            // desfazer — o médico continua RASCUNHO e editável, pronto para tentar de novo.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Não foi possível concluir o cadastro no momento (falha ao criar acesso). " +
                "Tente novamente em alguns minutos ou contate falecom@pinsaude.com.br: " + e.getMessage());
        }
    }

    private void validarCompletudeParaFinalizar(UUID id) {
        List<String> pendencias = new ArrayList<>();

        List<TipoDocumentoMedico> tiposEnviados = documentoRepo.findByMedicoId(id).stream()
            .map(DocumentoMedico::getTipo)
            .toList();
        for (TipoDocumentoMedico obrigatorio : DOCUMENTOS_OBRIGATORIOS) {
            if (!tiposEnviados.contains(obrigatorio)) {
                pendencias.add("documento " + obrigatorio.name());
            }
        }

        boolean lgpdCompleta = declaracoesLgpdRepo.findById(id)
            .map(DeclaracoesLgpdMedico::isCompleto)
            .orElse(false);
        if (!lgpdCompleta) {
            pendencias.add("declarações LGPD e assinatura eletrônica");
        }

        if (!pendencias.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é possível finalizar a candidatura — pendências: " + String.join(", ", pendencias));
        }
    }

    private void validarDuplicidade(String cpfHash, String crm, String crmUf, Medico medicoAtual) {
        boolean cpfMudou = medicoAtual == null || !cpfHash.equals(medicoAtual.getCpfHash());
        if (cpfMudou && medicoRepo.existsByCpfHash(cpfHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe uma candidatura ou cadastro com este CPF. " +
                "Entre em contato com falecom@pinsaude.com.br para mais informações.");
        }
        boolean crmMudou = medicoAtual == null
            || !medicoAtual.getCrm().equals(crm)
            || !medicoAtual.getCrmUf().equalsIgnoreCase(crmUf);
        if (crmMudou && medicoRepo.existsByCrmAndCrmUf(crm, crmUf.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe uma candidatura ou cadastro com este CRM/UF. " +
                "Entre em contato com falecom@pinsaude.com.br para mais informações.");
        }
    }

    private void aplicarDadosCivis(DadosCivisMedico dc, CandidaturaPublicaRequest req) {
        dc.setDataNascimento(req.dataNascimento());
        dc.setNacionalidade(req.nacionalidade());
        dc.setNaturalidade(req.naturalidade());
        dc.setEstadoCivil(req.estadoCivil());
        dc.setNomeMae(req.nomeMae());
        dc.setNomePai(req.nomePai());
        dc.setLogradouro(req.logradouro());
        dc.setNumero(req.numero());
        dc.setComplemento(req.complemento());
        dc.setBairro(req.bairro());
        dc.setCidade(req.cidade());
        dc.setUf(req.uf() != null ? req.uf().toUpperCase() : null);
        dc.setCep(req.cep());
        dc.setRgNumero(req.rgNumero());
        dc.setRgOrgaoExpedidor(req.rgOrgaoExpedidor());
        dc.setRgUf(req.rgUf() != null ? req.rgUf().toUpperCase() : null);
        dc.setRqe(req.rqe());
        dc.setCanalOrigem(req.canalOrigem());
        // nome_indicador só é preenchido quando o canal escolhido for "Indicação" (ver EPIC-14 plano).
        dc.setNomeIndicador(CANAL_INDICACAO.equalsIgnoreCase(req.canalOrigem()) ? req.nomeIndicador() : null);
        dc.setSituacaoFormacao(req.situacaoFormacao() != null
            ? req.situacaoFormacao().toArray(new String[0]) : null);
        dc.setAreasAtuacao(req.areasAtuacao());
        dc.setProcedimentosRealiza(req.procedimentosRealiza());
    }

    private Medico findAutoCadastroOrThrow(UUID id) {
        Medico medico = medicoRepo.findById(id)
            .filter(m -> "AUTO_CADASTRO".equals(m.getOrigemCadastro()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Candidatura não encontrada: " + id));
        return medico;
    }

    private Medico findEditavelOrThrow(UUID id) {
        Medico medico = findAutoCadastroOrThrow(id);
        if (medico.getStatus() != StatusMedico.RASCUNHO) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Esta candidatura já está em análise/aprovada e não pode mais ser editada por aqui. " +
                "Entre em contato com falecom@pinsaude.com.br para alterações.");
        }
        return medico;
    }

    private void registrarHistorico(UUID medicoId, String descricao) {
        registrarHistorico(medicoId, TipoAcaoMedico.CADASTRO, descricao);
    }

    private void registrarHistorico(UUID medicoId, TipoAcaoMedico tipo, String descricao) {
        var h = new HistoricoMedico();
        h.setMedicoId(medicoId);
        h.setTipoAcao(tipo.name());
        h.setDescricao(descricao);
        h.setUsuario(USUARIO_SISTEMA);
        historicoRepo.save(h);
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
}
