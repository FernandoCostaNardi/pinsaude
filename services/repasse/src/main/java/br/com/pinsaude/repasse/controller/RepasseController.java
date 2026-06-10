package br.com.pinsaude.repasse.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/repasse")
@PreAuthorize("hasRole('financeiro') or hasRole('gestao')")
public class RepasseController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "repasse"));
    }

    @GetMapping("/worklist")
    public ResponseEntity<Map<String, Object>> worklist() {
        return ResponseEntity.ok(Map.of("status", "ok", "endpoint", "worklist"));
    }

    @GetMapping("/historico")
    public ResponseEntity<Map<String, Object>> historico() {
        return ResponseEntity.ok(Map.of("status", "ok", "endpoint", "historico"));
    }
}
