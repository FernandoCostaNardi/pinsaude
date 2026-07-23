package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.CandidaturaPublicaRequest;
import br.com.pinsaude.onboarding.dto.CandidaturaPublicaResponse;
import br.com.pinsaude.onboarding.repository.DadosCivisMedicoRepository;
import br.com.pinsaude.onboarding.repository.HistoricoMedicoRepository;
import br.com.pinsaude.onboarding.repository.MedicoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    private final MedicoRepository medicoRepo;
    private final DadosCivisMedicoRepository dadosCivisRepo;
    private final HistoricoMedicoRepository historicoRepo;
    private final CryptoService cryptoService;

    public CadastroPublicoService(
            MedicoRepository medicoRepo,
            DadosCivisMedicoRepository dadosCivisRepo,
            HistoricoMedicoRepository historicoRepo,
            CryptoService cryptoService) {
        this.medicoRepo = medicoRepo;
        this.dadosCivisRepo = dadosCivisRepo;
        this.historicoRepo = historicoRepo;
        this.cryptoService = cryptoService;
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

    // ---- Helpers ----

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
        var h = new HistoricoMedico();
        h.setMedicoId(medicoId);
        h.setTipoAcao(TipoAcaoMedico.CADASTRO.name());
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
