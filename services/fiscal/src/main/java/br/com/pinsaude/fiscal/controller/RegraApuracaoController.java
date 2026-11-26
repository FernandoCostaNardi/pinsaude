package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.apuracao.ResultadoApuracao;
import br.com.pinsaude.fiscal.dto.RegraApuracaoRequest;
import br.com.pinsaude.fiscal.dto.RegraApuracaoResponse;
import br.com.pinsaude.fiscal.dto.SimulacaoApuracaoRequest;
import br.com.pinsaude.fiscal.service.RegraApuracaoService;
import br.com.pinsaude.fiscal.service.SimuladorApuracaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * APU-00 — regras da apuração mensal (parametrizáveis, a homologar pela contabilidade) e simulador.
 * Leitura e simulação: contabil/gestao/financeiro/perm_fiscal. Escrita e homologação: sem financeiro.
 */
@RestController
@RequestMapping("/api/fiscal/regras-apuracao")
public class RegraApuracaoController {

    private static final String LEITURA = "hasAnyRole('contabil','gestao','financeiro') or hasRole('perm_fiscal')";
    private static final String ESCRITA = "hasAnyRole('contabil','gestao') or hasRole('perm_fiscal')";

    private final RegraApuracaoService service;
    private final SimuladorApuracaoService simulador;

    public RegraApuracaoController(RegraApuracaoService service, SimuladorApuracaoService simulador) {
        this.service = service;
        this.simulador = simulador;
    }

    /** @param cnpj empresa (só para quem não tem cnpj_id no token); sem ele a gestão vê todas */
    @GetMapping
    @PreAuthorize(LEITURA)
    public List<RegraApuracaoResponse> listar(@RequestParam(required = false) String cnpj) {
        return service.listar(cnpj);
    }

    /** Regra que vale na competência: a da empresa, senão o padrão global. */
    @GetMapping("/vigente")
    @PreAuthorize(LEITURA)
    public RegraApuracaoResponse vigente(@RequestParam String competencia,
                                         @RequestParam(required = false) String cnpj) {
        return service.vigente(competencia, cnpj);
    }

    @GetMapping("/{id}")
    @PreAuthorize(LEITURA)
    public RegraApuracaoResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @PostMapping
    @PreAuthorize(ESCRITA)
    public ResponseEntity<RegraApuracaoResponse> criar(@Valid @RequestBody RegraApuracaoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
    }

    /** Edita a regra; se estava homologada, volta para rascunho. */
    @PutMapping("/{id}")
    @PreAuthorize(ESCRITA)
    public RegraApuracaoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody RegraApuracaoRequest req) {
        return service.atualizar(id, req);
    }

    @PutMapping("/{id}/homologar")
    @PreAuthorize(ESCRITA)
    public RegraApuracaoResponse homologar(@PathVariable UUID id) {
        return service.homologar(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ESCRITA)
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    /** Aplica a regra a números informados e devolve o cálculo passo a passo. Não grava nada. */
    @PostMapping("/simular")
    @PreAuthorize(LEITURA)
    public ResultadoApuracao simular(@Valid @RequestBody SimulacaoApuracaoRequest req) {
        return simulador.simular(req);
    }
}
