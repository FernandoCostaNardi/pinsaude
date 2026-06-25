package br.com.pinsaude.portal.service;

import br.com.pinsaude.portal.dto.DashboardResponse;
import br.com.pinsaude.portal.dto.NotaPortalResponse;
import br.com.pinsaude.portal.dto.ProducaoPortalResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class PortalService {

    private final JdbcTemplate jdbc;

    public PortalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID resolveMedicoId(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "JWT não contém claim 'email'. Configure o mapper no Keycloak.");
        }
        List<UUID> ids = jdbc.query(
                "SELECT id FROM onboarding.medicos WHERE email = ?",
                (rs, row) -> rs.getObject("id", UUID.class),
                email);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Médico não encontrado para o e-mail: " + email);
        }
        return ids.get(0);
    }

    public DashboardResponse getDashboard(UUID medicoId) {
        long saldoDisponivel = somarLiquidoPorStatus(medicoId, "EMITIDA");
        long valorAReceber = somarLiquidoPorStatusIn(medicoId, "PENDENTE", "PROCESSANDO", "AGUARDANDO_VALIDACAO");
        long totalEmitidas = contarNotasPorStatus(medicoId, "EMITIDA");

        long totalProduzido = jdbc.query(
                "SELECT COALESCE(SUM(valor_bruto), 0) FROM faturamento.producoes WHERE medico_id = ?",
                (rs, row) -> rs.getLong(1),
                medicoId).stream().findFirst().orElse(0L);

        long totalProducoes = jdbc.query(
                "SELECT COUNT(*) FROM faturamento.producoes WHERE medico_id = ?",
                (rs, row) -> rs.getLong(1),
                medicoId).stream().findFirst().orElse(0L);

        List<NotaPortalResponse> ultimasNotas = jdbc.query(
                "SELECT id, producao_id, competencia, tomador_nome, valor_bruto, " +
                "valor_liquido_medico, taxa_pin, status, numero_nota, emitida_at, created_at " +
                "FROM fiscal.notas_fiscais WHERE medico_id = ? ORDER BY created_at DESC LIMIT 5",
                (rs, row) -> mapNota(rs),
                medicoId);

        return new DashboardResponse(saldoDisponivel, valorAReceber, totalProduzido,
                totalEmitidas, totalProducoes, ultimasNotas, Collections.emptyList());
    }

    public List<NotaPortalResponse> getNotas(UUID medicoId, String competencia, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, producao_id, competencia, tomador_nome, valor_bruto, " +
                "valor_liquido_medico, taxa_pin, status, numero_nota, emitida_at, created_at " +
                "FROM fiscal.notas_fiscais WHERE medico_id = ?");

        List<Object> params = new java.util.ArrayList<>();
        params.add(medicoId);

        if (competencia != null && !competencia.isBlank()) {
            sql.append(" AND competencia = ?");
            params.add(competencia);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY created_at DESC");

        return jdbc.query(sql.toString(), (rs, row) -> mapNota(rs), params.toArray());
    }

    public List<ProducaoPortalResponse> getProducoes(UUID medicoId, String competencia) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.competencia, t.razao_social AS tomador_nome, " +
                "s.descricao_padrao AS servico_descricao, p.valor_bruto, p.status, p.created_at " +
                "FROM faturamento.producoes p " +
                "JOIN faturamento.tomadores t ON t.id = p.tomador_id " +
                "JOIN faturamento.servicos s ON s.id = p.servico_id " +
                "WHERE p.medico_id = ?");

        List<Object> params = new java.util.ArrayList<>();
        params.add(medicoId);

        if (competencia != null && !competencia.isBlank()) {
            sql.append(" AND p.competencia = ?");
            params.add(competencia);
        }
        sql.append(" ORDER BY p.created_at DESC");

        return jdbc.query(sql.toString(), (rs, row) -> {
            long bruto = rs.getLong("valor_bruto");
            return new ProducaoPortalResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("competencia"),
                    rs.getString("tomador_nome"),
                    rs.getString("servico_descricao"),
                    bruto,
                    Math.round(bruto * 0.85),
                    rs.getString("status"),
                    toOffsetDateTime(rs.getTimestamp("created_at"))
            );
        }, params.toArray());
    }

    private long somarLiquidoPorStatus(UUID medicoId, String status) {
        return jdbc.query(
                "SELECT COALESCE(SUM(valor_liquido_medico), 0) FROM fiscal.notas_fiscais " +
                "WHERE medico_id = ? AND status = ?",
                (rs, row) -> rs.getLong(1),
                medicoId, status).stream().findFirst().orElse(0L);
    }

    private long somarLiquidoPorStatusIn(UUID medicoId, String... statuses) {
        String placeholders = String.join(",", Collections.nCopies(statuses.length, "?"));
        Object[] params = new Object[statuses.length + 1];
        params[0] = medicoId;
        System.arraycopy(statuses, 0, params, 1, statuses.length);
        return jdbc.query(
                "SELECT COALESCE(SUM(valor_liquido_medico), 0) FROM fiscal.notas_fiscais " +
                "WHERE medico_id = ? AND status IN (" + placeholders + ")",
                (rs, row) -> rs.getLong(1),
                params).stream().findFirst().orElse(0L);
    }

    private long contarNotasPorStatus(UUID medicoId, String status) {
        return jdbc.query(
                "SELECT COUNT(*) FROM fiscal.notas_fiscais WHERE medico_id = ? AND status = ?",
                (rs, row) -> rs.getLong(1),
                medicoId, status).stream().findFirst().orElse(0L);
    }

    private NotaPortalResponse mapNota(ResultSet rs) throws SQLException {
        return new NotaPortalResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("producao_id", UUID.class),
                rs.getString("competencia"),
                rs.getString("tomador_nome"),
                rs.getLong("valor_bruto"),
                rs.getLong("valor_liquido_medico"),
                rs.getLong("taxa_pin"),
                rs.getString("status"),
                rs.getString("numero_nota"),
                toOffsetDateTime(rs.getTimestamp("emitida_at")),
                toOffsetDateTime(rs.getTimestamp("created_at"))
        );
    }

    private OffsetDateTime toOffsetDateTime(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
