package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.domain.ParametrosFiscais;
import br.com.pinsaude.fiscal.domain.TipoTributo;
import br.com.pinsaude.fiscal.dto.ParametrosFiscaisRequest;
import br.com.pinsaude.fiscal.dto.ParametrosFiscaisResponse;
import br.com.pinsaude.fiscal.repository.ParametrosFiscaisRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ParametrosFiscaisService {

    private final ParametrosFiscaisRepository repo;

    public ParametrosFiscaisService(ParametrosFiscaisRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ParametrosFiscaisResponse> listar(String cnpjId) {
        List<ParametrosFiscais> lista = new java.util.ArrayList<>();
        for (TipoTributo tributo : TipoTributo.values()) {
            lista.addAll(repo.findVigenteParaTributo(cnpjId, tributo,
                "9999-12", PageRequest.of(0, 200)));
        }
        lista.sort((a, b) -> {
            int cmp = a.getTipoTributo().name().compareTo(b.getTipoTributo().name());
            return cmp != 0 ? cmp : b.getCompetenciaInicio().compareTo(a.getCompetenciaInicio());
        });
        return lista.stream().map(ParametrosFiscaisResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public boolean proximaCompetenciaTemAliquotas(String cnpjId) {
        String proxima = YearMonth.now().plusMonths(1).toString(); // YYYY-MM
        for (TipoTributo tributo : TipoTributo.values()) {
            List<ParametrosFiscais> vigentes =
                repo.findVigenteParaTributo(cnpjId, tributo, proxima, PageRequest.of(0, 1));
            if (vigentes.isEmpty()) return false;
        }
        return true;
    }

    @Transactional
    public ParametrosFiscaisResponse criar(String cnpjId, ParametrosFiscaisRequest req) {
        validarSobreposicao(cnpjId, req);

        ParametrosFiscais p = new ParametrosFiscais();
        p.setCnpjId(cnpjId);
        p.setTipoTributo(req.tipoTributo());
        p.setCompetenciaInicio(req.competenciaInicio());
        p.setCompetenciaFim(req.competenciaFim());
        p.setValorAliquota(req.valorAliquota());
        p.setDescricao(req.descricao());
        p.setCreatedBy(currentUserLabel());
        return ParametrosFiscaisResponse.from(repo.save(p));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void validarSobreposicao(String cnpjId, ParametrosFiscaisRequest req) {
        List<ParametrosFiscais> existentes = repo.findVigenteParaTributo(
            cnpjId, req.tipoTributo(), "9999-12", PageRequest.of(0, 50));

        for (ParametrosFiscais ex : existentes) {
            // Nova entrada sobrepõe se competenciaInicio cair dentro da vigência existente
            boolean inicioNaVigencia =
                req.competenciaInicio().compareTo(ex.getCompetenciaInicio()) >= 0
                && (ex.getCompetenciaFim() == null
                    || req.competenciaInicio().compareTo(ex.getCompetenciaFim()) <= 0);
            if (inicioNaVigencia) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe alíquota para " + req.tipoTributo()
                    + " com vigência que inclui " + req.competenciaInicio()
                    + ". Defina competenciaFim na vigência anterior antes de criar uma nova.");
            }
        }
    }

    private String currentUserLabel() {
        String cnpj = SecurityUtils.currentCnpjTenant();
        return cnpj != null ? "cnpj:" + cnpj.replaceAll("\\D", "") : "sistema";
    }
}
