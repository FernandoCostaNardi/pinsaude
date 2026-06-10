package br.com.pinsaude.faturamento.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/faturamento")
@PreAuthorize("hasRole('operacao') or hasRole('gestao')")
public class FaturamentoController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "faturamento"));
    }

}
