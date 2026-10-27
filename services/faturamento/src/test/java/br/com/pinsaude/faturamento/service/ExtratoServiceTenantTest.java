package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.config.TenantContext;
import br.com.pinsaude.faturamento.conciliacao.messaging.MatchingProducer;
import br.com.pinsaude.faturamento.domain.*;
import br.com.pinsaude.faturamento.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Extrato importado por gestão (tenant vazio no token). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExtratoServiceTenantTest {

    @Mock ExtratoBancarioRepository   extratoRepo;
    @Mock LancamentoExtratoRepository lancamentoRepo;
    @Mock ConciliacaoRepository       conciliacaoRepo;
    @Mock ProducaoRepository          producaoRepo;
    @Mock MatchingProducer            matchingProducer;

    ExtratoService service;

    static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    static final LocalDate FIM    = LocalDate.of(2026, 10, 31);
    final MockMultipartFile ofx = new MockMultipartFile("arquivo", "extrato.ofx", "text/plain", new byte[]{1});

    @BeforeEach
    void setUp() {
        service = new ExtratoService(extratoRepo, lancamentoRepo, conciliacaoRepo, producaoRepo,
                List.of(), matchingProducer);
    }

    @AfterEach
    void limparTenant() { TenantContext.clear(); }

    @Test
    void upload_gestaoSemEmpresa_retorna400() {
        TenantContext.set("");

        assertThatThrownBy(() -> service.upload(ofx, BancoEnum.OUTRO, INICIO, FIM, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(extratoRepo);
    }

    @Test
    void upload_gestaoComEmpresa_usaCnpjDaEmpresaComoTenant() {
        TenantContext.set("");
        when(extratoRepo.existsByNomeArquivoAndPeriodoInicioAndPeriodoFimAndCnpjIdTenant(
                any(), any(), any(), any())).thenReturn(true); // para no 409, logo após resolver o tenant

        assertThatThrownBy(() -> service.upload(ofx, BancoEnum.OUTRO, INICIO, FIM, "11.222.333/0001-81"))
                .isInstanceOf(ResponseStatusException.class);
        verify(extratoRepo).existsByNomeArquivoAndPeriodoInicioAndPeriodoFimAndCnpjIdTenant(
                "extrato.ofx", INICIO, FIM, "11222333000181");
    }

    @Test
    void upload_usuarioComTenant_ignoraEmpresaInformada() {
        TenantContext.set("11222333000181");
        when(extratoRepo.existsByNomeArquivoAndPeriodoInicioAndPeriodoFimAndCnpjIdTenant(
                any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.upload(ofx, BancoEnum.OUTRO, INICIO, FIM, "99.999.999/0001-99"))
                .isInstanceOf(ResponseStatusException.class);
        verify(extratoRepo).existsByNomeArquivoAndPeriodoInicioAndPeriodoFimAndCnpjIdTenant(
                "extrato.ofx", INICIO, FIM, "11222333000181");
    }

    @Test
    void listarCandidatas_gestao_buscaEmTodosOsTenants() {
        TenantContext.set("");
        when(producaoRepo.findCandidatasParaMatchTodosTenants(any())).thenReturn(List.of());

        service.listarCandidatas();

        verify(producaoRepo).findCandidatasParaMatchTodosTenants(List.of(StatusProducao.EMITIDA.name()));
        verify(producaoRepo, never()).findCandidatasParaMatch(any(), any());
    }

    @Test
    void conciliarManual_extratoSemTenant_adotaTenantDaProducao() {
        UUID extratoId = UUID.randomUUID();
        UUID lancId    = UUID.randomUUID();
        UUID prodId    = UUID.randomUUID();

        LancamentoExtrato l = new LancamentoExtrato();
        l.setExtratoId(extratoId);
        l.setStatusConciliacao(StatusConciliacao.PENDENTE);
        Producao p = new Producao();
        p.setId(prodId);
        p.setStatus(StatusProducao.EMITIDA);
        p.setCnpjIdTenant("11222333000181");
        ExtratoBancario extrato = new ExtratoBancario();
        extrato.setCnpjIdTenant("");

        when(lancamentoRepo.findById(lancId)).thenReturn(Optional.of(l));
        when(producaoRepo.findById(prodId)).thenReturn(Optional.of(p));
        when(conciliacaoRepo.existsByNotaId(prodId)).thenReturn(false);
        when(extratoRepo.findById(extratoId)).thenReturn(Optional.of(extrato));

        service.conciliarManual(lancId, prodId, "user-1", null);

        assertThat(extrato.getCnpjIdTenant()).isEqualTo("11222333000181");
        verify(extratoRepo).save(extrato);
    }
}
