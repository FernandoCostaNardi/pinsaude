package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.dto.ApuracaoMensalResponse;
import br.com.pinsaude.fiscal.service.ApuracaoMensalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * APU-01 — apuração mensal por CNPJ sobre as notas reais, pela regra de apuração vigente (APU-00).
 * Só leitura: não grava nada (a tela e o fechamento da competência ficam para a APU-02).
 */
@RestController
@RequestMapping("/api/fiscal/apuracao")
public class ApuracaoMensalController {

    private final ApuracaoMensalService service;

    public ApuracaoMensalController(ApuracaoMensalService service) {
        this.service = service;
    }

    /**
     * @param competencia AAAA-MM
     * @param cnpj        empresa, obrigatório para quem não tem cnpj_id no token (gestão); ignorado nos demais
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('contabil','gestao','financeiro') or hasRole('perm_fiscal')")
    public ApuracaoMensalResponse apurar(@RequestParam String competencia,
                                         @RequestParam(required = false) String cnpj) {
        return service.apurar(competencia, cnpj);
    }
}
