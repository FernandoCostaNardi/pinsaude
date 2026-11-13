package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.domain.ParametroFiscal;
import br.com.pinsaude.fiscal.domain.RegimeTributario;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.IbsCbsRequest;
import br.com.pinsaude.fiscal.dto.ParametroFiscalRequest;
import br.com.pinsaude.fiscal.dto.ParametroFiscalResponse;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ParametroFiscalService {

    /** Notas que não contam para travar a chave CBS/IBS. */
    private static final Set<StatusNota> STATUS_IGNORADOS = Set.of(StatusNota.CANCELADA, StatusNota.REJEITADA);
    /** Limite superior aberto das competências (YYYY-MM): maior que qualquer competência real. */
    private static final String SEM_FIM = "9999-12";

    private final ParametroFiscalRepository repo;
    private final NotaFiscalRepository notaRepo;

    public ParametroFiscalService(ParametroFiscalRepository repo, NotaFiscalRepository notaRepo) {
        this.repo = repo;
        this.notaRepo = notaRepo;
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ParametroFiscalResponse> listar() {
        String cnpj = currentCnpj();
        List<ParametroFiscal> lista = cnpj.isBlank()
            ? repo.findAllByOrderByVigenciaInicioDesc()
            : repo.findByCnpjIdOrderByVigenciaInicioDesc(cnpj);
        return lista.stream().map(ParametroFiscalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ParametroFiscalResponse buscarParaCompetencia(String competencia) {
        String cnpj = currentCnpj();
        if (cnpj.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "CNPJ do tenant obrigatório para busca por competência");
        }
        LocalDate dataReferencia = competenciaToDate(competencia);
        return repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(cnpj, dataReferencia)
            .map(ParametroFiscalResponse::from)
            .orElseThrow(() -> new EntityNotFoundException(
                "Nenhum parâmetro fiscal vigente para " + competencia + " (cnpj: " + cnpj + ")"));
    }

    // ─── Criação ──────────────────────────────────────────────────────────────

    @Transactional
    public ParametroFiscalResponse criar(ParametroFiscalRequest req) {
        validarIbsCbs(req.ibsCbsAtivo(), req.aliqIbsCbs(), req.reducaoIbsCbsSaude());
        String cnpj = requireCnpj();
        LocalDate vigencia = competenciaToDate(req.competenciaInicio());
        if (req.ibsCbsAtivo()) {
            bloquearSeHaNotas(cnpj, vigencia, null,
                "Já existe nota fiscal emitida a partir de " + req.competenciaInicio()
                + ". Ligue a chave numa competência sem notas.");
        } else {
            boolean anteriorNoRegimeNovo = repo
                .findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(cnpj, vigencia)
                .map(a -> a.isIbsCbsAtivo() && a.isHomologado())
                .orElse(false);
            if (anteriorNoRegimeNovo) {
                bloquearSeHaNotas(cnpj, vigencia, RegimeTributario.IBS_CBS,
                    "Já existem notas fiscais emitidas no regime CBS/IBS a partir de "
                    + req.competenciaInicio() + ". A chave não pode ser desligada.");
            }
        }

        ParametroFiscal p = new ParametroFiscal();
        p.setCnpjId(cnpj);
        p.setVigenciaInicio(vigencia);
        p.setAliqIss(req.aliqIss());
        p.setAliqIr(req.aliqIr());
        p.setAliqCsll(req.aliqCsll());
        p.setAliqPis(req.aliqPis());
        p.setAliqCofins(req.aliqCofins());
        p.setIbsCbsAtivo(req.ibsCbsAtivo());
        p.setAliqIbsCbs(req.aliqIbsCbs());
        p.setReducaoIbsCbsSaude(req.reducaoIbsCbsSaude());
        p.setObservacoes(req.observacoes());
        p.setHomologado(false);
        return ParametroFiscalResponse.from(repo.save(p));
    }

    /**
     * CBS-14: liga a chave CBS/IBS da empresa a partir de uma competência.
     * Não mexe em ISS/IR/CSLL/PIS/COFINS: copia as alíquotas do parâmetro vigente
     * (o motor zera PIS/COFINS e calcula CBS/IBS pelo cronograma quando o regime é IBS_CBS).
     * O parâmetro nasce sem homologação; o motor só usa o regime novo depois de homologado.
     */
    @Transactional
    public ParametroFiscalResponse criarIbsCbs(IbsCbsRequest req) {
        String cnpj = requireCnpj();
        LocalDate vigencia = competenciaToDate(req.competenciaInicio());

        ParametroFiscal p = repo.findByCnpjIdAndVigenciaInicio(cnpj, vigencia).orElse(null);
        if (p != null && p.isIbsCbsAtivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A chave CBS/IBS já está ligada a partir de " + req.competenciaInicio());
        }
        bloquearSeHaNotas(cnpj, vigencia, null,
            "Já existe nota fiscal emitida a partir de " + req.competenciaInicio()
            + ". Ligue a chave numa competência sem notas.");

        if (p == null) {
            ParametroFiscal base = repo
                .findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(cnpj, vigencia)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cadastre antes o parâmetro fiscal do regime atual (ISS, IR, CSLL, PIS e COFINS)"));
            p = new ParametroFiscal();
            p.setCnpjId(cnpj);
            p.setVigenciaInicio(vigencia);
            p.setAliqIss(base.getAliqIss());
            p.setAliqIr(base.getAliqIr());
            p.setAliqCsll(base.getAliqCsll());
            p.setAliqPis(base.getAliqPis());
            p.setAliqCofins(base.getAliqCofins());
        }
        if (req.aliqIss() != null) p.setAliqIss(req.aliqIss());
        p.setIbsCbsAtivo(true);
        // Deprecados (CBS-10): alíquotas vêm do cronograma e a redução vem do serviço LC116.
        if (req.aliqIbsCbs() != null) p.setAliqIbsCbs(req.aliqIbsCbs());
        if (req.reducaoSaude() != null) p.setReducaoIbsCbsSaude(req.reducaoSaude());
        p.setObservacoes(req.observacoes() != null
            ? req.observacoes()
            : "Regime CBS/IBS (reforma tributária). Aguardando homologação da contabilidade.");
        limparHomologacao(p);
        return ParametroFiscalResponse.from(repo.save(p));
    }

    /**
     * CBS-14: desliga a chave CBS/IBS. Só é possível enquanto nenhuma nota foi
     * calculada no regime novo no período coberto pelo parâmetro.
     */
    @Transactional
    public ParametroFiscalResponse desligarIbsCbs(UUID id) {
        ParametroFiscal p = repo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Parâmetro não encontrado: " + id));
        exigirMesmoTenant(p);
        if (!p.isIbsCbsAtivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A chave CBS/IBS já está desligada");
        }
        bloquearSeHaNotas(p.getCnpjId(), p.getVigenciaInicio(), RegimeTributario.IBS_CBS,
            "Já existem notas fiscais emitidas no regime CBS/IBS neste período. A chave não pode ser desligada.");
        p.setIbsCbsAtivo(false);
        limparHomologacao(p);
        return ParametroFiscalResponse.from(repo.save(p));
    }

    /** Marca o parâmetro como homologado pela contabilidade. */
    @Transactional
    public ParametroFiscalResponse homologar(UUID id) {
        ParametroFiscal p = repo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Parâmetro não encontrado: " + id));
        exigirMesmoTenant(p);
        if (p.isHomologado()) return ParametroFiscalResponse.from(p);
        if (p.isIbsCbsAtivo()) {
            // É a homologação que vira o regime no motor: não pode alcançar notas já emitidas.
            bloquearSeHaNotas(p.getCnpjId(), p.getVigenciaInicio(), null,
                "Já existem notas fiscais emitidas neste período no regime atual. "
                + "Crie a chave CBS/IBS numa competência sem notas.");
        }
        p.setHomologado(true);
        p.setHomologadoPor(SecurityUtils.currentUsuario());
        p.setHomologadoEm(OffsetDateTime.now());
        return ParametroFiscalResponse.from(repo.save(p));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Lança 422 se a empresa já tem nota (não cancelada/rejeitada) no período coberto por um
     * parâmetro que começa em {@code vigencia}: até a vigência do próximo parâmetro, se houver.
     * Com {@code regime}, considera só as notas calculadas nesse regime.
     */
    private void bloquearSeHaNotas(String cnpj, LocalDate vigencia, RegimeTributario regime, String mensagem) {
        String de = toCompetencia(vigencia);
        String ate = repo.findFirstByCnpjIdAndVigenciaInicioGreaterThanOrderByVigenciaInicioAsc(cnpj, vigencia)
            .map(prox -> toCompetencia(prox.getVigenciaInicio()))
            .orElse(SEM_FIM);
        boolean existe = regime == null
            ? notaRepo.existeNotaNoPeriodo(cnpj, de, ate, STATUS_IGNORADOS)
            : notaRepo.existeNotaNoPeriodoNoRegime(cnpj, de, ate, STATUS_IGNORADOS, regime);
        if (existe) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem);
    }

    private void limparHomologacao(ParametroFiscal p) {
        p.setHomologado(false);
        p.setHomologadoPor(null);
        p.setHomologadoEm(null);
    }

    /** Usuário com tenant só altera parâmetro da própria empresa; gestão (sem tenant) altera qualquer um. */
    private void exigirMesmoTenant(ParametroFiscal p) {
        String cnpj = currentCnpj();
        if (!cnpj.isBlank() && !cnpj.equals(p.getCnpjId())) {
            throw new EntityNotFoundException("Parâmetro não encontrado: " + p.getId());
        }
    }

    private static String toCompetencia(LocalDate d) {
        return d.getYear() + "-" + String.format("%02d", d.getMonthValue());
    }

    /** Converte "YYYY-MM" → primeiro dia do mês (data do fato gerador). */
    private LocalDate competenciaToDate(String competencia) {
        String[] parts = competencia.split("-");
        return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
    }

    private String currentCnpj() {
        String cnpj = SecurityUtils.currentCnpjTenant();
        if (cnpj == null || cnpj.isBlank()) return "";
        return cnpj.replaceAll("\\D", "");
    }

    private String requireCnpj() {
        String cnpj = currentCnpj();
        if (cnpj.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "CNPJ do tenant não encontrado no token. Configure o atributo cnpj_id no Keycloak.");
        }
        return cnpj;
    }

    private void validarIbsCbs(boolean ativo, BigDecimal aliq, BigDecimal reducao) {
        if (ativo) {
            if (aliq == null || aliq.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "aliqIbsCbs é obrigatório quando ibsCbsAtivo=true");
            }
            if (reducao == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "reducaoIbsCbsSaude é obrigatório quando ibsCbsAtivo=true");
            }
        }
    }
}
