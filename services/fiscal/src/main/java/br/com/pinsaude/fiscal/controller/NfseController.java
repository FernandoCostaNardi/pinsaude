package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.dto.EmitirNfseRequest;
import br.com.pinsaude.fiscal.dto.EmitirNfseResponse;
import br.com.pinsaude.fiscal.service.NfseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nfse")
public class NfseController {

    private final NfseService nfseService;

    public NfseController(NfseService nfseService) {
        this.nfseService = nfseService;
    }

    /**
     * Inicia a emissão de NFS-e para uma produção.
     * Resposta 202: nota enfileirada, processamento assíncrono via RabbitMQ.
     */
    @PostMapping("/emitir")
    @PreAuthorize("hasAnyRole('contabil', 'gestao', 'operacao', 'financeiro')")
    public ResponseEntity<EmitirNfseResponse> emitir(@Valid @RequestBody EmitirNfseRequest request) {
        String cnpjTenant = SecurityUtils.currentCnpjTenant();
        EmitirNfseResponse response = nfseService.emitir(request, cnpjTenant);
        return ResponseEntity.accepted().body(response);
    }
}
