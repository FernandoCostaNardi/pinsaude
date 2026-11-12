package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.dto.CronogramaReformaRequest;
import br.com.pinsaude.fiscal.dto.CronogramaReformaResponse;
import br.com.pinsaude.fiscal.service.CronogramaReformaService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CBS-10 — cronograma de alíquotas da reforma tributária 2026–2033 (catálogo global). */
@RestController
@RequestMapping("/api/fiscal/cronograma-reforma")
public class CronogramaReformaController {

    private final CronogramaReformaService service;

    public CronogramaReformaController(CronogramaReformaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('contabil','gestao','financeiro') or hasRole('perm_fiscal')")
    public List<CronogramaReformaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{ano}")
    @PreAuthorize("hasAnyRole('contabil','gestao','financeiro') or hasRole('perm_fiscal')")
    public CronogramaReformaResponse buscar(@PathVariable int ano) {
        return service.buscar(ano);
    }

    /** Ajusta um ano (ex.: alíquota CBS 2027 após a resolução do Senado — CBS-50). */
    @PutMapping("/{ano}")
    @PreAuthorize("hasAnyRole('contabil','gestao') or hasRole('perm_fiscal')")
    public CronogramaReformaResponse atualizar(@PathVariable int ano,
                                               @Valid @RequestBody CronogramaReformaRequest req) {
        return service.atualizar(ano, req);
    }
}
