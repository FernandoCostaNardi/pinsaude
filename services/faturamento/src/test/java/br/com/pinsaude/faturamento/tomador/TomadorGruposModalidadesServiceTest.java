package br.com.pinsaude.faturamento.tomador;

import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.TipoTomador;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.domain.TomadorGrupoFaturamento;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import br.com.pinsaude.faturamento.dto.TomadorGrupoFaturamentoRequest;
import br.com.pinsaude.faturamento.dto.TomadorGrupoFaturamentoResponse;
import br.com.pinsaude.faturamento.dto.TomadorModalidadeRequest;
import br.com.pinsaude.faturamento.dto.TomadorModalidadeResponse;
import br.com.pinsaude.faturamento.dto.TomadorServicoOperacionalRequest;
import br.com.pinsaude.faturamento.dto.TomadorServicoOperacionalResponse;
import br.com.pinsaude.faturamento.port.ConsultaCnpjPort;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import br.com.pinsaude.faturamento.repository.TomadorAliquotaRepository;
import br.com.pinsaude.faturamento.repository.TomadorCnaeRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoFaturamentoRepository;
import br.com.pinsaude.faturamento.repository.TomadorModalidadeRepository;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoOperacionalRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoRepository;
import br.com.pinsaude.faturamento.service.CryptoService;
import br.com.pinsaude.faturamento.service.TomadorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TomadorGruposModalidadesServiceTest {

    @Mock TomadorRepository repo;
    @Mock CryptoService crypto;
    @Mock ConsultaCnpjPort consultaCnpjPort;
    @Mock TomadorAliquotaRepository aliquotaRepo;
    @Mock TomadorCnaeRepository cnaeRepo;
    @Mock TomadorServicoRepository servicoVinculoRepo;
    @Mock ServicoRepository servicoRepo;
    @Mock TomadorGrupoFaturamentoRepository grupoRepo;
    @Mock TomadorModalidadeRepository modalidadeRepo;
    @Mock TomadorServicoOperacionalRepository servicoOperacionalRepo;

    @InjectMocks TomadorService service;

    private UUID tomadorId;
    private Tomador tomador;
    private Servico servico;

    @BeforeEach
    void setUp() {
        tomadorId = UUID.randomUUID();
        tomador = new Tomador();
        tomador.setId(tomadorId);
        tomador.setTipo(TipoTomador.HOSPITAL);
        tomador.setRazaoSocialNome("Hospital Test");
        tomador.setCnpjCpfTomadorCriptografado(new byte[]{1});
        tomador.setIndicadorRetencaoFederal(false);
        tomador.setIndicadorRetencaoIss(false);

        servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setCodigoLc116("4.01");
        servico.setDescricaoPadrao("Medicina e biomedicina");

        when(repo.findById(tomadorId)).thenReturn(Optional.of(tomador));
        when(aliquotaRepo.findByTomadorId(any())).thenReturn(Collections.emptyList());
        when(cnaeRepo.findByTomadorId(any())).thenReturn(Collections.emptyList());
        when(servicoVinculoRepo.findByTomadorId(any())).thenReturn(Collections.emptyList());
    }

    // ─── Grupos de faturamento ────────────────────────────────────────────────

    @Test
    void listarGrupos_tomadorExistente_retornaLista() {
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        when(grupoRepo.findByTomadorIdOrderByOrdemAscNomeAsc(tomadorId)).thenReturn(List.of(grupo));
        when(servicoRepo.findAllById(any())).thenReturn(List.of(servico));
        when(servicoOperacionalRepo.findByGrupoIdOrderByNomeAsc(grupo.getId())).thenReturn(Collections.emptyList());

        List<TomadorGrupoFaturamentoResponse> result = service.listarGrupos(tomadorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Plantões");
        assertThat(result.get(0).codigoLc116()).isEqualTo("4.01");
        assertThat(result.get(0).servicosOperacionais()).isEmpty();
    }

    @Test
    void listarGrupos_comSetores_incluiSetoresNoResponse() {
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        TomadorServicoOperacional setor = setorFixture(tomadorId, grupo.getId());

        when(grupoRepo.findByTomadorIdOrderByOrdemAscNomeAsc(tomadorId)).thenReturn(List.of(grupo));
        when(servicoRepo.findAllById(any())).thenReturn(List.of(servico));
        when(servicoOperacionalRepo.findByGrupoIdOrderByNomeAsc(grupo.getId())).thenReturn(List.of(setor));

        List<TomadorGrupoFaturamentoResponse> result = service.listarGrupos(tomadorId);

        assertThat(result.get(0).servicosOperacionais()).hasSize(1);
        assertThat(result.get(0).servicosOperacionais().get(0).nome()).isEqualTo("Emergência Cardiológica");
    }

    @Test
    void criarGrupo_servicoInexistente_lanca400() {
        UUID servicoInexistente = UUID.randomUUID();
        when(servicoRepo.findById(servicoInexistente)).thenReturn(Optional.empty());

        TomadorGrupoFaturamentoRequest req = new TomadorGrupoFaturamentoRequest(
            servicoInexistente, "Grupo X",
            "Prestação de serviços referente a {competencia}.", 1, true);

        assertThatThrownBy(() -> service.criarGrupo(tomadorId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Serviço LC116 não encontrado");
    }

    @Test
    void criarGrupo_valido_salvaNoBanco() {
        when(servicoRepo.findById(servico.getId())).thenReturn(Optional.of(servico));
        when(grupoRepo.save(any())).thenAnswer(inv -> {
            TomadorGrupoFaturamento g = inv.getArgument(0);
            try {
                var f = TomadorGrupoFaturamento.class.getDeclaredField("id");
                f.setAccessible(true); f.set(g, UUID.randomUUID());
            } catch (Exception ignored) {}
            return g;
        });

        TomadorGrupoFaturamentoRequest req = new TomadorGrupoFaturamentoRequest(
            servico.getId(), "Plantões",
            "Prestação de serviços médicos referente a {competencia}.", 1, true);

        TomadorGrupoFaturamentoResponse resp = service.criarGrupo(tomadorId, req);

        assertThat(resp.nome()).isEqualTo("Plantões");
        assertThat(resp.descricaoNota()).contains("{competencia}");
        assertThat(resp.codigoLc116()).isEqualTo("4.01");
        verify(grupoRepo).save(any());
    }

    @Test
    void atualizarGrupo_naoEncontrado_lanca404() {
        UUID grupoId = UUID.randomUUID();
        when(grupoRepo.findById(grupoId)).thenReturn(Optional.empty());

        TomadorGrupoFaturamentoRequest req = new TomadorGrupoFaturamentoRequest(
            servico.getId(), "Novo", "desc {competencia}", 0, true);

        assertThatThrownBy(() -> service.atualizarGrupo(tomadorId, grupoId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Grupo não encontrado");
    }

    @Test
    void removerGrupo_outroTomador_lanca404() {
        UUID grupoId = UUID.randomUUID();
        TomadorGrupoFaturamento outroGrupo = grupoFixture(UUID.randomUUID(), servico.getId());
        when(grupoRepo.findById(grupoId)).thenReturn(Optional.of(outroGrupo));

        assertThatThrownBy(() -> service.removerGrupo(tomadorId, grupoId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Grupo não encontrado");
    }

    // ─── Modalidades ──────────────────────────────────────────────────────────

    @Test
    void listarModalidades_tomadorExistente_retornaLista() {
        TomadorModalidade m = modalidadeFixture(tomadorId);
        when(modalidadeRepo.findByTomadorIdOrderByNomeAsc(tomadorId)).thenReturn(List.of(m));

        List<TomadorModalidadeResponse> result = service.listarModalidades(tomadorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("plantão 12h noturno");
        assertThat(result.get(0).turno()).isEqualTo("NOTURNO");
        assertThat(result.get(0).valorCentavos()).isEqualTo(1_000_000L);
    }

    @Test
    void criarModalidade_valida_salvaNoBanco() {
        stubSaveComId();

        TomadorModalidadeRequest req = new TomadorModalidadeRequest(
            "plantão 12h noturno", "PLANTONISTA", "NOTURNO", "19:00 as 07:00",
            BigDecimal.valueOf(12), 1_000_000L, 0L, true, null);

        TomadorModalidadeResponse resp = service.criarModalidade(tomadorId, req);

        assertThat(resp.nome()).isEqualTo("plantão 12h noturno");
        assertThat(resp.tipo()).isEqualTo("PLANTONISTA");
        assertThat(resp.turno()).isEqualTo("NOTURNO");
        assertThat(resp.horas()).isEqualByComparingTo(BigDecimal.valueOf(12));
        verify(modalidadeRepo).save(any());
    }

    @Test
    void criarModalidade_horasLivres_aceitaQualquerQuantidade() {
        stubSaveComId();

        TomadorModalidadeRequest req = new TomadorModalidadeRequest(
            "Diária 10h", "PLANTONISTA", "DIURNO", "07:00 as 17:00",
            BigDecimal.valueOf(10), 800_000L, 0L, true, null);

        TomadorModalidadeResponse resp = service.criarModalidade(tomadorId, req);

        assertThat(resp.horas()).isEqualByComparingTo(BigDecimal.valueOf(10));
    }

    @Test
    void criarModalidade_plantonistaSemTurno_lanca422() {
        // PINSAUDE-13.22: turno passa a ser obrigatório pro tipo Plantonista (reverte V25)
        TomadorModalidadeRequest req = new TomadorModalidadeRequest(
            "Diária 15h", "PLANTONISTA", null, "07:00 as 22:00",
            BigDecimal.valueOf(15), 900_000L, 0L, true, null);

        assertThatThrownBy(() -> service.criarModalidade(tomadorId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Turno é obrigatório para modalidade do tipo Plantonista");
    }

    @Test
    void criarModalidade_plantonistaSemHorario_lanca422() {
        // PINSAUDE-13.22: horário passa a ser obrigatório pro tipo Plantonista (reverte V26)
        TomadorModalidadeRequest req = new TomadorModalidadeRequest(
            "Plantão 20h", "PLANTONISTA", "DIURNO", null, BigDecimal.valueOf(20), 1_100_000L, 0L, true, null);

        assertThatThrownBy(() -> service.criarModalidade(tomadorId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Horário é obrigatório para modalidade do tipo Plantonista");
    }

    @Test
    void criarModalidade_plantonistaSemHoras_lanca422() {
        TomadorModalidadeRequest req = new TomadorModalidadeRequest(
            "Diária incompleta", "PLANTONISTA", "DIURNO", "07:00 as 17:00", null, 800_000L, 0L, true, null);

        assertThatThrownBy(() -> service.criarModalidade(tomadorId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("obrigatórias para modalidade do tipo Plantonista");
    }

    // ─── Modalidade Diarista ───────────────────────────────────────────────────

    @Test
    void criarModalidade_diarista_salva() {
        stubSaveComId();

        TomadorModalidadeRequest req = new TomadorModalidadeRequest(
            "Diarista 20h/semana", "DIARISTA", null, null, null, 1_500_000L, 0L, true,
            BigDecimal.valueOf(20));

        TomadorModalidadeResponse resp = service.criarModalidade(tomadorId, req);

        assertThat(resp.tipo()).isEqualTo("DIARISTA");
        assertThat(resp.horasSemanais()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(resp.valorCentavos()).isEqualTo(1_500_000L);
        assertThat(resp.turno()).isNull();
        assertThat(resp.horario()).isNull();
        assertThat(resp.horas()).isNull();
    }

    @Test
    void criarModalidade_diarista_zeraTurnoHorarioHoras() {
        stubSaveComId();

        // Mesmo que o request venha com turno/horário/horas, Diarista os ignora (zera)
        TomadorModalidadeRequest req = new TomadorModalidadeRequest(
            "Diarista 20h/semana", "DIARISTA", "DIURNO", "07:00 as 19:00", BigDecimal.valueOf(12),
            1_500_000L, 0L, true, BigDecimal.valueOf(20));

        TomadorModalidadeResponse resp = service.criarModalidade(tomadorId, req);

        assertThat(resp.turno()).isNull();
        assertThat(resp.horario()).isNull();
        assertThat(resp.horas()).isNull();
    }

    @Test
    void criarModalidade_diaristaSemHorasSemanais_lanca422() {
        TomadorModalidadeRequest req = new TomadorModalidadeRequest(
            "Diarista sem meta", "DIARISTA", null, null, null, 1_500_000L, 0L, true, null);

        assertThatThrownBy(() -> service.criarModalidade(tomadorId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Horas semanais são obrigatórias para modalidade do tipo Diarista");
    }

    private void stubSaveComId() {
        when(modalidadeRepo.save(any())).thenAnswer(inv -> {
            TomadorModalidade mm = inv.getArgument(0);
            try {
                var f = TomadorModalidade.class.getDeclaredField("id");
                f.setAccessible(true); f.set(mm, UUID.randomUUID());
            } catch (Exception ignored) {}
            return mm;
        });
    }

    @Test
    void atualizarModalidade_naoEncontrada_lanca404() {
        UUID modalidadeId = UUID.randomUUID();
        when(modalidadeRepo.findById(modalidadeId)).thenReturn(Optional.empty());

        TomadorModalidadeRequest req = new TomadorModalidadeRequest(
            "x", "PLANTONISTA", "DIURNO", "07:00 as 19:00", BigDecimal.valueOf(12), 0L, 0L, true, null);

        assertThatThrownBy(() -> service.atualizarModalidade(tomadorId, modalidadeId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Modalidade não encontrada");
    }

    @Test
    void removerModalidade_outroTomador_lanca404() {
        UUID modalidadeId = UUID.randomUUID();
        TomadorModalidade outra = modalidadeFixture(UUID.randomUUID()); // outro tomador
        when(modalidadeRepo.findById(modalidadeId)).thenReturn(Optional.of(outra));

        assertThatThrownBy(() -> service.removerModalidade(tomadorId, modalidadeId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Modalidade não encontrada");
    }

    // ─── Serviços operacionais ────────────────────────────────────────────────

    @Test
    void listarServicosOperacionais_retornaLista() {
        TomadorServicoOperacional s = setorFixture(tomadorId, UUID.randomUUID());
        when(servicoOperacionalRepo.findByTomadorIdOrderByNomeAsc(tomadorId)).thenReturn(List.of(s));

        List<TomadorServicoOperacionalResponse> result = service.listarServicosOperacionais(tomadorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Emergência Cardiológica");
    }

    @Test
    void criarServicoOperacional_grupoDeOutroTomador_lanca400() {
        UUID grupoId = UUID.randomUUID();
        TomadorGrupoFaturamento outroGrupo = grupoFixture(UUID.randomUUID(), servico.getId());
        when(grupoRepo.findById(grupoId)).thenReturn(Optional.of(outroGrupo));

        TomadorServicoOperacionalRequest req = new TomadorServicoOperacionalRequest(
            grupoId, "Emergência", true);

        assertThatThrownBy(() -> service.criarServicoOperacional(tomadorId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Grupo não encontrado");
    }

    @Test
    void criarServicoOperacional_valido_salva() {
        UUID grupoId = UUID.randomUUID();
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        when(grupoRepo.findById(grupoId)).thenReturn(Optional.of(grupo));
        when(servicoOperacionalRepo.save(any())).thenAnswer(inv -> {
            TomadorServicoOperacional ss = inv.getArgument(0);
            try {
                var f = TomadorServicoOperacional.class.getDeclaredField("id");
                f.setAccessible(true); f.set(ss, UUID.randomUUID());
            } catch (Exception ignored) {}
            return ss;
        });

        TomadorServicoOperacionalRequest req = new TomadorServicoOperacionalRequest(
            grupoId, "UTI-CARDIOLÓGICA", true);

        TomadorServicoOperacionalResponse resp = service.criarServicoOperacional(tomadorId, req);

        assertThat(resp.nome()).isEqualTo("UTI-CARDIOLÓGICA");
        assertThat(resp.tomadorId()).isEqualTo(tomadorId);
        verify(servicoOperacionalRepo).save(any());
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private TomadorGrupoFaturamento grupoFixture(UUID tomadorId, UUID servicoId) {
        TomadorGrupoFaturamento g = new TomadorGrupoFaturamento();
        try {
            var f = TomadorGrupoFaturamento.class.getDeclaredField("id");
            f.setAccessible(true); f.set(g, UUID.randomUUID());
        } catch (Exception ignored) {}
        g.setTomadorId(tomadorId);
        g.setServicoLc116Id(servicoId);
        g.setNome("Plantões");
        g.setDescricaoNota("Plantões de {competencia}");
        g.setOrdem(1);
        g.setAtivo(true);
        return g;
    }

    private TomadorModalidade modalidadeFixture(UUID tomadorId) {
        TomadorModalidade m = new TomadorModalidade();
        try {
            var f = TomadorModalidade.class.getDeclaredField("id");
            f.setAccessible(true); f.set(m, UUID.randomUUID());
        } catch (Exception ignored) {}
        m.setTomadorId(tomadorId);
        m.setNome("plantão 12h noturno");
        m.setTurno("NOTURNO");
        m.setHorario("19:00 as 07:00");
        m.setHoras(BigDecimal.valueOf(12));
        m.setValorCentavos(1_000_000L);
        m.setDeslocamentoCentavos(0L);
        m.setAtivo(true);
        return m;
    }

    private TomadorServicoOperacional setorFixture(UUID tomadorId, UUID grupoId) {
        TomadorServicoOperacional s = new TomadorServicoOperacional();
        try {
            var f = TomadorServicoOperacional.class.getDeclaredField("id");
            f.setAccessible(true); f.set(s, UUID.randomUUID());
        } catch (Exception ignored) {}
        s.setTomadorId(tomadorId);
        s.setGrupoId(grupoId);
        s.setNome("Emergência Cardiológica");
        s.setAtivo(true);
        return s;
    }
}
