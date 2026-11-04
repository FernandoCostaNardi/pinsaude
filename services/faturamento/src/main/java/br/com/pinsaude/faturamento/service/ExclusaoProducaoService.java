package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.SecurityUtils;
import br.com.pinsaude.faturamento.domain.ParticipacaoProducao;
import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.StatusProducao;
import br.com.pinsaude.faturamento.dto.ProducaoResponse;
import br.com.pinsaude.faturamento.port.MedicoLogadoPort;
import br.com.pinsaude.faturamento.port.NotaFiscalConsultaPort;
import br.com.pinsaude.faturamento.port.NotaFiscalConsultaPort.ConsultaIndisponivelException;
import br.com.pinsaude.faturamento.repository.ConciliacaoRepository;
import br.com.pinsaude.faturamento.repository.ParticipacaoRepository;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Exclusão de produção lançada errado — backoffice (operação/gestão/perm_producao) e Portal do
 * Médico. Regras:
 * <ol>
 *   <li>Só antes de virar nota: bloqueia status EMITIDA/CANCELADA, NFS-e existente no Fiscal (exceto
 *       CANCELADA/REJEITADA) e conciliação ligada à produção.</li>
 *   <li>Produção gerada pelo Fechamento por Grupo não é excluída aqui (as frequências ficam
 *       marcadas como faturadas por ela).</li>
 *   <li>Médico (sem papel de backoffice) só exclui produção em que é o único participante.</li>
 *   <li>Exclusão física, com cópia completa em faturamento.producoes_excluidas (V52).</li>
 * </ol>
 */
@Service
public class ExclusaoProducaoService {

    private static final Logger log = LoggerFactory.getLogger(ExclusaoProducaoService.class);

    /** Status de nota fiscal que não impedem excluir a produção (a nota não vale mais). */
    static final Set<String> STATUS_NOTA_LIBERADOS = Set.of("CANCELADA", "REJEITADA");

    private final ProducaoRepository     producaoRepo;
    private final ParticipacaoRepository participacaoRepo;
    private final ConciliacaoRepository  conciliacaoRepo;
    private final NotaFiscalConsultaPort notaFiscal;
    private final MedicoLogadoPort       medicoLogado;
    private final JdbcTemplate           jdbc;
    private final ObjectMapper           objectMapper;

    public ExclusaoProducaoService(ProducaoRepository producaoRepo,
                                   ParticipacaoRepository participacaoRepo,
                                   ConciliacaoRepository conciliacaoRepo,
                                   NotaFiscalConsultaPort notaFiscal,
                                   MedicoLogadoPort medicoLogado,
                                   JdbcTemplate jdbc,
                                   ObjectMapper objectMapper) {
        this.producaoRepo     = producaoRepo;
        this.participacaoRepo = participacaoRepo;
        this.conciliacaoRepo  = conciliacaoRepo;
        this.notaFiscal       = notaFiscal;
        this.medicoLogado     = medicoLogado;
        this.jdbc             = jdbc;
        this.objectMapper     = objectMapper;
    }

    @Transactional
    public void excluir(UUID id, String motivo) {
        Producao p = producaoRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Produção não encontrada: " + id));
        List<ParticipacaoProducao> participacoes = participacaoRepo.findByProducaoId(id);

        boolean backoffice = SecurityUtils.isOperacao() || SecurityUtils.isGestao() || hasRole("perm_producao");
        if (!backoffice) {
            validarDonoMedico(participacoes);
        }

        if (p.getStatus() == StatusProducao.EMITIDA) {
            throw bloqueio("Esta produção já tem NFS-e emitida e não pode ser excluída. Cancele a nota primeiro.");
        }
        if (p.getStatus() == StatusProducao.CANCELADA) {
            throw bloqueio("Esta produção está cancelada e não pode ser excluída.");
        }
        if (p.getFechamentoId() != null) {
            throw bloqueio("Esta produção foi gerada pelo Fechamento por Grupo e não pode ser excluída por aqui.");
        }
        if (conciliacaoRepo.existsByNotaId(id)) {
            throw bloqueio("Esta produção já foi conciliada com um recebimento. Desfaça a conciliação primeiro.");
        }

        Optional<String> statusNota;
        try {
            statusNota = notaFiscal.statusDaNota(id);
        } catch (ConsultaIndisponivelException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível verificar se a produção tem nota fiscal. Tente novamente em instantes.");
        }
        if (statusNota.isPresent() && !STATUS_NOTA_LIBERADOS.contains(statusNota.get())) {
            throw bloqueio("Esta produção já tem NFS-e (" + descreverStatusNota(statusNota.get())
                + "). Cancele ou rejeite a nota antes de excluir a produção.");
        }

        registrarAuditoria(p, participacoes, motivo, backoffice);
        participacaoRepo.deleteAll(participacoes);
        producaoRepo.delete(p);
        log.info("Produção {} excluída por {} ({})", id, usuarioAtual(), backoffice ? "BACKOFFICE" : "MEDICO");
    }

    private void validarDonoMedico(List<ParticipacaoProducao> participacoes) {
        UUID medicoId;
        try {
            medicoId = medicoLogado.medicoIdAtual();
        } catch (ConsultaIndisponivelException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível identificar o médico logado. Tente novamente em instantes.");
        }
        boolean unicoParticipante = participacoes.size() == 1
            && medicoId.equals(participacoes.get(0).getMedicoId());
        if (!unicoParticipante) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Você só pode excluir produções lançadas apenas no seu nome. Peça à operação da Pin para excluir esta.");
        }
    }

    private void registrarAuditoria(Producao p, List<ParticipacaoProducao> participacoes,
                                    String motivo, boolean backoffice) {
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(ProducaoResponse.from(p, participacoes));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar produção para auditoria", e);
        }
        jdbc.update("""
                INSERT INTO faturamento.producoes_excluidas
                    (producao_id, cnpj_id_tenant, tomador_id, competencia, valor_bruto, status_anterior,
                     snapshot, motivo, excluida_por, perfil)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                """,
            p.getId(), p.getCnpjIdTenant(), p.getTomador() != null ? p.getTomador().getId() : null,
            p.getCompetencia(), p.getValorBruto(), p.getStatus() != null ? p.getStatus().name() : null,
            snapshot, blankToNull(motivo), usuarioAtual(), backoffice ? "BACKOFFICE" : "MEDICO");
    }

    private static String descreverStatusNota(String status) {
        return switch (status) {
            case "EMITIDA" -> "emitida";
            case "PENDENTE", "PROCESSANDO" -> "em emissão";
            case "AGUARDANDO_VALIDACAO" -> "aguardando validação";
            case "AGUARDANDO_EMISSAO_MANUAL" -> "aguardando emissão manual";
            case "ERRO" -> "com erro na emissão";
            default -> status.toLowerCase();
        };
    }

    private static ResponseStatusException bloqueio(String msg) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            String email = jwt.getToken().getClaimAsString("email");
            if (email != null) return email;
            return jwt.getToken().getClaimAsString("preferred_username");
        }
        return auth != null ? auth.getName() : null;
    }

    private static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
