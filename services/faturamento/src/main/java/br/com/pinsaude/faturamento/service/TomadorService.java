package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.TipoTomador;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.dto.ReceitaFederalResponse;
import br.com.pinsaude.faturamento.dto.TomadorRequest;
import br.com.pinsaude.faturamento.dto.TomadorResponse;
import br.com.pinsaude.faturamento.port.ConsultaCnpjPort;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TomadorService {

    private final TomadorRepository repo;
    private final CryptoService crypto;
    private final ConsultaCnpjPort consultaCnpjPort;

    public TomadorService(TomadorRepository repo,
                          CryptoService crypto,
                          ConsultaCnpjPort consultaCnpjPort) {
        this.repo = repo;
        this.crypto = crypto;
        this.consultaCnpjPort = consultaCnpjPort;
    }

    public List<TomadorResponse> buscar(String q) {
        if (q == null || q.isBlank()) {
            return repo.findAll().stream().map(this::toResponse).toList();
        }

        String qDigitos = q.replaceAll("\\D", "");

        // Se a query contém apenas dígitos, pontos, traços e barras → busca por CNPJ/CPF
        if (!qDigitos.isBlank() && qDigitos.length() >= 6 && q.matches("[\\d.\\-/]+")) {
            return repo.findAll().stream()
                .filter(t -> {
                    String dec = crypto.decrypt(t.getCnpjCpfTomadorCriptografado());
                    return dec != null && dec.startsWith(qDigitos);
                })
                .map(this::toResponse)
                .toList();
        }

        return repo.findByRazaoSocialNomeContainingIgnoreCase(q).stream()
            .map(this::toResponse)
            .toList();
    }

    public TomadorResponse buscarPorId(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public TomadorResponse criar(TomadorRequest req) {
        TipoTomador tipo = parseTipo(req.tipo());
        String cnpjCpfLimpo = req.cnpjCpf().replaceAll("\\D", "");
        validarDocumento(tipo, cnpjCpfLimpo);
        validarDocumentoDuplicado(cnpjCpfLimpo, null);

        String tenant = SecurityUtils.currentCnpjTenant();

        Tomador t = new Tomador();
        t.setCnpjIdTenant(tenant != null ? tenant : "");
        t.setTipo(tipo);
        t.setCnpjCpfTomadorCriptografado(crypto.encrypt(cnpjCpfLimpo));
        t.setRazaoSocialNome(req.razaoSocialNome());
        t.setNomeFantasia(req.nomeFantasia());
        t.setMunicipio(req.municipio());
        t.setInscricaoMunicipal(req.inscricaoMunicipal());
        t.setIndicadorRetencaoFederal(req.indicadorRetencaoFederal());
        t.setIndicadorRetencaoIss(req.indicadorRetencaoIss());
        t.setEmail(req.email());
        t.setTelefone(req.telefone());
        t.setLogradouro(req.logradouro());
        t.setBairro(req.bairro());
        t.setCep(req.cep());
        t.setUf(req.uf());
        t.setPais(req.pais());

        return toResponse(repo.save(t));
    }

    @Transactional
    public TomadorResponse atualizar(UUID id, TomadorRequest req) {
        Tomador t = findOrThrow(id);

        TipoTomador tipo = parseTipo(req.tipo());
        String cnpjCpfLimpo = req.cnpjCpf().replaceAll("\\D", "");
        validarDocumento(tipo, cnpjCpfLimpo);
        validarDocumentoDuplicado(cnpjCpfLimpo, id);

        t.setTipo(tipo);
        t.setCnpjCpfTomadorCriptografado(crypto.encrypt(cnpjCpfLimpo));
        t.setRazaoSocialNome(req.razaoSocialNome());
        t.setNomeFantasia(req.nomeFantasia());
        t.setMunicipio(req.municipio());
        t.setInscricaoMunicipal(req.inscricaoMunicipal());
        t.setIndicadorRetencaoFederal(req.indicadorRetencaoFederal());
        t.setIndicadorRetencaoIss(req.indicadorRetencaoIss());
        t.setEmail(req.email());
        t.setTelefone(req.telefone());
        t.setLogradouro(req.logradouro());
        t.setBairro(req.bairro());
        t.setCep(req.cep());
        t.setUf(req.uf());
        t.setPais(req.pais());

        return toResponse(repo.save(t));
    }

    @Transactional
    public void deletar(UUID id) {
        if (!repo.existsById(id)) {
            throw new EntityNotFoundException("Tomador não encontrado: " + id);
        }
        repo.deleteById(id);
    }

    public Optional<ReceitaFederalResponse> consultarReceita(String cnpj) {
        String digits = cnpj.replaceAll("\\D", "");
        if (!CnpjValidator.isValid(digits)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ inválido");
        }
        return consultaCnpjPort.consultar(digits);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void validarDocumentoDuplicado(String cnpjCpfLimpo, UUID idExcluir) {
        boolean existe = repo.findAll().stream()
            .filter(t -> idExcluir == null || !idExcluir.equals(t.getId()))
            .anyMatch(t -> cnpjCpfLimpo.equals(crypto.decrypt(t.getCnpjCpfTomadorCriptografado())));
        if (existe) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe um tomador cadastrado com este CNPJ/CPF");
        }
    }

    private TipoTomador parseTipo(String tipo) {
        try {
            return TipoTomador.valueOf(tipo.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Tipo inválido. Use: HOSPITAL, CLINICA, OPERADORA ou PACIENTE_PF");
        }
    }

    private void validarDocumento(TipoTomador tipo, String digits) {
        if (tipo == TipoTomador.PACIENTE_PF) {
            if (!CpfValidator.isValid(digits)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF inválido");
            }
        } else {
            if (!CnpjValidator.isValid(digits)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ inválido");
            }
        }
    }

    private Tomador findOrThrow(UUID id) {
        return repo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Tomador não encontrado: " + id));
    }

    private TomadorResponse toResponse(Tomador t) {
        String cnpjCpf = crypto.decrypt(t.getCnpjCpfTomadorCriptografado());
        return new TomadorResponse(
            t.getId(),
            t.getTipo().name(),
            cnpjCpf,
            t.getRazaoSocialNome(),
            t.getNomeFantasia(),
            t.getMunicipio(),
            t.getInscricaoMunicipal(),
            t.isIndicadorRetencaoFederal(),
            t.isIndicadorRetencaoIss(),
            t.getEmail(),
            t.getTelefone(),
            t.getLogradouro(),
            t.getBairro(),
            t.getCep(),
            t.getUf(),
            t.getPais()
        );
    }
}
