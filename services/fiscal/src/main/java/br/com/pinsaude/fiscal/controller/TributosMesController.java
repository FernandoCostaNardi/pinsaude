package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.dto.TributosMesResponse;
import br.com.pinsaude.fiscal.service.TributosMesService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * CBS-32 — retenções e CBS/IBS a recolher das notas emitidas num mês (Posição de Caixa).
 * Mesmos perfis do {@code GET /api/conciliacao/posicao-caixa} do faturamento.
 */
@RestController
@RequestMapping("/api/fiscal/tributos-mes")
public class TributosMesController {

    private final TributosMesService service;

    public TributosMesController(TributosMesService service) {
        this.service = service;
    }

    /** @param mes AAAA-MM da emissão; ausente = todas as notas emitidas */
    @GetMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil') "
        + "or hasAnyRole('perm_caixa','perm_conciliacao','perm_notas')")
    public TributosMesResponse tributos(@RequestParam(required = false) String mes) {
        return service.calcular(mes(mes));
    }

    private static YearMonth mes(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return YearMonth.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mês inválido: use AAAA-MM");
        }
    }
}
