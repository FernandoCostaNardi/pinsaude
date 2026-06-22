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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/motor-fiscal")
public class MotorFiscalController {

    private final MotorFiscalService motorFiscalService;

    public MotorFiscalController(MotorFiscalService motorFiscalService) {
        this.motorFiscalService = motorFiscalService;
    }

    @PostMapping("/calcular")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'financeiro', 'operacao')")
    public ResponseEntity<CalculoFiscalResponse> calcular(@Valid @RequestBody CalculoFiscalRequest req) {
        String cnpjId = SecurityUtils.currentCnpjTenant();
        if (cnpjId != null) cnpjId = cnpjId.replaceAll("\\D", "");
        if (cnpjId == null || cnpjId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "CNPJ do tenant não encontrado no token. Configure cnpj_id no Keycloak.");
        }
        return ResponseEntity.ok(motorFiscalService.calcular(cnpjId, req));
    }
}
