package br.com.pinsaude.faturamento.tomador;

import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.TipoTomador;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.domain.TomadorGrupoFaturamento;
import br.com.pinsaude.faturamento.domain.TomadorGrupoSetor;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import br.com.pinsaude.faturamento.dto.TomadorGrupoFaturamentoRequest;
import br.com.pinsaude.faturamento.dto.TomadorGrupoFaturamentoResponse;
import br.com.pinsaude.faturamento.dto.TomadorGrupoSetorRequest;
import br.com.pinsaude.faturamento.dto.TomadorModalidadeRequest;
import br.com.pinsaude.faturamento.dto.TomadorModalidadeResponse;
import br.com.pinsaude.faturamento.dto.TomadorServicoOperacionalRequest;
import br.com.pinsaude.faturamento.dto.TomadorServicoOperacionalResponse;
import br.com.pinsaude.faturamento.port.ConsultaCnpjPort;
import br.com.pinsaude.faturamento.repository.FrequenciaItemRepository;
import br.com.pinsaude.faturamento.repository.FrequenciaMedicaRepository;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import br.com.pinsaude.faturamento.repository.TomadorAliquotaRepository;
import br.com.pinsaude.faturamento.repository.TomadorCnaeRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoFaturamentoRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoSetorRepository;
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
    @Mock TomadorGrupoSetorRepository grupoSetorRepo;
    @Mock TomadorModalidadeRepository modalidadeRepo;
    @Mock TomadorServicoOperacionalRepository servicoOperacionalRepo;
    @Mock FrequenciaMedicaRepository frequenciaMedicaRepo;
    @Mock FrequenciaItemRepository frequenciaItemRepo;

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
        when(grupoSetorRepo.findByGrupoIdIn(any())).thenReturn(Collections.emptyList());

        List<TomadorGrupoFaturamentoResponse> result = service.listarGrupos(tomadorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Plantões");
        assertThat(result.get(0).codigoLc116()).isEqualTo("4.01");
        assertThat(result.get(0).servicosOperacionais()).isEmpty();
    }

    @Test
    void listarGrupos_comSetores_incluiSetoresNoResponse() {
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        TomadorServicoOperacional setor = setorFixture(tomadorId);
        TomadorGrupoSetor link = grupoSetorFixture(grupo.getId(), setor.getId());

        when(grupoRepo.findByTomadorIdOrderByOrdemAscNomeAsc(tomadorId)).thenReturn(List.of(grupo));
        when(servicoRepo.findAllById(any())).thenReturn(List.of(servico));
        when(grupoSetorRepo.findByGrupoIdIn(any())).thenReturn(List.of(link));
        when(servicoOperacionalRepo.findAllById(any())).thenReturn(List.of(setor));

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

    @Test
    void removerGrupo_semSetores_removeComSucesso() {
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        when(grupoRepo.findById(grupo.getId())).thenReturn(Optional.of(grupo));
        when(frequenciaMedicaRepo.existsByGrupoId(grupo.getId())).thenReturn(false);

        service.removerGrupo(tomadorId, grupo.getId());

        verify(grupoRepo).delete(grupo);
    }

    @Test
    void removerGrupo_comFrequenciaLancada_lanca409() {
        // bug real de homologação: FK de frequencias_medicas.servico_operacional_id sem
        // ON DELETE CASCADE fazia o DELETE do grupo (que cascateia pro setor) falhar e ser
        // reportado como "Registro duplicado" pelo GlobalExceptionHandler. Checagem hoje é por
        // grupo_id direto na frequência (não mais por setor — o mesmo setor pode estar em vários
        // grupos, ver PINSAUDE catálogo reutilizável).
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        when(grupoRepo.findById(grupo.getId())).thenReturn(Optional.of(grupo));
        when(frequenciaMedicaRepo.existsByGrupoId(grupo.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.removerGrupo(tomadorId, grupo.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("existem frequências médicas lançadas");
        verify(grupoRepo, never()).delete(any());
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

    @Test
    void removerModalidade_semUso_removeComSucesso() {
        TomadorModalidade m = modalidadeFixture(tomadorId);
        when(modalidadeRepo.findById(m.getId())).thenReturn(Optional.of(m));

        service.removerModalidade(tomadorId, m.getId());

        verify(modalidadeRepo).delete(m);
    }

    @Test
    void removerModalidade_comItemDeFrequencia_lanca409() {
        TomadorModalidade m = modalidadeFixture(tomadorId);
        when(modalidadeRepo.findById(m.getId())).thenReturn(Optional.of(m));
        when(frequenciaItemRepo.existsByModalidadeId(m.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.removerModalidade(tomadorId, m.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("existem plantões ou frequências lançados");
        verify(modalidadeRepo, never()).delete(any());
    }

    @Test
    void removerModalidade_comFrequenciaFixa_lanca409() {
        // modalidade fixada na frequência (Diarista, PINSAUDE-13.26) sem nenhum item lançado ainda
        TomadorModalidade m = modalidadeFixture(tomadorId);
        when(modalidadeRepo.findById(m.getId())).thenReturn(Optional.of(m));
        when(frequenciaItemRepo.existsByModalidadeId(m.getId())).thenReturn(false);
        when(frequenciaMedicaRepo.existsByModalidadeId(m.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.removerModalidade(tomadorId, m.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("existem plantões ou frequências lançados");
        verify(modalidadeRepo, never()).delete(any());
    }

    // ─── Serviços operacionais (catálogo por tomador) ─────────────────────────

    @Test
    void listarServicosOperacionais_retornaLista() {
        TomadorServicoOperacional s = setorFixture(tomadorId);
        when(servicoOperacionalRepo.findByTomadorIdOrderByNomeAsc(tomadorId)).thenReturn(List.of(s));

        List<TomadorServicoOperacionalResponse> result = service.listarServicosOperacionais(tomadorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Emergência Cardiológica");
    }

    @Test
    void criarServicoOperacional_valido_salva() {
        when(servicoOperacionalRepo.save(any())).thenAnswer(inv -> {
            TomadorServicoOperacional ss = inv.getArgument(0);
            try {
                var f = TomadorServicoOperacional.class.getDeclaredField("id");
                f.setAccessible(true); f.set(ss, UUID.randomUUID());
            } catch (Exception ignored) {}
            return ss;
        });

        TomadorServicoOperacionalRequest req = new TomadorServicoOperacionalRequest("UTI-CARDIOLÓGICA", "UTI", true);

        TomadorServicoOperacionalResponse resp = service.criarServicoOperacional(tomadorId, req);

        assertThat(resp.nome()).isEqualTo("UTI-CARDIOLÓGICA");
        assertThat(resp.categoria()).isEqualTo("UTI");
        assertThat(resp.tomadorId()).isEqualTo(tomadorId);
        verify(servicoOperacionalRepo).save(any());
    }

    @Test
    void criarServicoOperacional_categoriaEmBranco_salvaComoNull() {
        when(servicoOperacionalRepo.save(any())).thenAnswer(inv -> {
            TomadorServicoOperacional ss = inv.getArgument(0);
            try {
                var f = TomadorServicoOperacional.class.getDeclaredField("id");
                f.setAccessible(true); f.set(ss, UUID.randomUUID());
            } catch (Exception ignored) {}
            return ss;
        });

        TomadorServicoOperacionalRequest req = new TomadorServicoOperacionalRequest("Emergência", "   ", true);

        TomadorServicoOperacionalResponse resp = service.criarServicoOperacional(tomadorId, req);

        assertThat(resp.categoria()).isNull();
    }

    @Test
    void removerServicoOperacional_outroTomador_lanca404() {
        TomadorServicoOperacional outro = setorFixture(UUID.randomUUID());
        when(servicoOperacionalRepo.findById(outro.getId())).thenReturn(Optional.of(outro));

        assertThatThrownBy(() -> service.removerServicoOperacional(tomadorId, outro.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Serviço operacional não encontrado");
    }

    @Test
    void removerServicoOperacional_semFrequencia_removeComSucesso() {
        TomadorServicoOperacional s = setorFixture(tomadorId);
        when(servicoOperacionalRepo.findById(s.getId())).thenReturn(Optional.of(s));
        when(frequenciaMedicaRepo.existsByServicoOperacionalId(s.getId())).thenReturn(false);

        service.removerServicoOperacional(tomadorId, s.getId());

        verify(servicoOperacionalRepo).delete(s);
    }

    @Test
    void removerServicoOperacional_comFrequenciaVinculada_lanca409() {
        // bug real de homologação (tomador FGH/Hospital Dom Helder, setor "Emergência
        // Cardiológica"): DELETE falhava por violação de FK e o GlobalExceptionHandler reportava
        // "Registro duplicado" -- a causa real é frequência médica já lançada nesse setor.
        TomadorServicoOperacional s = setorFixture(tomadorId);
        when(servicoOperacionalRepo.findById(s.getId())).thenReturn(Optional.of(s));
        when(frequenciaMedicaRepo.existsByServicoOperacionalId(s.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.removerServicoOperacional(tomadorId, s.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("existem frequências médicas lançadas");
        verify(servicoOperacionalRepo, never()).delete(any());
    }

    // ─── Vínculo Grupo ↔ Setor (N:N) — catálogo reutilizável entre grupos ─────

    @Test
    void adicionarSetorAoGrupo_grupoDeOutroTomador_lanca404() {
        UUID grupoId = UUID.randomUUID();
        TomadorGrupoFaturamento outroGrupo = grupoFixture(UUID.randomUUID(), servico.getId());
        when(grupoRepo.findById(grupoId)).thenReturn(Optional.of(outroGrupo));

        TomadorGrupoSetorRequest req = new TomadorGrupoSetorRequest(UUID.randomUUID());

        assertThatThrownBy(() -> service.adicionarSetorAoGrupo(tomadorId, grupoId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Grupo não encontrado");
    }

    @Test
    void adicionarSetorAoGrupo_jaVinculado_lanca409() {
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        TomadorServicoOperacional setor = setorFixture(tomadorId);
        when(grupoRepo.findById(grupo.getId())).thenReturn(Optional.of(grupo));
        when(servicoOperacionalRepo.findById(setor.getId())).thenReturn(Optional.of(setor));
        when(grupoSetorRepo.existsByGrupoIdAndSetorId(grupo.getId(), setor.getId())).thenReturn(true);

        TomadorGrupoSetorRequest req = new TomadorGrupoSetorRequest(setor.getId());

        assertThatThrownBy(() -> service.adicionarSetorAoGrupo(tomadorId, grupo.getId(), req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("já está vinculado");
    }

    @Test
    void adicionarSetorAoGrupo_valido_criaVinculo() {
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        TomadorServicoOperacional setor = setorFixture(tomadorId);
        when(grupoRepo.findById(grupo.getId())).thenReturn(Optional.of(grupo));
        when(servicoOperacionalRepo.findById(setor.getId())).thenReturn(Optional.of(setor));
        when(grupoSetorRepo.existsByGrupoIdAndSetorId(grupo.getId(), setor.getId())).thenReturn(false);

        TomadorGrupoSetorRequest req = new TomadorGrupoSetorRequest(setor.getId());
        TomadorServicoOperacionalResponse resp = service.adicionarSetorAoGrupo(tomadorId, grupo.getId(), req);

        assertThat(resp.id()).isEqualTo(setor.getId());
        verify(grupoSetorRepo).save(any());
    }

    @Test
    void removerSetorDoGrupo_naoVinculado_lanca404() {
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        UUID setorId = UUID.randomUUID();
        when(grupoRepo.findById(grupo.getId())).thenReturn(Optional.of(grupo));
        when(grupoSetorRepo.existsByGrupoIdAndSetorId(grupo.getId(), setorId)).thenReturn(false);

        assertThatThrownBy(() -> service.removerSetorDoGrupo(tomadorId, grupo.getId(), setorId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não está vinculado");
    }

    @Test
    void removerSetorDoGrupo_comFrequenciaLancada_lanca409() {
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        UUID setorId = UUID.randomUUID();
        when(grupoRepo.findById(grupo.getId())).thenReturn(Optional.of(grupo));
        when(grupoSetorRepo.existsByGrupoIdAndSetorId(grupo.getId(), setorId)).thenReturn(true);
        when(frequenciaMedicaRepo.existsByGrupoIdAndServicoOperacionalId(grupo.getId(), setorId)).thenReturn(true);

        assertThatThrownBy(() -> service.removerSetorDoGrupo(tomadorId, grupo.getId(), setorId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("existem frequências médicas lançadas");
        verify(grupoSetorRepo, never()).deleteByGrupoIdAndSetorId(any(), any());
    }

    @Test
    void removerSetorDoGrupo_semFrequencia_removeVinculo() {
        TomadorGrupoFaturamento grupo = grupoFixture(tomadorId, servico.getId());
        UUID setorId = UUID.randomUUID();
        when(grupoRepo.findById(grupo.getId())).thenReturn(Optional.of(grupo));
        when(grupoSetorRepo.existsByGrupoIdAndSetorId(grupo.getId(), setorId)).thenReturn(true);
        when(frequenciaMedicaRepo.existsByGrupoIdAndServicoOperacionalId(grupo.getId(), setorId)).thenReturn(false);

        service.removerSetorDoGrupo(tomadorId, grupo.getId(), setorId);

        verify(grupoSetorRepo).deleteByGrupoIdAndSetorId(grupo.getId(), setorId);
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

    private TomadorServicoOperacional setorFixture(UUID tomadorId) {
        TomadorServicoOperacional s = new TomadorServicoOperacional();
        try {
            var f = TomadorServicoOperacional.class.getDeclaredField("id");
            f.setAccessible(true); f.set(s, UUID.randomUUID());
        } catch (Exception ignored) {}
        s.setTomadorId(tomadorId);
        s.setNome("Emergência Cardiológica");
        s.setAtivo(true);
        return s;
    }

    private TomadorGrupoSetor grupoSetorFixture(UUID grupoId, UUID setorId) {
        TomadorGrupoSetor link = new TomadorGrupoSetor();
        try {
            var f = TomadorGrupoSetor.class.getDeclaredField("id");
            f.setAccessible(true); f.set(link, UUID.randomUUID());
        } catch (Exception ignored) {}
        link.setGrupoId(grupoId);
        link.setSetorId(setorId);
        return link;
    }
}
