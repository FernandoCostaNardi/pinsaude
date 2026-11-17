package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.dto.CalculoFiscalRequest;
import br.com.pinsaude.fiscal.dto.CalculoFiscalResponse;
import br.com.pinsaude.fiscal.service.MotorFiscalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/motor-fiscal")
public class MotorFiscalController {

    private final MotorFiscalService motorFiscalService;

    public MotorFiscalController(MotorFiscalService motorFiscalService) {
        this.motorFiscalService = motorFiscalService;
    }

    /**
     * @param cnpj empresa emissora, só usada por quem não tem cnpj_id no token (gestão); para os
     *             demais vale sempre a empresa do token. Mesma regra do GET /api/fiscal/regime.
     */
    @PostMapping("/calcular")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'financeiro', 'operacao') "
        + "or hasRole('perm_fiscal') or hasRole('perm_notas') or hasRole('perm_producao')")
    public ResponseEntity<CalculoFiscalResponse> calcular(@Valid @RequestBody CalculoFiscalRequest req,
                                                          @RequestParam(required = false) String cnpj) {
        return ResponseEntity.ok(motorFiscalService.calcular(resolverEmpresa(cnpj), req));
    }

    private String resolverEmpresa(String cnpjParam) {
        String doToken = SecurityUtils.currentCnpjTenant();
        doToken = doToken == null ? "" : doToken.replaceAll("\\D", "");
        if (!doToken.isBlank()) return doToken;
        String informado = cnpjParam == null ? "" : cnpjParam.replaceAll("\\D", "");
        if (informado.length() == 14) return informado;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "CNPJ do tenant não encontrado no token. Informe o CNPJ da empresa emissora.");
    }
}
