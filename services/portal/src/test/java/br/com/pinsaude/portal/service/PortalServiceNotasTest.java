package br.com.pinsaude.portal.service;

import br.com.pinsaude.portal.dto.NotaPortalResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CBS-22 — a nota do portal traz o regime e os valores de CBS/IBS gravados na nota. */
class PortalServiceNotasTest {

    @Test
    void sqlDasNotas_leRegimeECbsIbsDaNota() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new PortalService(jdbc).getNotas(UUID.randomUUID(), null, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue())
                .contains("nf.regime_tributario", "nf.valor_cbs", "nf.valor_ibs", "nf.fator_iss");
    }

    @Test
    void notaNova_mapeiaRegimeIbsCbs() throws Exception {
        NotaPortalResponse n = mapear(linha("IBS_CBS", 3500L, 600L, new BigDecimal("1.0000")));
        assertThat(n.regimeTributario()).isEqualTo("IBS_CBS");
        assertThat(n.valorCbs()).isEqualTo(3500L);
        assertThat(n.valorIbs()).isEqualTo(600L);
        assertThat(n.fatorIss()).isEqualByComparingTo("1");
        assertThat(n.valorPis()).isZero();
        assertThat(n.valorCofins()).isZero();
    }

    @Test
    void notaSemRegimeGravado_viraLegado() throws Exception {
        NotaPortalResponse n = mapear(linha(null, 0L, 0L, null));
        assertThat(n.regimeTributario()).isEqualTo("LEGADO");
        assertThat(n.valorCbs()).isZero();
        assertThat(n.fatorIss()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static NotaPortalResponse mapear(ResultSet rs) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new PortalService(jdbc).getNotas(UUID.randomUUID(), null, null);
        ArgumentCaptor<RowMapper<NotaPortalResponse>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(anyString(), mapper.capture(), any(Object[].class));
        return mapper.getValue().mapRow(rs, 0);
    }

    private static ResultSet linha(String regime, long cbs, long ibs, BigDecimal fatorIss) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
        when(rs.getString("competencia")).thenReturn("2027-01");
        when(rs.getLong("valor_bruto")).thenReturn(100000L);
        when(rs.getLong("valor_iss")).thenReturn(1781L);
        when(rs.getString("regime_tributario")).thenReturn(regime);
        when(rs.getLong("valor_cbs")).thenReturn(cbs);
        when(rs.getLong("valor_ibs")).thenReturn(ibs);
        when(rs.getBigDecimal("fator_iss")).thenReturn(fatorIss);
        when(rs.getString("status")).thenReturn("EMITIDA");
        return rs;
    }
}
