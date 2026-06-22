package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.domain.HistoricoRegraEquiparacao;
import br.com.pinsaude.fiscal.domain.RegraEquiparacao;
import br.com.pinsaude.fiscal.dto.HistoricoRegraResponse;
import br.com.pinsaude.fiscal.dto.RegraEquiparacaoRequest;
import br.com.pinsaude.fiscal.dto.RegraEquiparacaoResponse;
import br.com.pinsaude.fiscal.repository.HistoricoRegraEquiparacaoRepository;
import br.com.pinsaude.fiscal.repository.RegraEquiparacaoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class RegraEquiparacaoService {

    private final RegraEquiparacaoRepository repo;
    private final HistoricoRegraEquiparacaoRepository histRepo;

    public RegraEquiparacaoService(RegraEquiparacaoRepository repo,
                                    HistoricoRegraEquiparacaoRepository histRepo) {
        this.repo = repo;
        this.histRepo = histRepo;
    }

    @Transactional(readOnly = true)
    public List<RegraEquiparacaoResponse> listar(String cnpjId) {
        return repo.findByCnpjIdOrderByCnaeAsc(cnpjId).stream()
            .map(r -> {
                List<HistoricoRegraResponse> hist = histRepo
                    .findByRegraIdOrderByAlteradoEmDesc(r.getId()).stream()
                    .map(HistoricoRegraResponse::from).toList();
                return RegraEquiparacaoResponse.from(r, hist);
            })
            .toList();
    }

    @Transactional
    public RegraEquiparacaoResponse criar(String cnpjId, RegraEquiparacaoRequest req) {
        validarRegraEquiparacao(req);
        if (repo.findByCnpjIdAndCnaeAndCodigoLc116(cnpjId, req.cnae(), req.codigoLc116()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe regra para CNAE=" + req.cnae() + " + LC116=" + req.codigoLc116());
        }
        RegraEquiparacao r = new RegraEquiparacao();
        r.setCnpjId(cnpjId);
        r.setCnae(req.cnae());
        r.setCodigoLc116(req.codigoLc116());
        r.setIndicadorEquiparacao(req.indicadorEquiparacao());
        r.setRegimePresuncao(req.regimePresuncao());
        return RegraEquiparacaoResponse.from(repo.save(r));
    }

    @Transactional
    public RegraEquiparacaoResponse atualizar(String cnpjId, UUID id, RegraEquiparacaoRequest req) {
        validarRegraEquiparacao(req);
        RegraEquiparacao r = repo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Regra não encontrada: " + id));
        if (!cnpjId.equals(r.getCnpjId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado a esta regra");
        }
        String autor = currentUserLabel();

        // Registra histórico para cada campo alterado
        if (r.getIndicadorEquiparacao() != req.indicadorEquiparacao()) {
            salvarHistorico(id, "indicadorEquiparacao",
                String.valueOf(r.getIndicadorEquiparacao()),
                String.valueOf(req.indicadorEquiparacao()), autor);
        }
        if (!java.util.Objects.equals(r.getRegimePresuncao(), req.regimePresuncao())) {
            salvarHistorico(id, "regimePresuncao",
                r.getRegimePresuncao(), req.regimePresuncao(), autor);
        }

        r.setIndicadorEquiparacao(req.indicadorEquiparacao());
        r.setRegimePresuncao(req.regimePresuncao());
        RegraEquiparacao salva = repo.save(r);

        List<HistoricoRegraResponse> hist = histRepo.findByRegraIdOrderByAlteradoEmDesc(id)
            .stream().map(HistoricoRegraResponse::from).toList();
        return RegraEquiparacaoResponse.from(salva, hist);
    }

    @Transactional(readOnly = true)
    public RegraEquiparacaoResponse buscarComHistorico(String cnpjId, UUID id) {
        RegraEquiparacao r = repo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Regra não encontrada: " + id));
        if (!cnpjId.equals(r.getCnpjId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado a esta regra");
        }
        List<HistoricoRegraResponse> hist = histRepo.findByRegraIdOrderByAlteradoEmDesc(id)
            .stream().map(HistoricoRegraResponse::from).toList();
        return RegraEquiparacaoResponse.from(r, hist);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void salvarHistorico(UUID regraId, String campo, String antes, String depois, String autor) {
        HistoricoRegraEquiparacao h = new HistoricoRegraEquiparacao();
        h.setRegraId(regraId);
        h.setCampoAlterado(campo);
        h.setValorAnterior(antes);
        h.setValorNovo(depois);
        h.setAlteradoPor(autor);
        histRepo.save(h);
    }

    private void validarRegraEquiparacao(RegraEquiparacaoRequest req) {
        if (req.indicadorEquiparacao() && (req.regimePresuncao() == null || req.regimePresuncao().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "regimePresuncao é obrigatório quando indicadorEquiparacao=true");
        }
    }

    private String currentUserLabel() {
        String cnpj = SecurityUtils.currentCnpjTenant();
        return cnpj != null ? "cnpj:" + cnpj.replaceAll("\\D", "") : "sistema";
    }
}
