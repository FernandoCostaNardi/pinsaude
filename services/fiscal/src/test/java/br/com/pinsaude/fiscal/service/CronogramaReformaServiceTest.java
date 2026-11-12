package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.CronogramaReforma;
import br.com.pinsaude.fiscal.dto.CronogramaReformaRequest;
import br.com.pinsaude.fiscal.dto.CronogramaReformaResponse;
import br.com.pinsaude.fiscal.repository.CronogramaReformaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CronogramaReformaServiceTest {

    @Mock CronogramaReformaRepository repo;

    CronogramaReformaService service;

    @BeforeEach
    void setUp() {
        service = new CronogramaReformaService(repo);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
            .subject("sub-1").claim("preferred_username", "contador@pinsaude.com.br")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static CronogramaReforma ano(int ano, String cbs, String uf, String mun, String fator) {
        CronogramaReforma c = new CronogramaReforma();
        c.setAno((short) ano);
        c.setAliqCbs(cbs == null ? null : new BigDecimal(cbs));
        c.setAliqIbsUf(uf == null ? null : new BigDecimal(uf));
        c.setAliqIbsMun(mun == null ? null : new BigDecimal(mun));
        c.setFatorIss(new BigDecimal(fator));
        return c;
    }

    @Test
    void listar_calculaIbsTotalEPercentuais() {
        when(repo.findAllByOrderByAnoAsc()).thenReturn(List.of(
            ano(2027, "0.1120", "0.0005", "0.0005", "1.0000"),
            ano(2029, null, null, null, "0.9000")));

        List<CronogramaReformaResponse> lista = service.listar();

        assertThat(lista).hasSize(2);
        CronogramaReformaResponse r2027 = lista.get(0);
        assertThat(r2027.aliqIbs()).isEqualByComparingTo("0.0010");
        assertThat(r2027.aliqCbsPct()).isEqualByComparingTo("11.2");
        assertThat(r2027.aliqIbsPct()).isEqualByComparingTo("0.1");
        CronogramaReformaResponse r2029 = lista.get(1);
        assertThat(r2029.aliqIbs()).isNull();
        assertThat(r2029.aliqCbsPct()).isNull();
        assertThat(r2029.fatorIssPct()).isEqualByComparingTo("90");
    }

    @Test
    void buscar_anoInexistente_404() {
        when(repo.findById((short) 2050)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.buscar(2050))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void atualizar_gravaValoresEUsuario() {
        CronogramaReforma c = ano(2027, "0.1120", "0.0005", "0.0005", "1.0000");
        when(repo.findById((short) 2027)).thenReturn(Optional.of(c));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CronogramaReformaResponse r = service.atualizar(2027, new CronogramaReformaRequest(
            new BigDecimal("0.1110"), new BigDecimal("0.0005"), new BigDecimal("0.0005"),
            new BigDecimal("1.0000"), true, "Resolução do Senado nº X/2026"));

        assertThat(r.aliqCbs()).isEqualByComparingTo("0.1110");
        assertThat(r.aliquotasDefinitivas()).isTrue();
        assertThat(r.fonte()).isEqualTo("Resolução do Senado nº X/2026");
        assertThat(c.getAtualizadoPor()).isEqualTo("contador@pinsaude.com.br");
    }

    @Test
    void atualizar_definitivoSemTodasAliquotas_422() {
        when(repo.findById((short) 2029)).thenReturn(Optional.of(ano(2029, null, null, null, "0.9000")));

        assertThatThrownBy(() -> service.atualizar(2029, new CronogramaReformaRequest(
                new BigDecimal("0.0880"), null, null, new BigDecimal("0.9000"), true, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("422");
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void atualizar_semFonte_mantemFonteAnterior() {
        CronogramaReforma c = ano(2028, "0.1120", "0.0005", "0.0005", "1.0000");
        c.setFonte("PROVISÓRIO");
        when(repo.findById((short) 2028)).thenReturn(Optional.of(c));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.atualizar(2028, new CronogramaReformaRequest(
            new BigDecimal("0.1110"), new BigDecimal("0.0005"), new BigDecimal("0.0005"),
            new BigDecimal("1.0000"), false, null));

        assertThat(c.getFonte()).isEqualTo("PROVISÓRIO");
    }
}
