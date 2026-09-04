package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.TipoTomador;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.domain.TomadorAliquota;
import br.com.pinsaude.faturamento.domain.TomadorCnae;
import br.com.pinsaude.faturamento.domain.TomadorGrupoFaturamento;
import br.com.pinsaude.faturamento.domain.TomadorGrupoSetor;
import br.com.pinsaude.faturamento.domain.SetorOperacionalModalidade;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorHorarioPadrao;
import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;
import br.com.pinsaude.faturamento.domain.TomadorServico;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import br.com.pinsaude.faturamento.domain.TipoEscala;
import br.com.pinsaude.faturamento.domain.MedicoTomador;
import br.com.pinsaude.faturamento.domain.MedicoTomadorSetor;
import br.com.pinsaude.faturamento.domain.TomadorEmpresa;
import br.com.pinsaude.faturamento.dto.MedicoTomadorRequest;
import br.com.pinsaude.faturamento.dto.MedicoTomadorResponse;
import br.com.pinsaude.faturamento.dto.MedicoTomadorSetorRequest;
import br.com.pinsaude.faturamento.dto.ModalidadeVinculoResolvido;
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
import br.com.pinsaude.faturamento.dto.TomadorHorarioPadraoRequest;
import br.com.pinsaude.faturamento.dto.TomadorHorarioPadraoResponse;
import br.com.pinsaude.faturamento.dto.TomadorOcorrenciaRequest;
import br.com.pinsaude.faturamento.dto.TomadorOcorrenciaResponse;
import br.com.pinsaude.faturamento.dto.TomadorRequest;
import br.com.pinsaude.faturamento.dto.TomadorResponse;
import br.com.pinsaude.faturamento.dto.TomadorGrupoSetorRequest;
import br.com.pinsaude.faturamento.dto.TomadorServicoOperacionalRequest;
import br.com.pinsaude.faturamento.dto.TomadorServicoOperacionalResponse;
import br.com.pinsaude.faturamento.dto.TomadorServicoRequest;
import br.com.pinsaude.faturamento.dto.TomadorServicoResponse;
import br.com.pinsaude.faturamento.port.ConsultaCnpjPort;
import br.com.pinsaude.faturamento.repository.FrequenciaItemRepository;
import br.com.pinsaude.faturamento.repository.FrequenciaMedicaRepository;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.MedicoTomadorSetorRepository;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import br.com.pinsaude.faturamento.repository.TomadorAliquotaRepository;
import br.com.pinsaude.faturamento.repository.TomadorCnaeRepository;
import br.com.pinsaude.faturamento.repository.TomadorEmpresaRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoFaturamentoRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoSetorRepository;
import br.com.pinsaude.faturamento.repository.SetorOperacionalModalidadeRepository;
import br.com.pinsaude.faturamento.repository.TomadorModalidadeRepository;
import br.com.pinsaude.faturamento.repository.TomadorHorarioPadraoRepository;
import br.com.pinsaude.faturamento.repository.TomadorOcorrenciaRepository;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoOperacionalRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final TomadorGrupoSetorRepository grupoSetorRepo;
    private final SetorOperacionalModalidadeRepository setorModalidadeRepo;
    private final TomadorModalidadeRepository modalidadeRepo;
    private final TomadorServicoOperacionalRepository servicoOperacionalRepo;
    private final MedicoTomadorRepository medicoTomadorRepo;
    private final MedicoTomadorSetorRepository medicoTomadorSetorRepo;
    private final TomadorEmpresaRepository empresaTomadorRepo;
    private final TomadorOcorrenciaRepository ocorrenciaRepo;
    private final TomadorHorarioPadraoRepository horarioPadraoRepo;
    private final FrequenciaMedicaRepository frequenciaMedicaRepo;
    private final FrequenciaItemRepository frequenciaItemRepo;

    public TomadorService(TomadorRepository repo,
                          CryptoService crypto,
                          ConsultaCnpjPort consultaCnpjPort,
                          TomadorAliquotaRepository aliquotaRepo,
                          TomadorCnaeRepository cnaeRepo,
                          TomadorServicoRepository servicoVinculoRepo,
                          ServicoRepository servicoRepo,
                          TomadorGrupoFaturamentoRepository grupoRepo,
                          TomadorGrupoSetorRepository grupoSetorRepo,
                          SetorOperacionalModalidadeRepository setorModalidadeRepo,
                          TomadorModalidadeRepository modalidadeRepo,
                          TomadorServicoOperacionalRepository servicoOperacionalRepo,
                          MedicoTomadorRepository medicoTomadorRepo,
                          MedicoTomadorSetorRepository medicoTomadorSetorRepo,
                          TomadorEmpresaRepository empresaTomadorRepo,
                          TomadorOcorrenciaRepository ocorrenciaRepo,
                          TomadorHorarioPadraoRepository horarioPadraoRepo,
                          FrequenciaMedicaRepository frequenciaMedicaRepo,
                          FrequenciaItemRepository frequenciaItemRepo) {
        this.repo = repo;
        this.crypto = crypto;
        this.consultaCnpjPort = consultaCnpjPort;
        this.aliquotaRepo = aliquotaRepo;
        this.cnaeRepo = cnaeRepo;
        this.servicoVinculoRepo = servicoVinculoRepo;
        this.servicoRepo = servicoRepo;
        this.grupoRepo = grupoRepo;
        this.grupoSetorRepo = grupoSetorRepo;
        this.setorModalidadeRepo = setorModalidadeRepo;
        this.modalidadeRepo = modalidadeRepo;
        this.servicoOperacionalRepo = servicoOperacionalRepo;
        this.medicoTomadorRepo = medicoTomadorRepo;
        this.medicoTomadorSetorRepo = medicoTomadorSetorRepo;
        this.empresaTomadorRepo = empresaTomadorRepo;
        this.horarioPadraoRepo = horarioPadraoRepo;
        this.ocorrenciaRepo = ocorrenciaRepo;
        this.frequenciaMedicaRepo = frequenciaMedicaRepo;
        this.frequenciaItemRepo = frequenciaItemRepo;
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
        t.setExigeFrequencia(Boolean.TRUE.equals(req.exigeFrequencia()));

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
        t.setExigeFrequencia(Boolean.TRUE.equals(req.exigeFrequencia()));

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
        Map<UUID, List<TomadorServicoOperacional>> setoresPorGrupo = setoresPorGrupoIds(
            grupos.stream().map(TomadorGrupoFaturamento::getId).toList());
        Map<UUID, List<ModalidadeVinculoResolvido>> modalidadesPorSetor = modalidadesPorSetorId(
            setoresPorGrupo.values().stream().flatMap(List::stream).toList());
        return grupos.stream()
            .map(g -> TomadorGrupoFaturamentoResponse.from(
                g, servicosPorId.get(g.getServicoLc116Id()), setoresPorGrupo.getOrDefault(g.getId(), List.of()),
                modalidadesPorSetor))
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
        List<TomadorServicoOperacional> setores = setoresPorGrupoIds(List.of(grupoId)).getOrDefault(grupoId, List.of());
        return TomadorGrupoFaturamentoResponse.from(grupoRepo.save(g), servico, setores, modalidadesPorSetorId(setores));
    }

    @Transactional
    public void removerGrupo(UUID tomadorId, UUID grupoId) {
        findOrThrow(tomadorId);
        TomadorGrupoFaturamento g = grupoRepo.findById(grupoId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado"));
        // Checagem precisa por grupo (não mais por setor): o mesmo setor pode estar vinculado a
        // outros grupos sem frequência nenhuma lançada neste aqui — ver PINSAUDE (catálogo de
        // setores reutilizável entre grupos).
        if (frequenciaMedicaRepo.existsByGrupoId(grupoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Não é possível remover: existem frequências médicas lançadas neste grupo.");
        }
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

    // Tipos "fixos" (DIARISTA, EVOLUCIONISTA — ver TipoEscala) exigem horas semanais e ignoram
    // turno/horário/horas (pagam um valor mensal fixo — motor de cálculo em PINSAUDE-13.23).
    // Tipos "por lançamento" (PLANTONISTA, EVOLUCIONISTA_FDS — reaproveita exatamente as mesmas
    // regras do PLANTONISTA, não do DIARISTA/EVOLUCIONISTA, apesar do nome parecido) exigem
    // turno + horário + horas (todos obrigatórios — o preenchimento rápido por tomador,
    // EPIC-13.20, existe justamente para agilizar o preenchimento dos 3 juntos). Tipo "por
    // serviço" (SERVICOS) não exige nenhum campo extra além do que já é comum a toda modalidade
    // (nome/valor, setados fora deste método) — a quantidade é informada por lançamento, não no
    // cadastro. Campos que não pertencem ao tipo são sempre zerados para manter a tabela
    // consistente com o CHECK do banco.
    //
    // Pedido do cliente: uma modalidade pode ter mais de um tipo, desde que todos pertençam à
    // mesma família (fixa, por-lançamento ou por-serviço) — os campos obrigatórios (turno/
    // horário/horas vs horas_semanais vs nenhum) só fazem sentido quando a família é homogênea
    // dentro do array, por isso a checagem de mistura vem antes de qualquer outra validação.
    private void aplicarCamposPorTipo(TomadorModalidade m, TomadorModalidadeRequest req) {
        boolean algumFixo = req.tipos().stream().anyMatch(TipoEscala::isModalidadeFixa);
        boolean algumServico = req.tipos().stream().anyMatch(TipoEscala::isModalidadeServico);
        boolean algumPorLancamento = req.tipos().stream()
            .anyMatch(t -> !TipoEscala.isModalidadeFixa(t) && !TipoEscala.isModalidadeServico(t));
        int familias = (algumFixo ? 1 : 0) + (algumServico ? 1 : 0) + (algumPorLancamento ? 1 : 0);
        if (familias > 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é possível combinar tipos de famílias diferentes na mesma modalidade "
                    + "(ex: Plantonista com Diarista, ou Serviços com qualquer outro tipo) — escolha só tipos com o mesmo formato de campos.");
        }
        String tipoRepresentante = req.tipos().get(0);

        if (algumFixo) {
            if (req.horasSemanais() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Horas semanais são obrigatórias para modalidade do tipo " + TipoEscala.label(tipoRepresentante));
            }
            m.setTipos(req.tipos().toArray(new String[0]));
            m.setTurno(null);
            m.setHorario(null);
            m.setHoras(null);
            m.setHorasSemanais(req.horasSemanais());
            return;
        }
        if (algumServico) {
            m.setTipos(req.tipos().toArray(new String[0]));
            m.setTurno(null);
            m.setHorario(null);
            m.setHoras(null);
            m.setHorasSemanais(null);
            return;
        }
        if (req.turno() == null || req.turno().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Turno é obrigatório para modalidade do tipo " + TipoEscala.label(tipoRepresentante));
        }
        if (req.horario() == null || req.horario().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Horário é obrigatório para modalidade do tipo " + TipoEscala.label(tipoRepresentante));
        }
        if (req.horas() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Horas são obrigatórias para modalidade do tipo " + TipoEscala.label(tipoRepresentante));
        }
        m.setTipos(req.tipos().toArray(new String[0]));
        m.setTurno(req.turno());
        m.setHorario(req.horario());
        m.setHoras(req.horas());
        m.setHorasSemanais(null);
    }

    @Transactional
    public void removerModalidade(UUID tomadorId, UUID modalidadeId) {
        findOrThrow(tomadorId);
        TomadorModalidade m = modalidadeRepo.findById(modalidadeId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modalidade não encontrada"));
        if (frequenciaItemRepo.existsByModalidadeId(modalidadeId)
                || frequenciaMedicaRepo.existsByModalidadeId(modalidadeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Não é possível remover: existem plantões ou frequências lançados com esta modalidade.");
        }
        modalidadeRepo.delete(m);
    }

    // ─── Serviços operacionais (setores) ──────────────────────────────────────
    // Catálogo por tomador (sem grupo próprio, ver PINSAUDE) — o mesmo setor pode ser vinculado
    // a quantos Grupos de Faturamento forem necessários via os métodos *SetorAoGrupo abaixo.

    public List<TomadorServicoOperacionalResponse> listarServicosOperacionais(UUID tomadorId) {
        findOrThrow(tomadorId);
        List<TomadorServicoOperacional> setores = servicoOperacionalRepo.findByTomadorIdOrderByNomeAsc(tomadorId);
        Map<UUID, List<ModalidadeVinculoResolvido>> modalidadesPorSetor = modalidadesPorSetorId(setores);
        return setores.stream()
            .map(s -> TomadorServicoOperacionalResponse.from(s, modalidadesPorSetor.getOrDefault(s.getId(), List.of())))
            .toList();
    }

    @Transactional
    public TomadorServicoOperacionalResponse criarServicoOperacional(UUID tomadorId,
                                                                      TomadorServicoOperacionalRequest req) {
        findOrThrow(tomadorId);
        List<ModalidadeVinculoResolvido> vinculos = resolverModalidadesDoSetor(tomadorId, req.vinculos());
        TomadorServicoOperacional s = new TomadorServicoOperacional();
        s.setTomadorId(tomadorId);
        s.setNome(req.nome());
        s.setCategoria(normalizarCategoria(req.categoria()));
        s.setAtivo(req.ativo());
        TomadorServicoOperacional salvo = servicoOperacionalRepo.save(s);
        salvarVinculosModalidade(salvo.getId(), req.vinculos());
        return TomadorServicoOperacionalResponse.from(salvo, vinculos);
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
        List<ModalidadeVinculoResolvido> vinculos = resolverModalidadesDoSetor(tomadorId, req.vinculos());
        s.setNome(req.nome());
        s.setCategoria(normalizarCategoria(req.categoria()));
        s.setAtivo(req.ativo());
        TomadorServicoOperacional salvo = servicoOperacionalRepo.save(s);
        // Reconstrói o vínculo N:N inteiro a cada PUT — o form envia sempre a lista completa
        // desejada (switches), não deltas; mais simples e sem risco de divergência que diffing.
        //
        // ⚠️ flush() obrigatório entre o delete e o save seguinte: deleteBySetorId é uma query
        // derivada (não @Modifying), então o Hibernate só enfileira a remoção das entidades na
        // persistence context, sem emitir o DELETE na hora. Como a ordem de flush padrão do
        // Hibernate executa INSERTs antes de DELETEs, salvar de novo uma modalidade que já
        // estava vinculada (ex: usuário mantém a mesma + adiciona outra) faz o INSERT da linha
        // nova colidir com o UNIQUE (setor_id, modalidade_id, tipo) da linha antiga que ainda não
        // foi fisicamente removida — 409 "Registro duplicado". flush() força o DELETE a sair antes.
        setorModalidadeRepo.deleteBySetorId(servicoOperacionalId);
        setorModalidadeRepo.flush();
        salvarVinculosModalidade(servicoOperacionalId, req.vinculos());
        return TomadorServicoOperacionalResponse.from(salvo, vinculos);
    }

    // LinkedHashSet: dedup defensivo (o form do frontend, switches, nunca manda duplicata, mas
    // salvar o mesmo par duas vezes violaria o UNIQUE (setor_id, modalidade_id, tipo) da tabela).
    // VinculoModalidade é um record — equals/hashCode por valor, então o dedup funciona por
    // (modalidadeId, tipo), não só por modalidadeId.
    private void salvarVinculosModalidade(UUID setorId, List<TomadorServicoOperacionalRequest.VinculoModalidade> vinculos) {
        for (TomadorServicoOperacionalRequest.VinculoModalidade v : new LinkedHashSet<>(vinculos)) {
            SetorOperacionalModalidade link = new SetorOperacionalModalidade();
            link.setSetorId(setorId);
            link.setModalidadeId(v.modalidadeId());
            link.setTipo(v.tipo());
            setorModalidadeRepo.save(link);
        }
    }

    // Todas as modalidades de referência do setor precisam existir, pertencer ao mesmo tomador e
    // suportar o tipo pedido para aquele vínculo (uma modalidade pode ter mais de um tipo — ver
    // TomadorModalidade.tipos — mas o vínculo com o setor precisa escolher um deles). Mesmo
    // padrão de validação já usado para modalidade fixa da Frequência (FrequenciaService).
    private List<ModalidadeVinculoResolvido> resolverModalidadesDoSetor(
            UUID tomadorId, List<TomadorServicoOperacionalRequest.VinculoModalidade> vinculos) {
        Set<UUID> modalidadeIdsUnicos = vinculos.stream()
            .map(TomadorServicoOperacionalRequest.VinculoModalidade::modalidadeId)
            .collect(Collectors.toSet());
        Map<UUID, TomadorModalidade> modalidadesMap = modalidadeRepo.findAllById(modalidadeIdsUnicos).stream()
            .collect(Collectors.toMap(TomadorModalidade::getId, Function.identity()));
        if (modalidadesMap.size() != modalidadeIdsUnicos.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uma ou mais modalidades não foram encontradas");
        }
        List<ModalidadeVinculoResolvido> resolvidos = new ArrayList<>();
        for (TomadorServicoOperacionalRequest.VinculoModalidade v : new LinkedHashSet<>(vinculos)) {
            TomadorModalidade m = modalidadesMap.get(v.modalidadeId());
            if (!m.getTomadorId().equals(tomadorId)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Modalidade não pertence ao tomador informado: " + m.getId());
            }
            if (!m.suportaTipo(v.tipo())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Modalidade " + m.getNome() + " não suporta o tipo " + TipoEscala.label(v.tipo()));
            }
            resolvidos.add(new ModalidadeVinculoResolvido(m, v.tipo()));
        }
        return resolvidos;
    }

    // Categoria é texto livre e opcional — string em branco vira null pra não poluir o
    // autocomplete de categorias já usadas (derivado no frontend a partir dos setores existentes).
    private String normalizarCategoria(String categoria) {
        return (categoria == null || categoria.isBlank()) ? null : categoria.trim();
    }

    @Transactional
    public void removerServicoOperacional(UUID tomadorId, UUID servicoOperacionalId) {
        findOrThrow(tomadorId);
        TomadorServicoOperacional s = servicoOperacionalRepo.findById(servicoOperacionalId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Serviço operacional não encontrado"));
        if (frequenciaMedicaRepo.existsByServicoOperacionalId(servicoOperacionalId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Não é possível remover: existem frequências médicas lançadas para este setor operacional.");
        }
        servicoOperacionalRepo.delete(s);
    }

    // ─── Vínculo Grupo ↔ Setor (N:N) ───────────────────────────────────────────

    public List<TomadorServicoOperacionalResponse> listarSetoresDoGrupo(UUID tomadorId, UUID grupoId) {
        findOrThrow(tomadorId);
        grupoRepo.findById(grupoId)
            .filter(g -> tomadorId.equals(g.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado"));
        List<TomadorServicoOperacional> setores = setoresPorGrupoIds(List.of(grupoId)).getOrDefault(grupoId, List.of());
        Map<UUID, List<ModalidadeVinculoResolvido>> modalidadesPorSetor = modalidadesPorSetorId(setores);
        return setores.stream()
            .map(s -> TomadorServicoOperacionalResponse.from(s, modalidadesPorSetor.getOrDefault(s.getId(), List.of())))
            .toList();
    }

    @Transactional
    public TomadorServicoOperacionalResponse adicionarSetorAoGrupo(UUID tomadorId, UUID grupoId,
                                                                    TomadorGrupoSetorRequest req) {
        findOrThrow(tomadorId);
        grupoRepo.findById(grupoId)
            .filter(g -> tomadorId.equals(g.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado"));
        TomadorServicoOperacional setor = servicoOperacionalRepo.findById(req.setorId())
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Serviço operacional não encontrado"));
        if (grupoSetorRepo.existsByGrupoIdAndSetorId(grupoId, req.setorId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Setor já está vinculado a este grupo");
        }
        TomadorGrupoSetor link = new TomadorGrupoSetor();
        link.setGrupoId(grupoId);
        link.setSetorId(req.setorId());
        grupoSetorRepo.save(link);
        List<ModalidadeVinculoResolvido> vinculos = modalidadesPorSetorId(List.of(setor)).getOrDefault(setor.getId(), List.of());
        return TomadorServicoOperacionalResponse.from(setor, vinculos);
    }

    @Transactional
    public void removerSetorDoGrupo(UUID tomadorId, UUID grupoId, UUID setorId) {
        findOrThrow(tomadorId);
        grupoRepo.findById(grupoId)
            .filter(g -> tomadorId.equals(g.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado"));
        if (!grupoSetorRepo.existsByGrupoIdAndSetorId(grupoId, setorId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Setor não está vinculado a este grupo");
        }
        if (frequenciaMedicaRepo.existsByGrupoIdAndServicoOperacionalId(grupoId, setorId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Não é possível remover: existem frequências médicas lançadas com este setor neste grupo.");
        }
        grupoSetorRepo.deleteByGrupoIdAndSetorId(grupoId, setorId);
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

    // ─── Setores Operacionais do médico alocado (só quando tomador.exigeFrequencia) ──────────

    public List<TomadorServicoOperacionalResponse> listarSetoresDoMedico(UUID tomadorId, UUID medicoId) {
        findOrThrow(tomadorId);
        MedicoTomador mt = medicoTomadorRepo.findByTomadorIdAndMedicoId(tomadorId, medicoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Médico não está alocado a este tomador"));
        List<MedicoTomadorSetor> vinculos = medicoTomadorSetorRepo.findByMedicoTomadorId(mt.getId());
        if (vinculos.isEmpty()) return List.of();
        Map<UUID, TomadorServicoOperacional> setoresPorId = servicoOperacionalRepo
            .findAllById(vinculos.stream().map(MedicoTomadorSetor::getSetorId).toList()).stream()
            .collect(Collectors.toMap(TomadorServicoOperacional::getId, Function.identity()));
        Map<UUID, List<ModalidadeVinculoResolvido>> modalidadesPorSetor = modalidadesPorSetorId(List.copyOf(setoresPorId.values()));
        return vinculos.stream()
            .map(v -> setoresPorId.get(v.getSetorId()))
            .filter(Objects::nonNull)
            .map(s -> TomadorServicoOperacionalResponse.from(s, modalidadesPorSetor.getOrDefault(s.getId(), List.of())))
            .toList();
    }

    @Transactional
    public TomadorServicoOperacionalResponse adicionarSetorAoMedico(UUID tomadorId, UUID medicoId,
                                                                      MedicoTomadorSetorRequest req) {
        findOrThrow(tomadorId);
        MedicoTomador mt = medicoTomadorRepo.findByTomadorIdAndMedicoId(tomadorId, medicoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Médico não está alocado a este tomador"));
        TomadorServicoOperacional setor = servicoOperacionalRepo.findById(req.setorId())
            .filter(s -> tomadorId.equals(s.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Serviço operacional não encontrado"));
        if (medicoTomadorSetorRepo.existsByMedicoTomadorIdAndSetorId(mt.getId(), req.setorId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Setor já atribuído a este médico");
        }
        MedicoTomadorSetor vinculo = new MedicoTomadorSetor();
        vinculo.setMedicoTomadorId(mt.getId());
        vinculo.setSetorId(req.setorId());
        medicoTomadorSetorRepo.save(vinculo);
        List<ModalidadeVinculoResolvido> vinculos = modalidadesPorSetorId(List.of(setor)).getOrDefault(setor.getId(), List.of());
        return TomadorServicoOperacionalResponse.from(setor, vinculos);
    }

    @Transactional
    public void removerSetorDoMedico(UUID tomadorId, UUID medicoId, UUID setorId) {
        findOrThrow(tomadorId);
        MedicoTomador mt = medicoTomadorRepo.findByTomadorIdAndMedicoId(tomadorId, medicoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Médico não está alocado a este tomador"));
        if (!medicoTomadorSetorRepo.existsByMedicoTomadorIdAndSetorId(mt.getId(), setorId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Setor não está atribuído a este médico");
        }
        medicoTomadorSetorRepo.deleteByMedicoTomadorIdAndSetorId(mt.getId(), setorId);
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

    // Batch: setorId → vínculos (modalidade + tipo resolvido daquele vínculo, nome asc), via
    // setor_operacional_modalidades (N:N — pedido do cliente, um setor pode ter mais de uma
    // modalidade de referência, inclusive a MESMA modalidade 2x sob tipos diferentes quando ela
    // suporta mais de um — ver TomadorModalidade.tipos). Mesmo padrão de setoresPorGrupoIds
    // abaixo: um único par de queries (findBySetorIdIn + findAllById) independente de quantos
    // setores forem enriquecidos de uma vez.
    private Map<UUID, List<ModalidadeVinculoResolvido>> modalidadesPorSetorId(List<TomadorServicoOperacional> setores) {
        List<UUID> setorIds = setores.stream().map(TomadorServicoOperacional::getId).distinct().toList();
        if (setorIds.isEmpty()) return Map.of();
        List<SetorOperacionalModalidade> links = setorModalidadeRepo.findBySetorIdIn(setorIds);
        if (links.isEmpty()) return Map.of();
        Set<UUID> modalidadeIds = links.stream().map(SetorOperacionalModalidade::getModalidadeId).collect(Collectors.toSet());
        Map<UUID, TomadorModalidade> modalidadesMap = modalidadeRepo.findAllById(modalidadeIds).stream()
            .collect(Collectors.toMap(TomadorModalidade::getId, Function.identity()));
        return links.stream()
            .collect(Collectors.groupingBy(SetorOperacionalModalidade::getSetorId,
                Collectors.mapping(l -> {
                    TomadorModalidade m = modalidadesMap.get(l.getModalidadeId());
                    return m != null ? new ModalidadeVinculoResolvido(m, l.getTipo()) : null;
                }, Collectors.toList())))
            .entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(v -> v.modalidade().getNome()))
                .toList()));
    }

    // Batch: grupoId → setores vinculados (nome asc), via tomador_grupo_setores (N:N). Um único
    // par de queries (findByGrupoIdIn + findAllById) independente de quantos grupos forem
    // consultados de uma vez.
    private Map<UUID, List<TomadorServicoOperacional>> setoresPorGrupoIds(List<UUID> grupoIds) {
        if (grupoIds.isEmpty()) return Map.of();
        List<TomadorGrupoSetor> links = grupoSetorRepo.findByGrupoIdIn(grupoIds);
        if (links.isEmpty()) return Map.of();
        Set<UUID> setorIds = links.stream().map(TomadorGrupoSetor::getSetorId).collect(Collectors.toSet());
        Map<UUID, TomadorServicoOperacional> setoresMap = servicoOperacionalRepo.findAllById(setorIds).stream()
            .collect(Collectors.toMap(TomadorServicoOperacional::getId, Function.identity()));
        return links.stream()
            .collect(Collectors.groupingBy(TomadorGrupoSetor::getGrupoId,
                Collectors.mapping(l -> setoresMap.get(l.getSetorId()), Collectors.toList())))
            .entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TomadorServicoOperacional::getNome))
                .toList()));
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
            empresas,
            t.isExigeFrequencia()
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

    // ─── Ocorrências pré-cadastradas com valor (PINSAUDE-13.19.5) ──────────────

    public List<TomadorOcorrenciaResponse> listarOcorrencias(UUID tomadorId) {
        findOrThrow(tomadorId);
        return ocorrenciaRepo.findByTomadorIdOrderByNomeAsc(tomadorId).stream()
            .map(TomadorOcorrenciaResponse::from)
            .toList();
    }

    @Transactional
    public TomadorOcorrenciaResponse criarOcorrencia(UUID tomadorId, TomadorOcorrenciaRequest req) {
        findOrThrow(tomadorId);
        TomadorOcorrencia o = new TomadorOcorrencia();
        o.setTomadorId(tomadorId);
        o.setNome(req.nome());
        aplicarCamposOcorrencia(o, req);
        o.setAtivo(req.ativo());
        return TomadorOcorrenciaResponse.from(ocorrenciaRepo.save(o));
    }

    @Transactional
    public TomadorOcorrenciaResponse atualizarOcorrencia(UUID tomadorId, UUID ocorrenciaId,
                                                          TomadorOcorrenciaRequest req) {
        findOrThrow(tomadorId);
        TomadorOcorrencia o = ocorrenciaRepo.findById(ocorrenciaId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocorrência não encontrada"));
        o.setNome(req.nome());
        aplicarCamposOcorrencia(o, req);
        o.setAtivo(req.ativo());
        return TomadorOcorrenciaResponse.from(ocorrenciaRepo.save(o));
    }

    // SEM_VALOR: os dois campos ficam vazios (texto/observação, sem impacto financeiro).
    // PERCENTUAL: exige valorPercentual; valorCentavos é opcional (permite combinar um extra
    // fixo, ex: "10% + R$ 50,00"). FIXO: exige valorCentavos; valorPercentual é opcional pelo
    // mesmo motivo. O cálculo real (FrequenciaService) sempre soma os dois campos presentes.
    private void aplicarCamposOcorrencia(TomadorOcorrencia o, TomadorOcorrenciaRequest req) {
        if ("SEM_VALOR".equals(req.tipoValor())) {
            o.setTipoValor("SEM_VALOR");
            o.setValorPercentual(null);
            o.setValorCentavos(null);
            return;
        }
        if ("PERCENTUAL".equals(req.tipoValor())) {
            if (req.valorPercentual() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Valor percentual é obrigatório para ocorrência do tipo Percentual");
            }
            o.setTipoValor("PERCENTUAL");
            o.setValorPercentual(req.valorPercentual());
            o.setValorCentavos(req.valorCentavos());
            return;
        }
        if (req.valorCentavos() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Valor fixo é obrigatório para ocorrência do tipo Fixo");
        }
        o.setTipoValor("FIXO");
        o.setValorCentavos(req.valorCentavos());
        o.setValorPercentual(req.valorPercentual());
    }

    @Transactional
    public void removerOcorrencia(UUID tomadorId, UUID ocorrenciaId) {
        findOrThrow(tomadorId);
        TomadorOcorrencia o = ocorrenciaRepo.findById(ocorrenciaId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocorrência não encontrada"));
        if (frequenciaItemRepo.existsByOcorrenciaId(ocorrenciaId)
                || frequenciaMedicaRepo.existsByOcorrenciaId(ocorrenciaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Não é possível remover: existem plantões ou frequências lançados com esta ocorrência.");
        }
        ocorrenciaRepo.delete(o);
    }

    // ─── Preenchimento rápido de turno (PINSAUDE-13.20) ─────────────────────────
    // Substitui o array fixo HORARIOS_FIXOS do frontend por um catálogo por tomador —
    // cada cliente tem seus próprios botões de "Preencher rápido" na modalidade Por Plantão.

    public List<TomadorHorarioPadraoResponse> listarHorariosPadrao(UUID tomadorId) {
        findOrThrow(tomadorId);
        return horarioPadraoRepo.findByTomadorIdOrderByOrdemAsc(tomadorId).stream()
            .map(TomadorHorarioPadraoResponse::from)
            .toList();
    }

    @Transactional
    public TomadorHorarioPadraoResponse criarHorarioPadrao(UUID tomadorId, TomadorHorarioPadraoRequest req) {
        findOrThrow(tomadorId);
        TomadorHorarioPadrao h = new TomadorHorarioPadrao();
        h.setTomadorId(tomadorId);
        h.setTurno(req.turno());
        h.setHoras(req.horas());
        h.setHorario(req.horario().trim());
        h.setOrdem(req.ordem());
        h.setAtivo(req.ativo());
        return TomadorHorarioPadraoResponse.from(horarioPadraoRepo.save(h));
    }

    @Transactional
    public TomadorHorarioPadraoResponse atualizarHorarioPadrao(UUID tomadorId, UUID horarioId,
                                                                TomadorHorarioPadraoRequest req) {
        findOrThrow(tomadorId);
        TomadorHorarioPadrao h = horarioPadraoRepo.findById(horarioId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset de horário não encontrado"));
        h.setTurno(req.turno());
        h.setHoras(req.horas());
        h.setHorario(req.horario().trim());
        h.setOrdem(req.ordem());
        h.setAtivo(req.ativo());
        return TomadorHorarioPadraoResponse.from(horarioPadraoRepo.save(h));
    }

    @Transactional
    public void removerHorarioPadrao(UUID tomadorId, UUID horarioId) {
        findOrThrow(tomadorId);
        TomadorHorarioPadrao h = horarioPadraoRepo.findById(horarioId)
            .filter(x -> tomadorId.equals(x.getTomadorId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preset de horário não encontrado"));
        horarioPadraoRepo.delete(h);
    }
}
