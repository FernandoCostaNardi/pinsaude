package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.dto.ServicoResponse;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/servicos")
public class ServicoController {

    private final ServicoRepository repo;

    public ServicoController(ServicoRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<ServicoResponse>> listar() {
        List<ServicoResponse> result = repo.findAll().stream()
            .sorted((a, b) -> a.getCodigoLc116().compareTo(b.getCodigoLc116()))
            .map(ServicoResponse::from)
            .toList();
        return ResponseEntity.ok(result);
    }
}
