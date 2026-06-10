package br.com.pinsaude.fiscal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fiscal")
@PreAuthorize("hasRole('contabil') or hasRole('gestao')")
public class FiscalController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "fiscal"));
    }

    @GetMapping("/parametros")
    public ResponseEntity<Map<String, Object>> getParametros() {
        return ResponseEntity.ok(Map.of("status", "ok", "endpoint", "parametros"));
    }

    @GetMapping("/notas")
    public ResponseEntity<Map<String, Object>> listNotas() {
        return ResponseEntity.ok(Map.of("status", "ok", "endpoint", "notas"));
    }
}
