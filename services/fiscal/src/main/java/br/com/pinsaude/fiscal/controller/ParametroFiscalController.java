package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.dto.IbsCbsRequest;
import br.com.pinsaude.fiscal.dto.ParametroFiscalRequest;
import br.com.pinsaude.fiscal.dto.ParametroFiscalResponse;
import br.com.pinsaude.fiscal.service.ParametroFiscalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parametros-fiscais")
public class ParametroFiscalController {

    private final ParametroFiscalService service;

    public ParametroFiscalController(ParametroFiscalService service) {
        this.service = service;
    }

    /**
     * Lista todos os parâmetros do tenant.
     * Se competencia (YYYY-MM) for fornecida, retorna apenas o vigente para aquela data.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('contabil','gestao','financeiro')")
    public ResponseEntity<?> listar(@RequestParam(required = false) String competencia) {
        if (competencia != null && !competencia.isBlank()) {
            return ResponseEntity.ok(service.buscarParaCompetencia(competencia));
        }
        List<ParametroFiscalResponse> lista = service.listar();
        return ResponseEntity.ok(lista);
    }

    /** Cria parâmetro fiscal genérico (regime atual ou IBS/CBS completo). */
    @PostMapping
    @PreAuthorize("hasAnyRole('contabil','gestao')")
    public ResponseEntity<ParametroFiscalResponse> criar(
            @Valid @RequestBody ParametroFiscalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
    }

    /**
     * Endpoint simplificado para parametrizar a Reforma Tributária.
     * Zera IR/CSLL/PIS/COFINS e ativa ibs_cbs_ativo=true.
     */
    @PostMapping("/ibs-cbs")
    @PreAuthorize("hasAnyRole('contabil','gestao')")
    public ResponseEntity<ParametroFiscalResponse> criarIbsCbs(
            @Valid @RequestBody IbsCbsRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarIbsCbs(req));
    }

    /**
     * Homologa o parâmetro — contabilidade confirma que os valores estão corretos.
     * Parâmetros não homologados são tratados como rascunho.
     */
    @PutMapping("/{id}/homologar")
    @PreAuthorize("hasAnyRole('contabil','gestao')")
    public ResponseEntity<ParametroFiscalResponse> homologar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.homologar(id));
    }
}
