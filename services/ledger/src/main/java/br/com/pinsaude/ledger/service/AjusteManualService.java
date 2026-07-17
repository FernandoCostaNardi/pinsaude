package br.com.pinsaude.ledger.service;

import br.com.pinsaude.ledger.domain.AjusteManual;
import br.com.pinsaude.ledger.domain.StatusAjuste;
import br.com.pinsaude.ledger.domain.TipoOrigem;
import br.com.pinsaude.ledger.domain.TipoPartida;
import br.com.pinsaude.ledger.dto.AjusteResponse;
import br.com.pinsaude.ledger.dto.CriarAjusteRequest;
import br.com.pinsaude.ledger.dto.CriarLancamentoRequest;
import br.com.pinsaude.ledger.dto.LancamentoResponse;
import br.com.pinsaude.ledger.dto.PartidaRequest;
import br.com.pinsaude.ledger.repository.AjusteManualRepository;
import br.com.pinsaude.ledger.repository.ContaLedgerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluxo de ajuste manual com DUPLA APROVAÇÃO. O solicitante cria um ajuste PENDENTE;
 * um segundo usuário, com id e PERFIL diferentes, aprova — só então o lançamento
 * (imutável) é criado no razão. Reusa {@link LancamentoService} (equilíbrio + idempotência).
 */
@Service
public class AjusteManualService {

    private final AjusteManualRepository ajusteRepo;
    private final ContaLedgerRepository contaRepo;
    private final LancamentoService lancamentoService;

    public AjusteManualService(AjusteManualRepository ajusteRepo,
                               ContaLedgerRepository contaRepo,
                               LancamentoService lancamentoService) {
        this.ajusteRepo = ajusteRepo;
        this.contaRepo = contaRepo;
        this.lancamentoService = lancamentoService;
    }

    @Transactional
    public AjusteResponse criar(CriarAjusteRequest req, String solicitanteId, String solicitantePerfil,
                                String cnpjTenant) {
        if (solicitanteId == null || solicitantePerfil == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem perfil de backoffice");
        }
        if (Objects.equals(req.contaDebitoCodigo(), req.contaCreditoCodigo())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Conta de débito e crédito devem ser diferentes");
        }
        validarConta(req.contaDebitoCodigo());
        validarConta(req.contaCreditoCodigo());

        AjusteManual ajuste = new AjusteManual(
            cnpjTenant != null ? cnpjTenant.replaceAll("\\D", "") : "",
            req.medicoId(), req.competencia(),
            req.contaDebitoCodigo(), req.contaCreditoCodigo(), req.valorCentavos(),
            req.motivo(), solicitanteId, solicitantePerfil);

        return AjusteResponse.from(ajusteRepo.save(ajuste));
    }

    @Transactional
    public AjusteResponse aprovar(UUID ajusteId, String aprovadorId, String aprovadorPerfil) {
        AjusteManual ajuste = buscarPendente(ajusteId);

        if (aprovadorId == null || aprovadorPerfil == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aprovador sem perfil de backoffice");
        }
        if (Objects.equals(aprovadorId, ajuste.getSolicitanteId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "O ajuste deve ser aprovado por um segundo usuário (diferente do solicitante)");
        }
        if (Objects.equals(aprovadorPerfil, ajuste.getSolicitantePerfil())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "O aprovador deve ter um perfil diferente do solicitante ("
                + ajuste.getSolicitantePerfil() + ")");
        }

        // Cria o lançamento (imutável) — LancamentoService valida o equilíbrio e é idempotente
        LancamentoResponse lancamento = lancamentoService.criar(new CriarLancamentoRequest(
            ajuste.getCnpjIdTenant(), ajuste.getMedicoId(), null, ajuste.getCompetencia(),
            TipoOrigem.AJUSTE, ajuste.getId(),
            ajuste.getMotivo() + " [solicitado por " + ajuste.getSolicitanteId()
                + ", aprovado por " + aprovadorId + "]",
            "AJUSTE:" + ajuste.getId(),
            List.of(new PartidaRequest(ajuste.getContaDebitoCodigo(), TipoPartida.DEBITO, ajuste.getValorCentavos()),
                    new PartidaRequest(ajuste.getContaCreditoCodigo(), TipoPartida.CREDITO, ajuste.getValorCentavos()))));

        ajuste.aprovar(aprovadorId, aprovadorPerfil, lancamento.id());
        return AjusteResponse.from(ajusteRepo.save(ajuste));
    }

    @Transactional
    public AjusteResponse rejeitar(UUID ajusteId, String aprovadorId, String aprovadorPerfil, String motivo) {
        AjusteManual ajuste = buscarPendente(ajusteId);
        ajuste.rejeitar(aprovadorId, aprovadorPerfil, motivo);
        return AjusteResponse.from(ajusteRepo.save(ajuste));
    }

    @Transactional(readOnly = true)
    public List<AjusteResponse> listar(StatusAjuste status) {
        List<AjusteManual> ajustes = status != null
            ? ajusteRepo.findByStatusOrderByCreatedAtDesc(status)
            : ajusteRepo.findAllByOrderByCreatedAtDesc();
        return ajustes.stream().map(AjusteResponse::from).toList();
    }

    private AjusteManual buscarPendente(UUID ajusteId) {
        AjusteManual ajuste = ajusteRepo.findById(ajusteId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ajuste não encontrado"));
        if (ajuste.getStatus() != StatusAjuste.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ajuste já foi decidido (status: " + ajuste.getStatus() + ")");
        }
        return ajuste;
    }

    private void validarConta(String codigo) {
        contaRepo.findByCodigo(codigo).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta não encontrada: " + codigo));
    }
}
