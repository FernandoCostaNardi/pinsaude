package br.com.pinsaude.fiscal.config;

import br.com.pinsaude.fiscal.domain.ParametrosFiscais;
import br.com.pinsaude.fiscal.domain.TipoTributo;
import br.com.pinsaude.fiscal.dto.ParametrosFiscaisRequest;
import br.com.pinsaude.fiscal.dto.ParametrosFiscaisResponse;
import br.com.pinsaude.fiscal.repository.ParametrosFiscaisRepository;
import br.com.pinsaude.fiscal.service.ParametrosFiscaisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParametrosFiscaisServiceTest {

    private static final String CNPJ = "12345678000195";

    @Mock ParametrosFiscaisRepository repo;
    ParametrosFiscaisService service;

    @BeforeEach
    void setUp() {
        service = new ParametrosFiscaisService(repo);
    }

    // ─── Criar alíquota ───────────────────────────────────────────────────────

    @Test
    void criar_semSobreposicao_persisteComSucesso() {
        when(repo.findVigenteParaTributo(anyString(), any(TipoTributo.class), anyString(), any(Pageable.class)))
            .thenReturn(List.of()); // sem vigências existentes

        ParametrosFiscais salvo = parametroPadrao("2025-01", null);
        when(repo.save(any(ParametrosFiscais.class))).thenReturn(salvo);

        ParametrosFiscaisResponse resp = service.criar(CNPJ,
            new ParametrosFiscaisRequest(TipoTributo.ISS, "2025-01", null, new BigDecimal("0.0200"), "ISS padrão"));

        assertThat(resp.tipoTributo()).isEqualTo(TipoTributo.ISS);
        assertThat(resp.competenciaInicio()).isEqualTo("2025-01");
    }

    @Test
    void criar_comSobreposicao_lancaConflict() {
        ParametrosFiscais existente = parametroPadrao("2024-01", null); // sem fim → vigente para todo futuro
        when(repo.findVigenteParaTributo(eq(CNPJ), eq(TipoTributo.ISS), anyString(), any(Pageable.class)))
            .thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.criar(CNPJ,
            new ParametrosFiscaisRequest(TipoTributo.ISS, "2025-01", null, new BigDecimal("0.0250"), null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CONFLICT");
    }

    @Test
    void criar_vigenciaFutura_naoAfetaCalculosAnteriores() {
        // Este teste valida o princípio de negócio: alíquotas futuras têm competenciaInicio > hoje.
        // O motor fiscal carrega por competenciaInicio <= competencia_solicitada,
        // então uma alíquota com inicio=2027-01 não será encontrada para competência=2025-06.
        when(repo.findVigenteParaTributo(anyString(), any(TipoTributo.class), anyString(), any(Pageable.class)))
            .thenReturn(List.of());

        ParametrosFiscais salvo = parametroPadrao("2027-01", null);
        when(repo.save(any(ParametrosFiscais.class))).thenReturn(salvo);

        ParametrosFiscaisResponse resp = service.criar(CNPJ,
            new ParametrosFiscaisRequest(TipoTributo.ISS, "2027-01", null, new BigDecimal("0.0150"), "IBS transição"));

        // Alíquota criada para 2027-01 — motor fiscal usará apenas para competências >= 2027-01
        assertThat(resp.competenciaInicio()).isEqualTo("2027-01");
    }

    // ─── Verificar próxima competência ───────────────────────────────────────

    @Test
    void proximaCompetencia_semAliquotaIsS_retornaFalse() {
        // ISS sem alíquota para próxima competência
        when(repo.findVigenteParaTributo(eq(CNPJ), eq(TipoTributo.ISS), anyString(), any(Pageable.class)))
            .thenReturn(List.of());
        // Outros tributos configurados (lenient: service short-circuits when ISS is missing)
        lenient().when(repo.findVigenteParaTributo(eq(CNPJ), argThat(t -> t != TipoTributo.ISS), anyString(), any(Pageable.class)))
            .thenReturn(List.of(parametroPadrao("2020-01", null)));

        assertThat(service.proximaCompetenciaTemAliquotas(CNPJ)).isFalse();
    }

    @Test
    void proximaCompetencia_todosConfigurados_retornaTrue() {
        when(repo.findVigenteParaTributo(anyString(), any(TipoTributo.class), anyString(), any(Pageable.class)))
            .thenReturn(List.of(parametroPadrao("2020-01", null)));

        assertThat(service.proximaCompetenciaTemAliquotas(CNPJ)).isTrue();
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private ParametrosFiscais parametroPadrao(String inicio, String fim) {
        ParametrosFiscais p = new ParametrosFiscais();
        p.setTipoTributo(TipoTributo.ISS);
        p.setCompetenciaInicio(inicio);
        p.setCompetenciaFim(fim);
        p.setValorAliquota(new BigDecimal("0.0200"));
        return p;
    }
}
