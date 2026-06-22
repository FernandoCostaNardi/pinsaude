package br.com.pinsaude.fiscal.config;

import br.com.pinsaude.fiscal.domain.HistoricoRegraEquiparacao;
import br.com.pinsaude.fiscal.domain.RegraEquiparacao;
import br.com.pinsaude.fiscal.dto.RegraEquiparacaoRequest;
import br.com.pinsaude.fiscal.dto.RegraEquiparacaoResponse;
import br.com.pinsaude.fiscal.repository.HistoricoRegraEquiparacaoRepository;
import br.com.pinsaude.fiscal.repository.RegraEquiparacaoRepository;
import br.com.pinsaude.fiscal.service.RegraEquiparacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegraEquiparacaoServiceTest {

    private static final String CNPJ   = "12345678000195";
    private static final UUID   ID_REG = UUID.randomUUID();
    private static final String CNAE   = "8610100";
    private static final String LC116  = "4.02";

    @Mock RegraEquiparacaoRepository          repo;
    @Mock HistoricoRegraEquiparacaoRepository histRepo;

    RegraEquiparacaoService service;

    @BeforeEach
    void setUp() {
        service = new RegraEquiparacaoService(repo, histRepo);
    }

    // ─── Criar regra ──────────────────────────────────────────────────────────

    @Test
    void criar_semConflito_persisteRegra() {
        when(repo.findByCnpjIdAndCnaeAndCodigoLc116(CNPJ, CNAE, LC116))
            .thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> {
            RegraEquiparacao r = inv.getArgument(0);
            var f = RegraEquiparacao.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(r, ID_REG);
            return r;
        });

        RegraEquiparacaoResponse resp = service.criar(CNPJ,
            new RegraEquiparacaoRequest(CNAE, LC116, true, "REDUZIDA"));

        assertThat(resp.cnae()).isEqualTo(CNAE);
        assertThat(resp.indicadorEquiparacao()).isTrue();
        assertThat(resp.regimePresuncao()).isEqualTo("REDUZIDA");
    }

    @Test
    void criar_comConflito_lancaConflict() {
        when(repo.findByCnpjIdAndCnaeAndCodigoLc116(CNPJ, CNAE, LC116))
            .thenReturn(Optional.of(regraExistente()));

        assertThatThrownBy(() -> service.criar(CNPJ,
            new RegraEquiparacaoRequest(CNAE, LC116, true, "CHEIA")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CONFLICT");
    }

    @Test
    void criar_equiparadoSemRegime_lancaBadRequest() {
        assertThatThrownBy(() -> service.criar(CNPJ,
            new RegraEquiparacaoRequest(CNAE, LC116, true, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("regimePresuncao é obrigatório");
    }

    // ─── Atualizar regra — gera histórico ─────────────────────────────────────

    @Test
    void atualizar_mudaIndicador_geraRegistroHistorico() {
        RegraEquiparacao existente = regraExistente(); // equiparado=false
        when(repo.findById(ID_REG)).thenReturn(Optional.of(existente));
        when(repo.save(any())).thenReturn(existente);
        when(histRepo.findByRegraIdOrderByAlteradoEmDesc(ID_REG)).thenReturn(List.of());
        when(histRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.atualizar(CNPJ, ID_REG,
            new RegraEquiparacaoRequest(CNAE, LC116, true, "CHEIA"));

        // Deve ter sido salvo um registro de histórico para "indicadorEquiparacao"
        ArgumentCaptor<HistoricoRegraEquiparacao> captor =
            ArgumentCaptor.forClass(HistoricoRegraEquiparacao.class);
        verify(histRepo, atLeastOnce()).save(captor.capture());

        HistoricoRegraEquiparacao hist = captor.getAllValues().stream()
            .filter(h -> "indicadorEquiparacao".equals(h.getCampoAlterado()))
            .findFirst().orElseThrow();

        assertThat(hist.getValorAnterior()).isEqualTo("false");
        assertThat(hist.getValorNovo()).isEqualTo("true");
    }

    @Test
    void atualizar_mudaRegime_geraHistoricoDeRegime() {
        RegraEquiparacao existente = regraExistente();
        existente.setIndicadorEquiparacao(true);
        existente.setRegimePresuncao("REDUZIDA");
        when(repo.findById(ID_REG)).thenReturn(Optional.of(existente));
        when(repo.save(any())).thenReturn(existente);
        when(histRepo.findByRegraIdOrderByAlteradoEmDesc(ID_REG)).thenReturn(List.of());
        when(histRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.atualizar(CNPJ, ID_REG,
            new RegraEquiparacaoRequest(CNAE, LC116, true, "CHEIA"));

        ArgumentCaptor<HistoricoRegraEquiparacao> captor =
            ArgumentCaptor.forClass(HistoricoRegraEquiparacao.class);
        verify(histRepo, atLeastOnce()).save(captor.capture());

        HistoricoRegraEquiparacao hist = captor.getAllValues().stream()
            .filter(h -> "regimePresuncao".equals(h.getCampoAlterado()))
            .findFirst().orElseThrow();

        assertThat(hist.getValorAnterior()).isEqualTo("REDUZIDA");
        assertThat(hist.getValorNovo()).isEqualTo("CHEIA");
    }

    @Test
    void atualizar_semMudancas_naoGeraHistorico() {
        RegraEquiparacao existente = regraExistente(); // equiparado=false, regime=null
        when(repo.findById(ID_REG)).thenReturn(Optional.of(existente));
        when(repo.save(any())).thenReturn(existente);
        when(histRepo.findByRegraIdOrderByAlteradoEmDesc(ID_REG)).thenReturn(List.of());

        service.atualizar(CNPJ, ID_REG,
            new RegraEquiparacaoRequest(CNAE, LC116, false, null)); // sem mudança

        verify(histRepo, never()).save(any());
    }

    @Test
    void atualizar_outroTenant_lancaForbidden() {
        RegraEquiparacao existente = regraExistente();
        existente.setCnpjId("99999999000100"); // diferente
        when(repo.findById(ID_REG)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizar(CNPJ, ID_REG,
            new RegraEquiparacaoRequest(CNAE, LC116, false, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("FORBIDDEN");
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private RegraEquiparacao regraExistente() {
        RegraEquiparacao r = new RegraEquiparacao();
        r.setCnpjId(CNPJ);
        r.setCnae(CNAE);
        r.setCodigoLc116(LC116);
        r.setIndicadorEquiparacao(false);
        r.setRegimePresuncao(null);
        // Simula ID via reflection (prePersist não é chamado em unit test)
        try {
            var f = RegraEquiparacao.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(r, ID_REG);
        } catch (Exception e) { throw new RuntimeException(e); }
        return r;
    }
}
