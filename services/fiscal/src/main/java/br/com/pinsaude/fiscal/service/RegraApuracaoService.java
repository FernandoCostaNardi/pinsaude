package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.domain.*;
import br.com.pinsaude.fiscal.dto.RegraApuracaoRequest;
import br.com.pinsaude.fiscal.dto.RegraApuracaoResponse;
import br.com.pinsaude.fiscal.dto.TributoRegraApuracaoRequest;
import br.com.pinsaude.fiscal.repository.RegraApuracaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * APU-00 — cadastro das regras de apuração (versionadas por competência, rascunho → homologada).
 *
 * <p>Escopo: usuário com cnpj_id no token só vê e altera as regras da sua empresa e lê o padrão
 * global; quem não tem cnpj_id (gestão) altera qualquer uma e é o único que mexe no padrão global.
 * Editar uma regra homologada volta ela para rascunho (como a chave CBS/IBS, CBS-14).
 */
@Service
public class RegraApuracaoService {

    private final RegraApuracaoRepository repo;

    public RegraApuracaoService(RegraApuracaoRepository repo) {
        this.repo = repo;
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    /** Regras visíveis: as da empresa (token, ou {@code cnpj} para a gestão) e as globais. */
    @Transactional(readOnly = true)
    public List<RegraApuracaoResponse> listar(String cnpjInformado) {
        String cnpj = empresaDoToken().orElseGet(() -> normalizar(cnpjInformado));
        List<RegraApuracao> lista = cnpj == null
            ? repo.findAllByOrderByCompetenciaInicioDesc()
            : repo.listarDaEmpresaEGlobais(cnpj);
        return lista.stream().map(RegraApuracaoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RegraApuracaoResponse buscar(UUID id) {
        return RegraApuracaoResponse.from(findVisivel(id));
    }

    /** Regra que vale para a empresa na competência: a própria, senão o padrão global. */
    @Transactional(readOnly = true)
    public RegraApuracaoResponse vigente(String competencia, String cnpjInformado) {
        String cnpj = empresaDoToken().orElseGet(() -> normalizar(cnpjInformado));
        return RegraApuracaoResponse.from(regraVigente(cnpj, validarCompetencia(competencia)));
    }

    /** Entidade da regra vigente (usada pelo simulador e, depois, pelo motor da APU-01). */
    @Transactional(readOnly = true)
    public RegraApuracao regraVigente(String cnpj, String competencia) {
        Optional<RegraApuracao> daEmpresa = cnpj == null ? Optional.empty()
            : repo.findFirstByCnpjIdAndCompetenciaInicioLessThanEqualOrderByCompetenciaInicioDesc(cnpj, competencia);
        RegraApuracao regra = daEmpresa.or(() ->
                repo.findFirstByCnpjIdIsNullAndCompetenciaInicioLessThanEqualOrderByCompetenciaInicioDesc(competencia))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Nenhuma regra de apuração vigente em " + competencia + ". Cadastre uma regra a partir dessa competência."));
        regra.getTributos().size(); // carrega os tributos dentro da transação
        return regra;
    }

    /** Regra pelo id, respeitando o escopo do usuário (para simular um rascunho). */
    @Transactional(readOnly = true)
    public RegraApuracao regraPorId(UUID id) {
        RegraApuracao r = findVisivel(id);
        r.getTributos().size();
        return r;
    }

    // ─── Escrita ──────────────────────────────────────────────────────────────

    @Transactional
    public RegraApuracaoResponse criar(RegraApuracaoRequest req) {
        String cnpj = escopoDeEscrita(req.cnpj());
        String comp = validarCompetencia(req.competenciaInicio());
        if (existe(cnpj, comp)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe regra de apuração " + (cnpj == null ? "global" : "da empresa") + " a partir de " + comp
                + ". Edite a existente ou escolha outra competência.");
        }
        RegraApuracao r = new RegraApuracao();
        r.setCnpjId(cnpj);
        aplicar(r, req, comp);
        return RegraApuracaoResponse.from(repo.saveAndFlush(r));
    }

    @Transactional
    public RegraApuracaoResponse atualizar(UUID id, RegraApuracaoRequest req) {
        RegraApuracao r = findParaEscrita(id);
        String comp = validarCompetencia(req.competenciaInicio());
        if (!comp.equals(r.getCompetenciaInicio()) && existe(r.getCnpjId(), comp)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe outra regra de apuração no mesmo escopo a partir de " + comp);
        }
        aplicar(r, req, comp);
        limparHomologacao(r);
        return RegraApuracaoResponse.from(repo.saveAndFlush(r));
    }

    @Transactional
    public RegraApuracaoResponse homologar(UUID id) {
        RegraApuracao r = findParaEscrita(id);
        if (r.isHomologado()) return RegraApuracaoResponse.from(r);
        r.setHomologado(true);
        r.setHomologadoPor(SecurityUtils.currentUsuario());
        r.setHomologadoEm(OffsetDateTime.now());
        return RegraApuracaoResponse.from(repo.saveAndFlush(r));
    }

    /** Exclui um rascunho. Regra homologada não se exclui: crie uma nova vigência. */
    @Transactional
    public void excluir(UUID id) {
        RegraApuracao r = findParaEscrita(id);
        if (r.isHomologado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Regra homologada não pode ser excluída. Cadastre uma nova regra a partir da próxima competência.");
        }
        repo.delete(r);
    }

    // ─── Validação e montagem ─────────────────────────────────────────────────

    private void aplicar(RegraApuracao r, RegraApuracaoRequest req, String comp) {
        validarTributos(req.tributos());
        r.setCompetenciaInicio(comp);
        r.setPresuncaoIrpjEquiparado(req.presuncaoIrpjEquiparado());
        r.setPresuncaoCsllEquiparado(req.presuncaoCsllEquiparado());
        r.setPresuncaoIrpjPadrao(req.presuncaoIrpjPadrao());
        r.setPresuncaoCsllPadrao(req.presuncaoCsllPadrao());
        r.setAliqAdicionalIrpj(req.aliqAdicionalIrpj());
        r.setLimiteAdicionalIrpjMensal(req.limiteAdicionalIrpjMensal());
        r.setTetoReceitaAnual(req.tetoReceitaAnual());
        r.setAlertaTetoPercentual(req.alertaTetoPercentual());
        r.setValorMinimoGuia(req.valorMinimoGuia());
        r.setFonte(req.fonte());
        r.setObservacoes(req.observacoes());
        r.setAtualizadoPor(SecurityUtils.currentUsuario());
        r.substituirTributos(req.tributos().stream().map(RegraApuracaoService::tributo).toList());
    }

    private static RegraApuracaoTributo tributo(TributoRegraApuracaoRequest t) {
        RegraApuracaoTributo e = new RegraApuracaoTributo();
        e.setTributo(t.tributo());
        e.setAtivo(t.ativo());
        e.setAplicaEm(t.aplicaEm());
        e.setAliquota(t.aliquota());
        e.setBaseCalculo(t.baseCalculo());
        e.setRegimeReconhecimento(t.regimeReconhecimento());
        e.setPeriodicidade(t.periodicidade());
        e.setDeduzRetencao(t.deduzRetencao());
        e.setCodigoReceita(blankToNull(t.codigoReceita()));
        e.setGuia(blankToNull(t.guia()));
        e.setVencimentoDia(t.vencimentoDia() == null ? null : t.vencimentoDia().shortValue());
        e.setVencimentoMesesApos(t.vencimentoMesesApos() == null ? null : t.vencimentoMesesApos().shortValue());
        e.setVencimentoAjuste(t.vencimentoAjuste() == null ? AjusteVencimento.NENHUM : t.vencimentoAjuste());
        return e;
    }

    /** Regras de consistência entre os campos de cada tributo (422). */
    static void validarTributos(List<TributoRegraApuracaoRequest> tributos) {
        Set<TributoApuracao> vistos = EnumSet.noneOf(TributoApuracao.class);
        for (TributoRegraApuracaoRequest t : tributos) {
            TributoApuracao trib = t.tributo();
            if (!vistos.add(trib)) erro(trib + " aparece mais de uma vez na regra");
            if (t.baseCalculo() == BaseCalculoApuracao.BASE_PRESUMIDA && !trib.isBasePresumidaPermitida()) {
                erro(trib + ": base presumida só vale para IRPJ e CSLL");
            }
            if (t.baseCalculo() == BaseCalculoApuracao.SOMA_NOTAS && !trib.isReforma()) {
                erro(trib + ": soma das notas só vale para CBS e IBS");
            }
            if (t.aliquota() == null && t.baseCalculo() != BaseCalculoApuracao.SOMA_NOTAS && !trib.isReforma()) {
                erro(trib + ": informe a alíquota (só CBS/IBS podem usar a do cronograma da reforma)");
            }
            if ((t.vencimentoDia() == null) != (t.vencimentoMesesApos() == null)) {
                erro(trib + ": informe o dia e os meses após o período do vencimento, ou nenhum dos dois");
            }
        }
    }

    private static void erro(String msg) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }

    private boolean existe(String cnpj, String comp) {
        return cnpj == null
            ? repo.existsByCnpjIdIsNullAndCompetenciaInicio(comp)
            : repo.existsByCnpjIdAndCompetenciaInicio(cnpj, comp);
    }

    private static void limparHomologacao(RegraApuracao r) {
        r.setHomologado(false);
        r.setHomologadoPor(null);
        r.setHomologadoEm(null);
    }

    // ─── Escopo ───────────────────────────────────────────────────────────────

    /** Empresa da regra a criar: a do token; sem token, a informada (vazia = global). */
    private String escopoDeEscrita(String cnpjInformado) {
        return empresaDoToken().orElseGet(() -> {
            String c = normalizar(cnpjInformado);
            if (c != null && c.length() != 14) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ da empresa inválido");
            }
            return c;
        });
    }

