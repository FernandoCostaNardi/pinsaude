package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.domain.ParametroFiscal;
import br.com.pinsaude.fiscal.dto.IbsCbsRequest;
import br.com.pinsaude.fiscal.dto.ParametroFiscalRequest;
import br.com.pinsaude.fiscal.dto.ParametroFiscalResponse;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ParametroFiscalService {

    private static final BigDecimal REDUCAO_SAUDE_PADRAO = new BigDecimal("0.6000");
    private static final BigDecimal ALIQ_ISS_PADRAO      = new BigDecimal("0.0200");

    private final ParametroFiscalRepository repo;

    public ParametroFiscalService(ParametroFiscalRepository repo) {
        this.repo = repo;
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

    /** Endpoint simplificado: cria parâmetro IBS/CBS com IR/CSLL/PIS/COFINS zerados. */
    @Transactional
    public ParametroFiscalResponse criarIbsCbs(IbsCbsRequest req) {
        String cnpj = requireCnpj();
        LocalDate vigencia = competenciaToDate(req.competenciaInicio());

        BigDecimal iss    = req.aliqIss() != null ? req.aliqIss() : ALIQ_ISS_PADRAO;
        BigDecimal reducao = req.reducaoSaude() != null ? req.reducaoSaude() : REDUCAO_SAUDE_PADRAO;

        ParametroFiscal p = new ParametroFiscal();
        p.setCnpjId(cnpj);
        p.setVigenciaInicio(vigencia);
        p.setAliqIss(iss);
        p.setAliqIr(BigDecimal.ZERO);
        p.setAliqCsll(BigDecimal.ZERO);
        p.setAliqPis(BigDecimal.ZERO);
        p.setAliqCofins(BigDecimal.ZERO);
        p.setIbsCbsAtivo(true);
        p.setAliqIbsCbs(req.aliqIbsCbs());
        p.setReducaoIbsCbsSaude(reducao);
        p.setObservacoes(req.observacoes() != null
            ? req.observacoes()
            : "Regime IBS/CBS conforme reforma tributária. Alíquotas a homologar.");
        p.setHomologado(false);
        return ParametroFiscalResponse.from(repo.save(p));
    }

    /** Marca o parâmetro como homologado pela contabilidade. */
    @Transactional
    public ParametroFiscalResponse homologar(UUID id) {
        ParametroFiscal p = repo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Parâmetro não encontrado: " + id));
        p.setHomologado(true);
        return ParametroFiscalResponse.from(repo.save(p));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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
