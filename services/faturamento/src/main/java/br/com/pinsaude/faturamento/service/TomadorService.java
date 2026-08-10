package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.TipoTomador;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.domain.TomadorAliquota;
import br.com.pinsaude.faturamento.domain.TomadorCnae;
import br.com.pinsaude.faturamento.domain.TomadorGrupoFaturamento;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServico;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import br.com.pinsaude.faturamento.domain.MedicoTomador;
import br.com.pinsaude.faturamento.domain.TomadorEmpresa;
import br.com.pinsaude.faturamento.dto.MedicoTomadorRequest;
import br.com.pinsaude.faturamento.dto.MedicoTomadorResponse;
import br.com.pinsaude.faturamento.dto.ReceitaFederalResponse;
import br.com.pinsaude.faturamento.dto.TomadorEmpresaRequest;
import br.com.pinsaude.faturamento.dto.TomadorEmpresaResponse;
import br.com.pinsaude.faturamento.dto.TomadorAliquotaRequest;
import br.com.pinsaude.faturamento.dto.TomadorAliquotaResponse;
import br.com.pinsaude.faturamento.dto.TomadorCnaeRequest;
import br.com.pinsaude.faturamento.dto.TomadorCnaeResponse;
import br.com.pinsaude.faturamento.dto.TomadorGrupoFaturamentoRequest;
import br.com.pinsaude.faturamento.dto.TomadorGrupoFaturamentoResponse;
import br.com.pinsaude.faturamento.dto.TomadorModalidadeRequest;
import br.com.pinsaude.faturamento.dto.TomadorModalidadeResponse;
import br.com.pinsaude.faturamento.dto.TomadorRequest;
import br.com.pinsaude.faturamento.dto.TomadorResponse;
import br.com.pinsaude.faturamento.dto.TomadorServicoOperacionalRequest;
import br.com.pinsaude.faturamento.dto.TomadorServicoOperacionalResponse;
import br.com.pinsaude.faturamento.dto.TomadorServicoRequest;
import br.com.pinsaude.faturamento.dto.TomadorServicoResponse;
import br.com.pinsaude.faturamento.port.ConsultaCnpjPort;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import br.com.pinsaude.faturamento.repository.TomadorAliquotaRepository;
import br.com.pinsaude.faturamento.repository.TomadorCnaeRepository;
import br.com.pinsaude.faturamento.repository.TomadorEmpresaRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoFaturamentoRepository;
import br.com.pinsaude.faturamento.repository.TomadorModalidadeRepository;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoOperacionalRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TomadorService {

    private final TomadorRepository repo;
    private final CryptoService crypto;
    private final ConsultaCnpjPort consultaCnpjPort;
    private final TomadorAliquotaRepository aliquotaRepo;
    private final TomadorCnaeRepository cnaeRepo;
    private final TomadorServicoRepository servicoVinculoRepo;
    private final ServicoRepository servicoRepo;
    private final TomadorGrupoFaturamentoRepository grupoRepo;
    private final TomadorModalidadeRepository modalidadeRepo;
    private final TomadorServicoOperacionalRepository servicoOperacionalRepo;
    private final MedicoTomadorRepository medicoTomadorRepo;
    private final TomadorEmpresaRepository empresaTomadorRepo;

    public TomadorService(TomadorRepository repo,
                          CryptoService crypto,
                          ConsultaCnpjPort consultaCnpjPort,
                          TomadorAliquotaRepository aliquotaRepo,
                          TomadorCnaeRepository cnaeRepo,
                          TomadorServicoRepository servicoVinculoRepo,
                          ServicoRepository servicoRepo,
                          TomadorGrupoFaturamentoRepository grupoRepo,
                          TomadorModalidadeRepository modalidadeRepo,
                          TomadorServicoOperacionalRepository servicoOperacionalRepo,
                          MedicoTomadorRepository medicoTomadorRepo,
                          TomadorEmpresaRepository empresaTomadorRepo) {
        this.repo = repo;
        this.crypto = crypto;
        this.consultaCnpjPort = consultaCnpjPort;
        this.aliquotaRepo = aliquotaRepo;
        this.cnaeRepo = cnaeRepo;
        this.servicoVinculoRepo = servicoVinculoRepo;
        this.servicoRepo = servicoRepo;
        this.grupoRepo = grupoRepo;
        this.modalidadeRepo = modalidadeRepo;
        this.servicoOperacionalRepo = servicoOperacionalRepo;
        this.medicoTomadorRepo = medicoTomadorRepo;
        this.empresaTomadorRepo = empresaTomadorRepo;
    }

    public List<TomadorResponse> buscar(String q, UUID medicoId) {
        return buscar(q, medicoId, null);
    }

    public List<TomadorResponse> buscar(String q, UUID medicoId, UUID empresaId) {
        List<Tomador> tomadores = buscarPorQ(q);
        if (medicoId != null) {
            Set<UUID> alocados = new HashSet<>(medicoTomadorRepo.findTomadorIdsByMedicoId(medicoId));
            tomadores = tomadores.stream().filter(t -> alocados.contains(t.getId())).toList();
        }
        if (empresaId != null) {
            Set<UUID> vinculados = new HashSet<>(empresaTomadorRepo.findTomadorIdsByEmpresaId(empresaId));
            tomadores = tomadores.stream().filter(t -> vinculados.contains(t.getId())).toList();
        }
        return tomadores.stream().map(this::toResponse).toList();
    }

    private List<Tomador> buscarPorQ(String q) {
        if (q == null || q.isBlank()) {
            return repo.findAll();
        }

        String qDigitos = q.replaceAll("\\D", "");

        if (!qDigitos.isBlank() && qDigitos.length() >= 6 && q.matches("[\\d.\\-/]+")) {
            return repo.findAll().stream()
                .filter(t -> {
                    String dec = crypto.decrypt(t.getCnpjCpfTomadorCriptografado());
                    return dec != null && dec.startsWith(qDigitos);
                })
                .toList();
        }

        return repo.findByRazaoSocialNomeContainingIgnoreCase(q);
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

        String cnpjAtual = crypto.decrypt(t.getCnpjCpfTomadorCriptografado());
        if (!cnpjCpfLimpo.equals(cnpjAtual)) {
            validarDocumentoDuplicado(cnpjCpfLimpo, id);
        }

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

    // ─── Alíquotas por tomador ────────────────────────────────────────────────

    public List<TomadorAliquotaResponse> listarAliquotas(UUID tomadorId) {
        findOrThrow(tomadorId);
        return aliquotaRepo.findByTomadorId(tomadorId).stream()
            .map(TomadorAliquotaResponse::from).toList();
    }

    @Transactional
    public TomadorAliquotaResponse salvarAliquota(UUID tomadorId, TomadorAliquotaRequest req) {
        findOrThrow(tomadorId);
        TomadorAliquota aliquota = aliquotaRepo
            .findByTomadorIdAndTipoTributo(tomadorId, req.tipoTributo())
            .orElseGet(TomadorAliquota::new);
        aliquota.setTomadorId(tomadorId);
        aliquota.setTipoTributo(req.tipoTributo());
        aliquota.setValorAliquota(req.valorAliquota());
        return TomadorAliquotaResponse.from(aliquotaRepo.save(aliquota));
    }

    @Transactional
    public void removerAliquota(UUID tomadorId, UUID aliquotaId) {
        findOrThrow(tomadorId);
        TomadorAliquota aliquota = aliquotaRepo.findById(aliquotaId)
            .filter(a -> tomadorId.equals(a.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alíquota não encontrada"));
        aliquotaRepo.delete(aliquota);
    }

    // ─── CNAEs por tomador ────────────────────────────────────────────────────

    public List<TomadorCnaeResponse> listarCnaes(UUID tomadorId) {
        findOrThrow(tomadorId);
        return cnaeRepo.findByTomadorId(tomadorId).stream()
            .map(TomadorCnaeResponse::from).toList();
    }

    @Transactional
    public TomadorCnaeResponse adicionarCnae(UUID tomadorId, TomadorCnaeRequest req) {
        findOrThrow(tomadorId);
        if (cnaeRepo.existsByTomadorIdAndCodigoCnae(tomadorId, req.codigoCnae())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "CNAE " + req.codigoCnae() + " já cadastrado para este tomador");
        }
        TomadorCnae cnae = new TomadorCnae();
        cnae.setTomadorId(tomadorId);
        cnae.setCodigoCnae(req.codigoCnae());
        cnae.setDescricao(req.descricao());
        return TomadorCnaeResponse.from(cnaeRepo.save(cnae));
    }

    @Transactional
    public void removerCnae(UUID tomadorId, UUID cnaeId) {
        findOrThrow(tomadorId);
        TomadorCnae cnae = cnaeRepo.findById(cnaeId)
            .filter(c -> tomadorId.equals(c.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CNAE não encontrado"));
        cnaeRepo.delete(cnae);
    }

    // ─── Serviços por tomador ─────────────────────────────────────────────────

    public List<TomadorServicoResponse> listarServicos(UUID tomadorId) {
        findOrThrow(tomadorId);
        List<TomadorServico> vinculos = servicoVinculoRepo.findByTomadorId(tomadorId);
        Map<UUID, Servico> servicosPorId = servicosPorId(vinculos);
        return vinculos.stream()
            .map(v -> TomadorServicoResponse.from(v, servicosPorId.get(v.getServicoId())))
            .toList();
    }

    @Transactional
    public TomadorServicoResponse adicionarServico(UUID tomadorId, TomadorServicoRequest req) {
        findOrThrow(tomadorId);
        Servico servico = servicoRepo.findById(req.servicoId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Serviço não encontrado: " + req.servicoId()));
        if (servicoVinculoRepo.existsByTomadorIdAndServicoId(tomadorId, req.servicoId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Serviço " + servico.getCodigoLc116() + " já cadastrado para este tomador");
        }
        TomadorServico vinculo = new TomadorServico();
        vinculo.setTomadorId(tomadorId);
        vinculo.setServicoId(req.servicoId());
        return TomadorServicoResponse.from(servicoVinculoRepo.save(vinculo), servico);
    }

    @Transactional
    public void removerServico(UUID tomadorId, UUID vinculoId) {
        findOrThrow(tomadorId);
        TomadorServico vinculo = servicoVinculoRepo.findById(vinculoId)
            .filter(v -> tomadorId.equals(v.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
        servicoVinculoRepo.delete(vinculo);
    }

    // ─── Grupos de faturamento ────────────────────────────────────────────────

    public List<TomadorGrupoFaturamentoResponse> listarGrupos(UUID tomadorId) {
        findOrThrow(tomadorId);
        List<TomadorGrupoFaturamento> grupos = grupoRepo.findByTomadorIdOrderByOrdemAscNomeAsc(tomadorId);
        Map<UUID, Servico> servicosPorId = servicosPorGrupoIds(grupos);
        return grupos.stream()
            .map(g -> {
                List<TomadorServicoOperacional> setores =
                    servicoOperacionalRepo.findByGrupoIdOrderByNomeAsc(g.getId());
                return TomadorGrupoFaturamentoResponse.from(g, servicosPorId.get(g.getServicoLc116Id()), setores);
            })
            .toList();
    }

    @Transactional
    public TomadorGrupoFaturamentoResponse criarGrupo(UUID tomadorId, TomadorGrupoFaturamentoRequest req) {
        findOrThrow(tomadorId);
        Servico servico = servicoRepo.findById(req.servicoLc116Id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Serviço LC116 não encontrado: " + req.servicoLc116Id()));
        TomadorGrupoFaturamento g = new TomadorGrupoFaturamento();
        g.setTomadorId(tomadorId);
        g.setServicoLc116Id(req.servicoLc116Id());
        g.setNome(req.nome());
        g.setDescricaoNota(req.descricaoNota());
        g.setOrdem(req.ordem());
        g.setAtivo(req.ativo());
        return TomadorGrupoFaturamentoResponse.from(grupoRepo.save(g), servico);
    }

    @Transactional
    public TomadorGrupoFaturamentoResponse atualizarGrupo(UUID tomadorId, UUID grupoId,
                                                           TomadorGrupoFaturamentoRequest req) {
        findOrThrow(tomadorId);
        TomadorGrupoFaturamento g = grupoRepo.findById(grupoId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado"));
        Servico servico = servicoRepo.findById(req.servicoLc116Id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Serviço LC116 não encontrado: " + req.servicoLc116Id()));
        g.setServicoLc116Id(req.servicoLc116Id());
        g.setNome(req.nome());
        g.setDescricaoNota(req.descricaoNota());
        g.setOrdem(req.ordem());
        g.setAtivo(req.ativo());
        List<TomadorServicoOperacional> setores = servicoOperacionalRepo.findByGrupoIdOrderByNomeAsc(grupoId);
        return TomadorGrupoFaturamentoResponse.from(grupoRepo.save(g), servico, setores);
    }

    @Transactional
    public void removerGrupo(UUID tomadorId, UUID grupoId) {
        findOrThrow(tomadorId);
        TomadorGrupoFaturamento g = grupoRepo.findById(grupoId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado"));
        grupoRepo.delete(g);
    }

    // ─── Modalidades ──────────────────────────────────────────────────────────

    public List<TomadorModalidadeResponse> listarModalidades(UUID tomadorId) {
        findOrThrow(tomadorId);
        return modalidadeRepo.findByTomadorIdOrderByNomeAsc(tomadorId).stream()
            .map(TomadorModalidadeResponse::from)
            .toList();
    }

    @Transactional
    public TomadorModalidadeResponse criarModalidade(UUID tomadorId, TomadorModalidadeRequest req) {
        findOrThrow(tomadorId);
        TomadorModalidade m = new TomadorModalidade();
        m.setTomadorId(tomadorId);
        m.setNome(req.nome());
        aplicarCamposPorTipo(m, req);
        m.setValorCentavos(req.valorCentavos());
        m.setDeslocamentoCentavos(req.deslocamentoCentavos());
        m.setAtivo(req.ativo());
        return TomadorModalidadeResponse.from(modalidadeRepo.save(m));
    }

    @Transactional
    public TomadorModalidadeResponse atualizarModalidade(UUID tomadorId, UUID modalidadeId,
                                                          TomadorModalidadeRequest req) {
        findOrThrow(tomadorId);
        TomadorModalidade m = modalidadeRepo.findById(modalidadeId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modalidade não encontrada"));
        m.setNome(req.nome());
        aplicarCamposPorTipo(m, req);
        m.setValorCentavos(req.valorCentavos());
        m.setDeslocamentoCentavos(req.deslocamentoCentavos());
        m.setAtivo(req.ativo());
        return TomadorModalidadeResponse.from(modalidadeRepo.save(m));
    }

    // Modalidade PLANTAO exige só horas (turno e horário são opcionais — ex: "Diária 15h" pode
    // não ter turno nem horário definidos, só a quantidade de horas); MENSAL (valor fixo, ex:
    // "Coordenação de UTI") ignora turno/horário/horas; META (pagamento proporcional por hora/dia,
    // ex: "Diária 40h"/"Evolucionista") ver aplicarCamposMeta. Campos que não pertencem ao tipo
    // são sempre zerados para manter a tabela consistente com o CHECK do banco.
    private void aplicarCamposPorTipo(TomadorModalidade m, TomadorModalidadeRequest req) {
        if ("META".equals(req.tipo())) {
            aplicarCamposMeta(m, req);
            return;
        }
        // PLANTAO / MENSAL: campos de META sempre vazios
        m.setUnidadeCalculo(null);
        m.setMetaHoras(null);
        m.setMetaDias(null);
        if ("MENSAL".equals(req.tipo())) {
            m.setTipo("MENSAL");
            m.setTurno(null);
            m.setHorario(null);
            m.setHoras(null);
            return;
        }
        if (req.horas() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Horas são obrigatórias para modalidade do tipo Plantão");
        }
        m.setTipo("PLANTAO");
        m.setTurno(req.turno() != null && !req.turno().isBlank() ? req.turno() : null);
        m.setHorario(req.horario() != null && !req.horario().isBlank() ? req.horario() : null);
        m.setHoras(req.horas());
    }

    // META: valor pago é proporcional à unidade escolhida (HORA ou DIA). valor_centavos = valor de
    // um bloco completo da meta (ex: R$ 4.000 = valor de 40h). Exige unidade_calculo e a meta da
    // unidade; a outra meta é opcional (validação secundária). turno/horário/horas não se aplicam.
    private void aplicarCamposMeta(TomadorModalidade m, TomadorModalidadeRequest req) {
        String unidade = req.unidadeCalculo();
        if (unidade == null || unidade.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Unidade de cálculo (HORA ou DIA) é obrigatória para modalidade do tipo Meta");
        }
        if ("HORA".equals(unidade) && req.metaHoras() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Meta de horas é obrigatória para modalidade Meta por hora");
        }
        if ("DIA".equals(unidade) && req.metaDias() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Meta de dias é obrigatória para modalidade Meta por dia");
        }
        m.setTipo("META");
        m.setTurno(null);
        m.setHorario(null);
        m.setHoras(null);
        m.setUnidadeCalculo(unidade);
        m.setMetaHoras(req.metaHoras());
        m.setMetaDias(req.metaDias());
    }

    @Transactional
    public void removerModalidade(UUID tomadorId, UUID modalidadeId) {
        findOrThrow(tomadorId);
        TomadorModalidade m = modalidadeRepo.findById(modalidadeId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modalidade não encontrada"));
        modalidadeRepo.delete(m);
    }

    // ─── Serviços operacionais (setores) ──────────────────────────────────────

    public List<TomadorServicoOperacionalResponse> listarServicosOperacionais(UUID tomadorId) {
        findOrThrow(tomadorId);
        return servicoOperacionalRepo.findByTomadorIdOrderByNomeAsc(tomadorId).stream()
            .map(TomadorServicoOperacionalResponse::from)
            .toList();
    }

    @Transactional
    public TomadorServicoOperacionalResponse criarServicoOperacional(UUID tomadorId,
                                                                      TomadorServicoOperacionalRequest req) {
        findOrThrow(tomadorId);
        grupoRepo.findById(req.grupoId())
            .filter(g -> tomadorId.equals(g.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Grupo não encontrado: " + req.grupoId()));
        TomadorServicoOperacional s = new TomadorServicoOperacional();
        s.setTomadorId(tomadorId);
        s.setGrupoId(req.grupoId());
        s.setNome(req.nome());
        s.setAtivo(req.ativo());
        return TomadorServicoOperacionalResponse.from(servicoOperacionalRepo.save(s));
    }

    @Transactional
    public TomadorServicoOperacionalResponse atualizarServicoOperacional(UUID tomadorId,
                                                                          UUID servicoOperacionalId,
                                                                          TomadorServicoOperacionalRequest req) {
        findOrThrow(tomadorId);
        TomadorServicoOperacional s = servicoOperacionalRepo.findById(servicoOperacionalId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Serviço operacional não encontrado"));
        grupoRepo.findById(req.grupoId())
            .filter(g -> tomadorId.equals(g.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Grupo não encontrado: " + req.grupoId()));
        s.setGrupoId(req.grupoId());
        s.setNome(req.nome());
        s.setAtivo(req.ativo());
        return TomadorServicoOperacionalResponse.from(servicoOperacionalRepo.save(s));
    }

    @Transactional
    public void removerServicoOperacional(UUID tomadorId, UUID servicoOperacionalId) {
        findOrThrow(tomadorId);
        TomadorServicoOperacional s = servicoOperacionalRepo.findById(servicoOperacionalId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Serviço operacional não encontrado"));
        servicoOperacionalRepo.delete(s);
    }

    // ─── Médicos alocados ao tomador (EPIC-15) ────────────────────────────────

    public List<MedicoTomadorResponse> listarMedicos(UUID tomadorId) {
        findOrThrow(tomadorId);
        return medicoTomadorRepo.findByTomadorId(tomadorId).stream()
            .map(MedicoTomadorResponse::from)
            .toList();
    }

    @Transactional
    public MedicoTomadorResponse adicionarMedico(UUID tomadorId, MedicoTomadorRequest req) {
        findOrThrow(tomadorId);
        if (medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, req.medicoId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Médico já está alocado a este tomador");
        }
        MedicoTomador mt = new MedicoTomador();
        mt.setTomadorId(tomadorId);
        mt.setMedicoId(req.medicoId());
        return MedicoTomadorResponse.from(medicoTomadorRepo.save(mt));
    }

    @Transactional
    public void removerMedico(UUID tomadorId, UUID medicoId) {
        findOrThrow(tomadorId);
        if (!medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Médico não está alocado a este tomador");
        }
        medicoTomadorRepo.deleteByTomadorIdAndMedicoId(tomadorId, medicoId);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Map<UUID, Servico> servicosPorId(List<TomadorServico> vinculos) {
        List<UUID> ids = vinculos.stream().map(TomadorServico::getServicoId).toList();
        if (ids.isEmpty()) return Map.of();
        return servicoRepo.findAllById(ids).stream()
            .collect(Collectors.toMap(Servico::getId, Function.identity()));
    }

    private Map<UUID, Servico> servicosPorGrupoIds(List<TomadorGrupoFaturamento> grupos) {
        List<UUID> ids = grupos.stream().map(TomadorGrupoFaturamento::getServicoLc116Id).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return servicoRepo.findAllById(ids).stream()
            .collect(Collectors.toMap(Servico::getId, Function.identity()));
    }

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
        List<TomadorAliquotaResponse> aliquotas = aliquotaRepo.findByTomadorId(t.getId()).stream()
            .map(TomadorAliquotaResponse::from).toList();
        List<TomadorCnaeResponse> cnaes = cnaeRepo.findByTomadorId(t.getId()).stream()
            .map(TomadorCnaeResponse::from).toList();
        List<TomadorServico> vinculosServico = servicoVinculoRepo.findByTomadorId(t.getId());
        Map<UUID, Servico> servicosPorId = servicosPorId(vinculosServico);
        List<TomadorServicoResponse> servicos = vinculosServico.stream()
            .map(v -> TomadorServicoResponse.from(v, servicosPorId.get(v.getServicoId())))
            .toList();
        boolean temGrupoFaturamento = grupoRepo.existsByTomadorIdAndAtivoTrue(t.getId());
        List<TomadorEmpresaResponse> empresas = empresaTomadorRepo.findByTomadorId(t.getId()).stream()
            .map(TomadorEmpresaResponse::from).toList();
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
            t.getPais(),
            aliquotas,
            cnaes,
            servicos,
            temGrupoFaturamento,
            empresas
        );
    }

    // ─── Empresas Pin vinculadas ao tomador (PINSAUDE-13.12) ───────────────────

    public List<TomadorEmpresaResponse> listarEmpresas(UUID tomadorId) {
        findOrThrow(tomadorId);
        return empresaTomadorRepo.findByTomadorId(tomadorId).stream()
            .map(TomadorEmpresaResponse::from)
            .toList();
    }

    @Transactional
    public TomadorEmpresaResponse adicionarEmpresa(UUID tomadorId, TomadorEmpresaRequest req) {
        findOrThrow(tomadorId);
        if (empresaTomadorRepo.existsByTomadorIdAndEmpresaId(tomadorId, req.empresaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Empresa já está vinculada a este tomador");
        }
        TomadorEmpresa te = new TomadorEmpresa();
        te.setTomadorId(tomadorId);
        te.setEmpresaId(req.empresaId());
        return TomadorEmpresaResponse.from(empresaTomadorRepo.save(te));
    }

    @Transactional
    public void removerEmpresa(UUID tomadorId, UUID empresaId) {
        findOrThrow(tomadorId);
        if (!empresaTomadorRepo.existsByTomadorIdAndEmpresaId(tomadorId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Empresa não está vinculada a este tomador");
        }
        empresaTomadorRepo.deleteByTomadorIdAndEmpresaId(tomadorId, empresaId);
    }
}