    private RegraApuracao findVisivel(UUID id) {
        RegraApuracao r = repo.findById(id).orElseThrow(() -> naoEncontrada(id));
        Optional<String> tenant = empresaDoToken();
        if (tenant.isPresent() && !r.isGlobal() && !tenant.get().equals(r.getCnpjId())) throw naoEncontrada(id);
        return r;
    }

    private RegraApuracao findParaEscrita(UUID id) {
        RegraApuracao r = findVisivel(id);
        if (empresaDoToken().isPresent() && r.isGlobal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "O padrão global só pode ser alterado pela gestão. Crie uma regra própria da empresa.");
        }
        return r;
    }

    private static ResponseStatusException naoEncontrada(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Regra de apuração não encontrada: " + id);
    }

    static Optional<String> empresaDoToken() {
        return Optional.ofNullable(normalizar(SecurityUtils.currentCnpjTenant()));
    }

    static String normalizar(String cnpj) {
        if (cnpj == null) return null;
        String d = cnpj.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    static String validarCompetencia(String competencia) {
        try {
            if (competencia == null || !competencia.matches("\\d{4}-\\d{2}")) throw new DateTimeParseException("", "", 0);
            return YearMonth.parse(competencia).toString();
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Competência inválida: use o formato AAAA-MM");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
