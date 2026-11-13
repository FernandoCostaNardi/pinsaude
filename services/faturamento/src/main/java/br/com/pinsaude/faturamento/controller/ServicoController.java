package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.dto.ServicoClassificacaoRequest;
import br.com.pinsaude.faturamento.dto.ServicoResponse;
import br.com.pinsaude.faturamento.service.ServicoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/servicos")
public class ServicoController {

    private final ServicoService service;

    public ServicoController(ServicoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<ServicoResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    /** CBS-12 — classificação de CBS/IBS do serviço. Mesmo acesso da escrita do cronograma (CBS-10). */
    @PutMapping("/{id}/classificacao-tributaria")
    @PreAuthorize("hasAnyRole('contabil','gestao') or hasRole('perm_fiscal')")
    public ResponseEntity<ServicoResponse> atualizarClassificacao(
            @PathVariable UUID id, @Valid @RequestBody ServicoClassificacaoRequest req) {
        return ResponseEntity.ok(service.atualizarClassificacao(id, req));
    }
}
