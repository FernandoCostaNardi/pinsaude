package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.dto.RegimeCompetenciaResponse;
import br.com.pinsaude.fiscal.service.RegimeFiscalService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * CBS-15 — regime tributário (LEGADO | IBS_CBS) da empresa numa competência.
 * Mesmos perfis que calculam tributos nas telas fiscais (motor fiscal, emissão de NFS-e e produção).
 */
@RestController
@RequestMapping("/api/fiscal/regime")
public class RegimeFiscalController {

    private final RegimeFiscalService service;

    public RegimeFiscalController(RegimeFiscalService service) {
        this.service = service;
    }

    /**
     * @param cnpj empresa consultada, só usada por quem não tem cnpj_id no token (gestão);
     *             para os demais vale sempre a empresa do token.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('contabil','gestao','financeiro','operacao') "
        + "or hasRole('perm_fiscal') or hasRole('perm_notas') or hasRole('perm_producao')")
    public RegimeCompetenciaResponse regime(@RequestParam String competencia,
                                            @RequestParam(required = false) String cnpj) {
        return service.regime(resolverEmpresa(cnpj), competencia);
    }

    private String resolverEmpresa(String cnpjParam) {
        String doToken = SecurityUtils.currentCnpjTenant();
        if (doToken != null && !doToken.isBlank()) return doToken;
        String informado = cnpjParam == null ? "" : cnpjParam.replaceAll("\\D", "");
        if (informado.length() == 14) return informado;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Informe o CNPJ da empresa: seu usuário não está vinculado a uma empresa");
    }
}
