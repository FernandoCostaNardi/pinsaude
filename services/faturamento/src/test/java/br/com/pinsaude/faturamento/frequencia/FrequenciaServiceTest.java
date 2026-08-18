package br.com.pinsaude.faturamento.frequencia;

import br.com.pinsaude.faturamento.domain.FrequenciaItem;
import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorGrupoFaturamento;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import br.com.pinsaude.faturamento.dto.FrequenciaItemRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaItemResponse;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaEditRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaResponse;
import br.com.pinsaude.faturamento.repository.FrequenciaItemRepository;
import br.com.pinsaude.faturamento.repository.FrequenciaMedicaRepository;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoFaturamentoRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoSetorRepository;
import br.com.pinsaude.faturamento.repository.TomadorModalidadeRepository;
import br.com.pinsaude.faturamento.repository.TomadorOcorrenciaRepository;
import br.com.pinsaude.faturamento.repository.TomadorServicoOperacionalRepository;
import br.com.pinsaude.faturamento.service.FrequenciaService;
import br.com.pinsaude.faturamento.service.StorageService;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FrequenciaServiceTest {

    @Mock FrequenciaMedicaRepository frequenciaRepo;
    @Mock FrequenciaItemRepository   itemRepo;
    @Mock TomadorServicoOperacionalRepository setorRepo;
    @Mock TomadorModalidadeRepository modalidadeRepo;
    @Mock StorageService storageService;
    @Mock MedicoTomadorRepository medicoTomadorRepo;
    @Mock TomadorOcorrenciaRepository ocorrenciaRepo;
    @Mock TomadorGrupoFaturamentoRepository grupoRepo;
    @Mock TomadorGrupoSetorRepository grupoSetorRepo;

    @InjectMocks FrequenciaService service;

    private UUID tomadorId;
    private UUID medicoId;
    private UUID setorId;
    private UUID modalidadeId;
    private UUID grupoId;
    private TomadorServicoOperacional setor;
    private TomadorModalidade modalidade;
    private TomadorGrupoFaturamento grupo;

    @BeforeEach
    void setUp() {
        tomadorId   = UUID.randomUUID();
        medicoId    = UUID.randomUUID();
        setorId     = UUID.randomUUID();
        modalidadeId = UUID.randomUUID();
        grupoId     = UUID.randomUUID();

        setor = new TomadorServicoOperacional();
        setId(setor, setorId);
        setor.setTomadorId(tomadorId);
        setor.setNome("Emergência Cardiológica");
        setor.setAtivo(true);

        grupo = new TomadorGrupoFaturamento();
        setId(grupo, grupoId);
        grupo.setTomadorId(tomadorId);
        grupo.setNome("Plantões e Diárias");
        grupo.setDescricaoNota("Serviços médicos referente a {competencia}.");
        grupo.setOrdem(1);
        grupo.setAtivo(true);

        modalidade = new TomadorModalidade();
        setId(modalidade, modalidadeId);
        modalidade.setTomadorId(tomadorId);
        modalidade.setNome("Plantão 12h Noturno");
        modalidade.setTipo("PLANTONISTA");
        modalidade.setTurno("NOTURNO");
        modalidade.setHorario("19:00 as 07:00");
        modalidade.setHoras(new BigDecimal("12"));
        modalidade.setValorCentavos(150000L);
        modalidade.setDeslocamentoCentavos(10000L);
        modalidade.setAtivo(true);

        when(setorRepo.findById(setorId)).thenReturn(Optional.of(setor));
        when(modalidadeRepo.findById(modalidadeId)).thenReturn(Optional.of(modalidade));
        when(itemRepo.findAll()).thenReturn(Collections.emptyList());
        when(setorRepo.findAllById(any())).thenReturn(List.of(setor));
        when(modalidadeRepo.findAllById(any())).thenReturn(List.of(modalidade));
        when(ocorrenciaRepo.findAllById(any())).thenReturn(List.of());
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(true);
        // Grupo válido por padrão pra qualquer combinação — testes que exercitam especificamente
        // a validação de grupo/vínculo sobrescrevem estes stubs (ver seção dedicada abaixo).
        when(grupoRepo.findById(any())).thenReturn(Optional.of(grupo));
        when(grupoSetorRepo.existsByGrupoIdAndSetorId(any(), any())).thenReturn(true);
    }

    // ─── Criar frequência — Plantonista (sem modalidade/ocorrência fixa) ──────

    @Test
    void criar_plantonista_semModalidade_salvaNoBanco() {
        when(frequenciaRepo.save(any())).thenAnswer(inv -> {
            FrequenciaMedica f = inv.getArgument(0);
            setId(f, UUID.randomUUID());
            return f;
        });

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "PLANTONISTA", null, null);

        FrequenciaMedicaResponse resp = service.criar(req);

        assertThat(resp.competencia()).isEqualTo("2026-07");
        assertThat(resp.tipoMedico()).isEqualTo("PLANTONISTA");
        assertThat(resp.status()).isEqualTo("RASCUNHO");
        assertThat(resp.servicoOperacionalNome()).isEqualTo("Emergência Cardiológica");
        assertThat(resp.totalValorCentavos()).isZero();
        // Plantonista: modalidade escolhida por lançamento — nada fixado na frequência.
        assertThat(resp.modalidadeId()).isNull();
        verify(frequenciaRepo).save(any());
    }

    @Test
    void criar_plantonista_comModalidadeInformada_lanca422() {
        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "PLANTONISTA", modalidadeId, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Modalidade não deve ser informada para Tipo de Escala Plantonista")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(frequenciaRepo, never()).save(any());
    }

    @Test
    void criar_plantonista_comOcorrenciaInformada_lanca422() {
        UUID ocorrenciaId = UUID.randomUUID();
        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "PLANTONISTA", null, ocorrenciaId);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Ocorrência não deve ser informada para Tipo de Escala Plantonista")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(frequenciaRepo, never()).save(any());
    }

    @Test
    void criar_plantonista_semChecagemDeDuplicidade_permiteMultiplasFolhas() {
        // Pedido do cliente: o médico pode abrir mais de uma frequência ("folha") Plantonista
        // pro mesmo médico+setor+competência (ex.: uma pra semana, outra pro fim de semana) —
        // sem modalidade fixa, não existe checagem de duplicidade nenhuma pra Plantonista (V34).
        when(frequenciaRepo.save(any())).thenAnswer(inv -> {
            FrequenciaMedica f = inv.getArgument(0);
            setId(f, UUID.randomUUID());
            return f;
        });

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "PLANTONISTA", null, null);

        service.criar(req);
        service.criar(req);

        verify(frequenciaRepo, times(2)).save(any());
        verify(frequenciaRepo, never()).existsByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
            any(), any(), any(), any(), any());
    }

    @Test
    void criar_setorInexistente_lanca404() {
        UUID setorInexistente = UUID.randomUUID();
        when(setorRepo.findById(setorInexistente)).thenReturn(Optional.empty());

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorInexistente, "2026-07", "PLANTONISTA", null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void criar_setorDeOutroTomador_lanca422() {
        UUID outroTomadorId = UUID.randomUUID();

        // setor pertence a tomadorId, mas a requisição informa outroTomadorId
        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            outroTomadorId, medicoId, grupoId, setorId, "2026-07", "PLANTONISTA", null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Setor operacional não pertence ao tomador informado")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void criar_medicoNaoAlocadoAoTomador_lanca422() {
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(false);

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "PLANTONISTA", null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não está alocado a este tomador")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ─── Grupo de Faturamento explícito (catálogo de setores reutilizável) ────

    @Test
    void criar_grupoInexistente_lanca404() {
        UUID grupoInexistente = UUID.randomUUID();
        when(grupoRepo.findById(grupoInexistente)).thenReturn(Optional.empty());

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoInexistente, setorId, "2026-07", "PLANTONISTA", null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Grupo de faturamento não encontrado")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void criar_grupoDeOutroTomador_lanca422() {
        TomadorGrupoFaturamento grupoOutroTomador = new TomadorGrupoFaturamento();
        setId(grupoOutroTomador, grupoId);
        grupoOutroTomador.setTomadorId(UUID.randomUUID());
        when(grupoRepo.findById(grupoId)).thenReturn(Optional.of(grupoOutroTomador));

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "PLANTONISTA", null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Grupo de faturamento não pertence ao tomador informado")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void criar_setorNaoVinculadoAoGrupo_lanca422() {
        when(grupoSetorRepo.existsByGrupoIdAndSetorId(grupoId, setorId)).thenReturn(false);

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "PLANTONISTA", null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Setor operacional não pertence ao grupo de faturamento informado")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void criar_grupoESetorValidos_persisteGrupoIdNaFrequencia() {
        when(frequenciaRepo.save(any())).thenAnswer(inv -> {
            FrequenciaMedica f = inv.getArgument(0);
            setId(f, UUID.randomUUID());
            return f;
        });

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "PLANTONISTA", null, null);

        FrequenciaMedicaResponse resp = service.criar(req);

        assertThat(resp.grupoId()).isEqualTo(grupoId);
    }

    // ─── Criar frequência — Diarista (modalidade obrigatória e fixa) ──────────

    @Test
    void criar_diarista_semModalidade_lanca422() {
        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "DIARISTA", null, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Modalidade é obrigatória para Tipo de Escala Diarista")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(frequenciaRepo, never()).save(any());
    }

    @Test
    void criar_diarista_comModalidade_salvaNoBanco() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));
        when(frequenciaRepo.existsByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
                medicoId, setorId, "2026-07", "DIARISTA", diaristaId)).thenReturn(false);
        when(frequenciaRepo.save(any())).thenAnswer(inv -> {
            FrequenciaMedica f = inv.getArgument(0);
            setId(f, UUID.randomUUID());
            return f;
        });

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "DIARISTA", diaristaId, null);

        FrequenciaMedicaResponse resp = service.criar(req);

        assertThat(resp.tipoMedico()).isEqualTo("DIARISTA");
        assertThat(resp.modalidadeId()).isEqualTo(diaristaId);
        assertThat(resp.modalidadeNome()).isEqualTo("Diarista 20h/semana");
        verify(frequenciaRepo).save(any());
    }

    @Test
    void criar_diarista_duplicataMesmaModalidade_lanca409() {
        when(frequenciaRepo.existsByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
                medicoId, setorId, "2026-07", "DIARISTA", modalidadeId)).thenReturn(true);

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "DIARISTA", modalidadeId, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));

        verify(frequenciaRepo, never()).save(any());
    }

    @Test
    void criar_modalidadeInexistente_lanca404() {
        UUID modalInexistente = UUID.randomUUID();
        when(modalidadeRepo.findById(modalInexistente)).thenReturn(Optional.empty());

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "DIARISTA", modalInexistente, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void criar_modalidadeDeOutroTomador_lanca422() {
        UUID outroTomadorId = UUID.randomUUID();
        UUID modalOutroTomadorId = UUID.randomUUID();
        TomadorModalidade modalOutroTomador = modalidadeDiaristaFixture(modalOutroTomadorId, 1_000_000L, "20");
        modalOutroTomador.setTomadorId(outroTomadorId);
        when(modalidadeRepo.findById(modalOutroTomadorId)).thenReturn(Optional.of(modalOutroTomador));

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "DIARISTA", modalOutroTomadorId, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Modalidade não pertence ao tomador informado")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void criar_modalidadeComTipoIncompativel_lanca422() {
        // tipoMedico da frequência é DIARISTA, mas a modalidade escolhida (fixture padrão do
        // setUp) é PLANTONISTA
        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "DIARISTA", modalidadeId, null);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não pode ser usada numa frequência com Tipo de Escala DIARISTA")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void criar_ocorrenciaInexistente_lanca404() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));
        UUID ocorrenciaInexistente = UUID.randomUUID();
        when(ocorrenciaRepo.findById(ocorrenciaInexistente)).thenReturn(Optional.empty());

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "DIARISTA", diaristaId, ocorrenciaInexistente);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void criar_ocorrenciaDeOutroTomador_lanca422() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));

        UUID outroTomadorId = UUID.randomUUID();
        UUID ocorrenciaOutroTomadorId = UUID.randomUUID();
        TomadorOcorrencia ocorrenciaOutroTomador = ocorrenciaFixture(ocorrenciaOutroTomadorId, "FIXO", null, 5000L);
        ocorrenciaOutroTomador.setTomadorId(outroTomadorId);
        when(ocorrenciaRepo.findById(ocorrenciaOutroTomadorId)).thenReturn(Optional.of(ocorrenciaOutroTomador));

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "DIARISTA", diaristaId, ocorrenciaOutroTomadorId);

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Ocorrência não pertence ao tomador informado")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void criar_diarista_comOcorrencia_salvaOcorrenciaIdEExibeNoResumo() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));

        UUID ocorrenciaId = UUID.randomUUID();
        TomadorOcorrencia ocorrencia = ocorrenciaFixture(ocorrenciaId, "FIXO", null, 5000L);
        when(ocorrenciaRepo.findById(ocorrenciaId)).thenReturn(Optional.of(ocorrencia));
        when(frequenciaRepo.save(any())).thenAnswer(inv -> {
            FrequenciaMedica f = inv.getArgument(0);
            setId(f, UUID.randomUUID());
            return f;
        });

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, grupoId, setorId, "2026-07", "DIARISTA", diaristaId, ocorrenciaId);

        FrequenciaMedicaResponse resp = service.criar(req);

        assertThat(resp.ocorrenciaId()).isEqualTo(ocorrenciaId);
        assertThat(resp.ocorrenciaNome()).isEqualTo("Feriado");
    }

    // ─── Listar ───────────────────────────────────────────────────────────────

    @Test
    void listar_porMedicoId_retornaApenasDoMedico() {
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findByMedicoIdOrderByCompetenciaDescCreatedAtDesc(medicoId))
            .thenReturn(List.of(f));
        when(itemRepo.findAll()).thenReturn(Collections.emptyList());

        List<FrequenciaMedicaResponse> result = service.listar(medicoId, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).medicoId()).isEqualTo(medicoId);
    }

    @Test
    void listar_semFiltros_retornaTodos() {
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findAllByOrderByCompetenciaDescCreatedAtDesc()).thenReturn(List.of(f));

        List<FrequenciaMedicaResponse> result = service.listar(null, null, null, null, null);

        assertThat(result).hasSize(1);
    }

    // ─── Adicionar item ───────────────────────────────────────────────────────

    @Test
    void adicionarItem_valido_snapshotPreco() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), "Normal", null, null, null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.modalidadeNome()).isEqualTo("Plantão 12h Noturno");
        assertThat(resp.valorUnitarioCentavos()).isEqualTo(150000L);
        assertThat(resp.deslocamentoCentavos()).isEqualTo(10000L);
        assertThat(resp.totalItemCentavos()).isEqualTo(160000L);
        assertThat(resp.dataExecucao()).isEqualTo(LocalDate.of(2026, 7, 5));
        verify(itemRepo).save(any());
    }

    @Test
    void adicionarItem_frequenciaFaturada_lanca422() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("FATURADA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, null, null);

        assertThatThrownBy(() -> service.adicionarItem(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void adicionarItem_modalidadeInexistente_lanca404() {
        UUID freqId = UUID.randomUUID();
        UUID modalInexistente = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(modalidadeRepo.findById(modalInexistente)).thenReturn(Optional.empty());

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalInexistente, LocalDate.of(2026, 7, 5), null, null, null, null);

        assertThatThrownBy(() -> service.adicionarItem(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void adicionarItem_plantao_valorPermaneceFlat_semRegressao() {
        // modalidade PLANTONISTA (fixture padrão do setUp) — comportamento flat por lançamento
        // deve continuar idêntico, sem depender de horasTrabalhadas
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, null, null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.valorUnitarioCentavos()).isEqualTo(150000L);
        assertThat(resp.horasTrabalhadas()).isNull();
        assertThat(resp.horaInicio()).isNull();
        assertThat(resp.horaFim()).isNull();
    }

    // ─── PINSAUDE-13.26: modalidade fixa da frequência ignora override do item ─

    @Test
    void adicionarItem_frequenciaComModalidadeFixa_ignoraModalidadeIdDoRequest() {
        UUID freqId = UUID.randomUUID();
        UUID outraModalidadeId = UUID.randomUUID();
        TomadorModalidade outraModalidade = new TomadorModalidade();
        setId(outraModalidade, outraModalidadeId);
        outraModalidade.setTomadorId(tomadorId);
        outraModalidade.setNome("Plantão 6h Diurno");
        outraModalidade.setTipo("PLANTONISTA");
        outraModalidade.setValorCentavos(90000L);
        outraModalidade.setDeslocamentoCentavos(0L);
        when(modalidadeRepo.findById(outraModalidadeId)).thenReturn(Optional.of(outraModalidade));

        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setModalidadeId(modalidadeId); // fixa na criação — "Plantão 12h Noturno" (setUp)
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        // Envia um modalidadeId diferente no request do item — deve ser ignorado
        FrequenciaItemRequest req = new FrequenciaItemRequest(
            outraModalidadeId, LocalDate.of(2026, 7, 5), null, null, null, null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.modalidadeId()).isEqualTo(modalidadeId);
        assertThat(resp.modalidadeNome()).isEqualTo("Plantão 12h Noturno");
        assertThat(resp.valorUnitarioCentavos()).isEqualTo(150000L);
    }

    @Test
    void adicionarItem_frequenciaComModalidadeFixa_semModalidadeIdNoRequest_funcionaNormalmente() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setModalidadeId(modalidadeId);
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        // Formulário simplificado (PINSAUDE-13.26): não envia mais modalidadeId
        FrequenciaItemRequest req = new FrequenciaItemRequest(
            null, LocalDate.of(2026, 7, 5), null, null, null, null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.modalidadeId()).isEqualTo(modalidadeId);
        assertThat(resp.valorUnitarioCentavos()).isEqualTo(150000L);
    }

    @Test
    void adicionarItem_frequenciaComOcorrenciaFixa_naoValoraNemResolveOcorrenciaPorItem() {
        // PINSAUDE-13.26 (ajuste pós-implantação): com modalidade/ocorrência fixas na
        // frequência, o valor da ocorrência deixa de ser aplicado por item (evita inflar o
        // total quando N plantões são lançados) — passa a ser somado uma única vez sobre o
        // valor da modalidade, no nível da frequência (ver FrequenciaMedicaResponseTest /
        // toResponse_comOcorrenciaFixa_...). O item nem resolve mais a ocorrência.
        UUID freqId = UUID.randomUUID();
        UUID ocorrenciaFixaId = UUID.randomUUID();
        UUID outraOcorrenciaId = UUID.randomUUID();

        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setModalidadeId(modalidadeId);
        f.setOcorrenciaId(ocorrenciaFixaId);
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            null, LocalDate.of(2026, 7, 5), null, null, null, outraOcorrenciaId);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.ocorrenciaId()).isNull();
        assertThat(resp.ocorrenciaValorCentavos()).isNull();
        assertThat(resp.totalItemCentavos()).isEqualTo(resp.valorUnitarioCentavos() + resp.deslocamentoCentavos());
        verify(ocorrenciaRepo, never()).findById(any());
    }

    @Test
    void adicionarItem_frequenciaLegadaSemModalidadeFixa_semModalidadeIdNoRequest_lanca422() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07"); // modalidadeId null (legado)
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            null, LocalDate.of(2026, 7, 5), null, null, null, null);

        assertThatThrownBy(() -> service.adicionarItem(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Informe a modalidade para este lançamento")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void adicionarItem_frequenciaLegadaSemModalidadeFixa_comModalidadeIdNoRequest_funcionaComoAntes() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07"); // modalidadeId null (legado)
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, null, null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.modalidadeId()).isEqualTo(modalidadeId);
        assertThat(resp.valorUnitarioCentavos()).isEqualTo(150000L);
    }

    @Test
    void atualizarItem_frequenciaComModalidadeFixa_ignoraModalidadeIdDoRequest() {
        UUID freqId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setModalidadeId(modalidadeId);
        FrequenciaItem item = itemFixture(freqId, modalidadeId, LocalDate.of(2026, 7, 5), null, 150000L);
        setId(item, itemId);
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID outraModalidadeId = UUID.randomUUID();
        FrequenciaItemRequest req = new FrequenciaItemRequest(
            outraModalidadeId, LocalDate.of(2026, 7, 6), null, null, null, null);

        FrequenciaItemResponse resp = service.atualizarItem(freqId, itemId, req);

        assertThat(resp.modalidadeId()).isEqualTo(modalidadeId);
        assertThat(resp.dataExecucao()).isEqualTo(LocalDate.of(2026, 7, 6));
        verify(modalidadeRepo, never()).findById(outraModalidadeId);
    }

    // ─── Excluir frequência (bloqueado só em FATURADA) ─────────────────────────

    @Test
    void excluir_frequenciaRascunho_deletaComSucesso() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        service.excluir(freqId);

        verify(frequenciaRepo).delete(f);
    }

    @Test
    void excluir_frequenciaAguardandoAssinatura_deletaComSucesso() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("AGUARDANDO_ASSINATURA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        service.excluir(freqId);

        verify(frequenciaRepo).delete(f);
    }

    @Test
    void excluir_frequenciaComDocumentoAssinado_removeDoStorageAntes() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("ASSINADA_RECEBIDA");
        f.setDocumentoAssinadoKey("frequencias/" + freqId + "/doc.pdf");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        service.excluir(freqId);

        verify(storageService).delete("frequencias/" + freqId + "/doc.pdf");
        verify(frequenciaRepo).delete(f);
    }

    @Test
    void excluir_frequenciaNaoEncontrada_lanca404() {
        UUID freqId = UUID.randomUUID();
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(freqId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void excluir_frequenciaFaturada_lanca422() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("FATURADA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.excluir(freqId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("já faturada")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(frequenciaRepo, never()).delete(any());
    }

    // ─── Editar frequência (Competência + Setor, bloqueado só em FATURADA) ─────

    @Test
    void atualizar_competenciaESetor_salvaComSucesso() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        f.setModalidadeId(modalidadeId);
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        UUID outroSetorId = UUID.randomUUID();
        TomadorServicoOperacional outroSetor = new TomadorServicoOperacional();
        setId(outroSetor, outroSetorId);
        outroSetor.setTomadorId(tomadorId);
        outroSetor.setNome("UTI");
        when(setorRepo.findById(outroSetorId)).thenReturn(Optional.of(outroSetor));
        when(frequenciaRepo.findByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
                medicoId, outroSetorId, "2026-08", "DIARISTA", modalidadeId)).thenReturn(Optional.empty());

        FrequenciaMedicaEditRequest req = new FrequenciaMedicaEditRequest("2026-08", grupoId, outroSetorId);
        FrequenciaMedicaResponse resp = service.atualizar(freqId, req);

        assertThat(resp.competencia()).isEqualTo("2026-08");
        assertThat(f.getServicoOperacionalId()).isEqualTo(outroSetorId);
        verify(frequenciaRepo).save(f);
    }

    @Test
    void atualizar_setorDeOutroTomador_lanca422() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        UUID setorDeOutroTomadorId = UUID.randomUUID();
        TomadorServicoOperacional setorDeOutroTomador = new TomadorServicoOperacional();
        setId(setorDeOutroTomador, setorDeOutroTomadorId);
        setorDeOutroTomador.setTomadorId(UUID.randomUUID()); // tomador diferente do da frequência
        when(setorRepo.findById(setorDeOutroTomadorId)).thenReturn(Optional.of(setorDeOutroTomador));

        FrequenciaMedicaEditRequest req = new FrequenciaMedicaEditRequest("2026-07", grupoId, setorDeOutroTomadorId);

        assertThatThrownBy(() -> service.atualizar(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não pertence ao tomador")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(frequenciaRepo, never()).save(any());
    }

    @Test
    void atualizar_conflitoComOutraFrequenciaExistente_lanca409() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        f.setModalidadeId(modalidadeId);
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        FrequenciaMedica outraExistente = frequenciaFixture(medicoId, setorId, "2026-08");
        when(frequenciaRepo.findByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
                medicoId, setorId, "2026-08", "DIARISTA", modalidadeId)).thenReturn(Optional.of(outraExistente));

        FrequenciaMedicaEditRequest req = new FrequenciaMedicaEditRequest("2026-08", grupoId, setorId);

        assertThatThrownBy(() -> service.atualizar(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));

        verify(frequenciaRepo, never()).save(any());
    }

    @Test
    void atualizar_mesmaFrequenciaEncontradaNaChecagemDeConflito_naoLancaErro() {
        // A própria frequência sendo editada aparece na busca por (medico, setor, competência,
        // tipo, modalidade) quando os valores não mudam (ou mudam só um dos dois) — não pode ser
        // tratada como conflito.
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        f.setModalidadeId(modalidadeId);
        setId(f, freqId);
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        UUID outroSetorId = UUID.randomUUID();
        TomadorServicoOperacional outroSetor = new TomadorServicoOperacional();
        setId(outroSetor, outroSetorId);
        outroSetor.setTomadorId(tomadorId);
        when(setorRepo.findById(outroSetorId)).thenReturn(Optional.of(outroSetor));
        when(frequenciaRepo.findByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
                medicoId, outroSetorId, "2026-07", "DIARISTA", modalidadeId)).thenReturn(Optional.of(f));

        FrequenciaMedicaEditRequest req = new FrequenciaMedicaEditRequest("2026-07", grupoId, outroSetorId);
        service.atualizar(freqId, req);

        verify(frequenciaRepo).save(f);
    }

    @Test
    void atualizar_plantonista_semChecagemDeConflito_permiteMesmaChave() {
        // Plantonista nunca teve checagem de duplicidade (ver criar()/V34) — editar competência
        // ou setor de uma frequência Plantonista nunca dispara a busca por conflito, mesmo já
        // existindo outra frequência com a mesma chave (medico+setor+competência).
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("PLANTONISTA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        UUID outroSetorId = UUID.randomUUID();
        TomadorServicoOperacional outroSetor = new TomadorServicoOperacional();
        setId(outroSetor, outroSetorId);
        outroSetor.setTomadorId(tomadorId);
        when(setorRepo.findById(outroSetorId)).thenReturn(Optional.of(outroSetor));

        FrequenciaMedicaEditRequest req = new FrequenciaMedicaEditRequest("2026-08", grupoId, outroSetorId);
        service.atualizar(freqId, req);

        verify(frequenciaRepo).save(f);
        verify(frequenciaRepo, never()).findByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
            any(), any(), any(), any(), any());
    }

    @Test
    void atualizar_frequenciaFaturada_lanca422() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("FATURADA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        FrequenciaMedicaEditRequest req = new FrequenciaMedicaEditRequest("2026-08", grupoId, setorId);

        assertThatThrownBy(() -> service.atualizar(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("já faturada")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(frequenciaRepo, never()).save(any());
    }

    // ─── Valoração e coupling da modalidade Diarista (PINSAUDE-13.23) ─────────

    @Test
    void adicionarItem_diarista_valorUnitarioSempreZero() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            diaristaId, LocalDate.of(2026, 7, 6), null, LocalTime.of(7, 0), LocalTime.of(15, 0), null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        // cada lançamento de Diarista vale R$0 — o valor mensal é somado uma única vez no total
        // da frequência (ver testes de buscarPorId abaixo)
        assertThat(resp.valorUnitarioCentavos()).isZero();
        assertThat(resp.horasTrabalhadas()).isEqualByComparingTo("8");
        assertThat(resp.horaInicio()).isEqualTo(LocalTime.of(7, 0));
        assertThat(resp.horaFim()).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void adicionarItem_diarista_horarioAtravessaMeiaNoite_calculaCorretamente() {
        // turno noturno 19:00 às 07:00 do dia seguinte — 12h, mesmo com horaFim < horaInicio
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            diaristaId, LocalDate.of(2026, 7, 6), null, LocalTime.of(19, 0), LocalTime.of(7, 0), null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.horasTrabalhadas()).isEqualByComparingTo("12");
    }

    @Test
    void adicionarItem_diarista_horarioEntradaIgualSaida_lanca422() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            diaristaId, LocalDate.of(2026, 7, 6), null, LocalTime.of(7, 0), LocalTime.of(7, 0), null);

        assertThatThrownBy(() -> service.adicionarItem(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("diferente do horário de entrada")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void adicionarItem_diarista_semHorario_lanca422() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            diaristaId, LocalDate.of(2026, 7, 6), null, null, null, null);

        assertThatThrownBy(() -> service.adicionarItem(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("horário de entrada e saída")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void adicionarItem_diaristaComOcorrencia_permiteESomaSobreValorCadastrado() {
        // Ajuste pós-PINSAUDE-13.25: ocorrências do catálogo passaram a ser permitidas em
        // qualquer Tipo de Escala — o % incide sobre o valor CADASTRADO da modalidade
        // (valorCentavos), igual ao comportamento já existente pra Plantonista.
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));

        UUID ocorrenciaId = UUID.randomUUID();
        TomadorOcorrencia ocorrencia = ocorrenciaFixture(ocorrenciaId, "PERCENTUAL", new BigDecimal("10"), null);
        when(ocorrenciaRepo.findById(ocorrenciaId)).thenReturn(Optional.of(ocorrencia));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            diaristaId, LocalDate.of(2026, 7, 6), null, LocalTime.of(7, 0), LocalTime.of(15, 0), ocorrenciaId);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        // 10% de 1.500.000 (valor mensal cadastrado da modalidade) = 150.000
        assertThat(resp.ocorrenciaValorCentavos()).isEqualTo(150_000L);
        assertThat(resp.valorUnitarioCentavos()).isZero(); // continua 0 — só a ocorrência soma
    }

    @Test
    void adicionarItem_diaristaEmFrequenciaPlantonista_lanca422() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("PLANTONISTA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            diaristaId, LocalDate.of(2026, 7, 6), null, LocalTime.of(7, 0), LocalTime.of(15, 0), null);

        assertThatThrownBy(() -> service.adicionarItem(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não pode ser lançada numa frequência com Tipo de Escala PLANTONISTA")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void adicionarItem_plantonistaEmFrequenciaDiarista_lanca422() {
        // modalidade PLANTONISTA (fixture padrão do setUp) numa frequência marcada Diarista
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 6), null, null, null, null);

        assertThatThrownBy(() -> service.adicionarItem(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não pode ser lançada numa frequência com Tipo de Escala DIARISTA")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void adicionarItem_frequenciaSemTipoMedico_bypassaCoupling() {
        // frequência legada (tipoMedico nulo, anterior ao EPIC-13.11) — sem restrição de coupling
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        when(modalidadeRepo.findById(diaristaId)).thenReturn(Optional.of(diarista));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07"); // tipoMedico fica null
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            diaristaId, LocalDate.of(2026, 7, 6), null, LocalTime.of(7, 0), LocalTime.of(15, 0), null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.valorUnitarioCentavos()).isZero();
    }

    @Test
    void buscarPorId_diarista_totalContaValorMensalUmaUnicaVez() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        setId(f, freqId);

        // 3 dias trabalhados no mês, cada item já persistido com valorUnitarioCentavos=0
        FrequenciaItem item1 = itemFixture(freqId, diaristaId, LocalDate.of(2026, 7, 6), new BigDecimal("8"), 0L);
        FrequenciaItem item2 = itemFixture(freqId, diaristaId, LocalDate.of(2026, 7, 8), new BigDecimal("8"), 0L);
        FrequenciaItem item3 = itemFixture(freqId, diaristaId, LocalDate.of(2026, 7, 13), new BigDecimal("8"), 0L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId))
            .thenReturn(List.of(item1, item2, item3));
        when(modalidadeRepo.findAllById(any())).thenReturn(List.of(diarista));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        // valor mensal fixo (R$15.000) uma única vez, não 3x
        assertThat(resp.totalValorCentavos()).isEqualTo(1_500_000L);
        assertThat(resp.itens()).hasSize(3);
    }

    @Test
    void buscarPorId_diarista_progressoSemanalAbaixoDaMeta() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        setId(f, freqId);

        // segunda-feira 2026-07-06: semana ISO de 06/07 (seg) a 12/07 (dom) — só 12h lançadas de 20h de meta
        FrequenciaItem item1 = itemFixture(freqId, diaristaId, LocalDate.of(2026, 7, 6), new BigDecimal("6"), 0L);
        FrequenciaItem item2 = itemFixture(freqId, diaristaId, LocalDate.of(2026, 7, 8), new BigDecimal("6"), 0L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId))
            .thenReturn(List.of(item1, item2));
        when(modalidadeRepo.findAllById(any())).thenReturn(List.of(diarista));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        assertThat(resp.progressoSemanal()).hasSize(1);
        var semana = resp.progressoSemanal().get(0);
        assertThat(semana.semanaInicio()).isEqualTo(LocalDate.of(2026, 7, 6));   // segunda
        assertThat(semana.semanaFim()).isEqualTo(LocalDate.of(2026, 7, 12));     // domingo
        assertThat(semana.horasLancadas()).isEqualByComparingTo("12");
        assertThat(semana.metaHoras()).isEqualByComparingTo("20");
        assertThat(semana.cumprida()).isFalse();
    }

    @Test
    void buscarPorId_diarista_progressoSemanalCumprida() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        setId(f, freqId);

        FrequenciaItem item1 = itemFixture(freqId, diaristaId, LocalDate.of(2026, 7, 6), new BigDecimal("10"), 0L);
        FrequenciaItem item2 = itemFixture(freqId, diaristaId, LocalDate.of(2026, 7, 8), new BigDecimal("10"), 0L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId))
            .thenReturn(List.of(item1, item2));
        when(modalidadeRepo.findAllById(any())).thenReturn(List.of(diarista));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        var semana = resp.progressoSemanal().get(0);
        assertThat(semana.horasLancadas()).isEqualByComparingTo("20");
        assertThat(semana.cumprida()).isTrue();
    }

    @Test
    void buscarPorId_diarista_progressoSemanalAgrupaPorSemanasDiferentesEmOrdemCronologica() {
        UUID diaristaId = UUID.randomUUID();
        TomadorModalidade diarista = modalidadeDiaristaFixture(diaristaId, 1_500_000L, "20");
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setTipoMedico("DIARISTA");
        setId(f, freqId);

        // semana de 06/07 (seg) a 12/07 (dom) e semana de 13/07 (seg) a 19/07 (dom)
        FrequenciaItem item1 = itemFixture(freqId, diaristaId, LocalDate.of(2026, 7, 13), new BigDecimal("5"), 0L);
        FrequenciaItem item2 = itemFixture(freqId, diaristaId, LocalDate.of(2026, 7, 6), new BigDecimal("5"), 0L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId))
            .thenReturn(List.of(item1, item2));
        when(modalidadeRepo.findAllById(any())).thenReturn(List.of(diarista));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        assertThat(resp.progressoSemanal()).hasSize(2);
        assertThat(resp.progressoSemanal().get(0).semanaInicio()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(resp.progressoSemanal().get(1).semanaInicio()).isEqualTo(LocalDate.of(2026, 7, 13));
    }

    @Test
    void buscarPorId_plantonista_naoApareceNoProgressoSemanal() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        setId(f, freqId);
        FrequenciaItem item = itemFixture(freqId, modalidadeId, LocalDate.of(2026, 7, 6), null, 150000L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId))
            .thenReturn(List.of(item));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        assertThat(resp.progressoSemanal()).isEmpty();
    }

    // ─── PINSAUDE-13.26 (ajuste pós-implantação): ocorrência fixa aplicada uma única vez ───────

    @Test
    void buscarPorId_ocorrenciaFixaComItens_aplicaValorUmaUnicaVezSobreModalidade_naoPorItem() {
        UUID freqId = UUID.randomUUID();
        UUID ocorrenciaFixaId = UUID.randomUUID();
        // FIXO + percentual combinados: 10% de 150000 = 15000, + 5000 fixo = 20000 (uma vez)
        TomadorOcorrencia ocorrenciaFixa = ocorrenciaFixture(ocorrenciaFixaId, "PERCENTUAL", new BigDecimal("10"), 5000L);

        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setModalidadeId(modalidadeId);
        f.setOcorrenciaId(ocorrenciaFixaId);
        setId(f, freqId);

        // 3 plantões lançados (sem ocorrência por item — resolvida null desde o adicionarItem)
        FrequenciaItem item1 = itemFixture(freqId, modalidadeId, LocalDate.of(2026, 7, 6), null, 150000L);
        FrequenciaItem item2 = itemFixture(freqId, modalidadeId, LocalDate.of(2026, 7, 8), null, 150000L);
        FrequenciaItem item3 = itemFixture(freqId, modalidadeId, LocalDate.of(2026, 7, 13), null, 150000L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId))
            .thenReturn(List.of(item1, item2, item3));
        when(ocorrenciaRepo.findAllById(any())).thenReturn(List.of(ocorrenciaFixa));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        // 3 × R$1.500 (itemFixture não seta deslocamento) + R$200 da ocorrência UMA ÚNICA VEZ
        assertThat(resp.totalValorCentavos()).isEqualTo(3 * 150000L + 20000L);
        assertThat(resp.ocorrenciaValorCentavos()).isEqualTo(20000L);
        // itens não carregam mais valor/ocorrência individual quando a frequência tem modalidade fixa
        assertThat(resp.itens()).allSatisfy(i -> {
            assertThat(i.ocorrenciaId()).isNull();
            assertThat(i.ocorrenciaValorCentavos()).isNull();
        });
    }

    @Test
    void buscarPorId_ocorrenciaFixaSemItensLancados_jaAplicaNoTotal() {
        // Ajuste pós-implantação: o valor da ocorrência fixa (assim como o valor mensal do
        // Diarista) já entra no total apurado assim que a frequência é criada com
        // modalidade+ocorrência fixas — não é mais preciso lançar nenhum plantão.
        UUID freqId = UUID.randomUUID();
        UUID ocorrenciaFixaId = UUID.randomUUID();
        TomadorOcorrencia ocorrenciaFixa = ocorrenciaFixture(ocorrenciaFixaId, "FIXO", null, 5000L);

        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setModalidadeId(modalidadeId);
        f.setOcorrenciaId(ocorrenciaFixaId);
        setId(f, freqId);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId)).thenReturn(List.of());
        when(ocorrenciaRepo.findAllById(any())).thenReturn(List.of(ocorrenciaFixa));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        assertThat(resp.totalValorCentavos()).isEqualTo(5000L);
        assertThat(resp.ocorrenciaValorCentavos()).isEqualTo(5000L);
    }

    // ─── Valoração da Ocorrência do catálogo (PINSAUDE-13.19.5) ───────────────

    @Test
    void adicionarItem_ocorrenciaPercentual_calculaSobreValorCadastradoDaModalidade() {
        UUID ocorrenciaId = UUID.randomUUID();
        TomadorOcorrencia ocorrencia = ocorrenciaFixture(ocorrenciaId, "PERCENTUAL", new BigDecimal("10"), null);
        when(ocorrenciaRepo.findById(ocorrenciaId)).thenReturn(Optional.of(ocorrencia));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        // modalidade (fixture do setUp): valorCentavos=150000, deslocamento=10000 -> 10% de 150000 = 15000
        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, null, ocorrenciaId);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.ocorrenciaValorCentavos()).isEqualTo(15000L);
        assertThat(resp.ocorrenciaId()).isEqualTo(ocorrenciaId);
        assertThat(resp.totalItemCentavos()).isEqualTo(150000L + 10000L + 15000L);
    }

    @Test
    void adicionarItem_ocorrenciaFixa_somaValorFixo() {
        UUID ocorrenciaId = UUID.randomUUID();
        TomadorOcorrencia ocorrencia = ocorrenciaFixture(ocorrenciaId, "FIXO", null, 5000L);
        when(ocorrenciaRepo.findById(ocorrenciaId)).thenReturn(Optional.of(ocorrencia));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, null, ocorrenciaId);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.ocorrenciaValorCentavos()).isEqualTo(5000L);
        assertThat(resp.totalItemCentavos()).isEqualTo(150000L + 10000L + 5000L);
    }

    @Test
    void adicionarItem_ocorrenciaPercentualComFixoExtra_somaAmbos() {
        UUID ocorrenciaId = UUID.randomUUID();
        TomadorOcorrencia ocorrencia = ocorrenciaFixture(ocorrenciaId, "PERCENTUAL", new BigDecimal("10"), 2000L);
        when(ocorrenciaRepo.findById(ocorrenciaId)).thenReturn(Optional.of(ocorrencia));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, null, ocorrenciaId);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        // 10% de 150000 = 15000, + 2000 fixo extra = 17000
        assertThat(resp.ocorrenciaValorCentavos()).isEqualTo(17000L);
    }

    @Test
    void adicionarItem_ocorrenciaSemValor_valorZeroSemQuebrarTotal() {
        UUID ocorrenciaId = UUID.randomUUID();
        TomadorOcorrencia ocorrencia = ocorrenciaFixture(ocorrenciaId, "SEM_VALOR", null, null);
        when(ocorrenciaRepo.findById(ocorrenciaId)).thenReturn(Optional.of(ocorrencia));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, null, ocorrenciaId);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.ocorrenciaValorCentavos()).isEqualTo(0L);
        assertThat(resp.totalItemCentavos()).isEqualTo(160000L);
    }

    @Test
    void adicionarItem_ocorrenciaInexistente_lanca404() {
        UUID ocorrenciaId = UUID.randomUUID();
        when(ocorrenciaRepo.findById(ocorrenciaId)).thenReturn(Optional.empty());

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, null, ocorrenciaId);

        assertThatThrownBy(() -> service.adicionarItem(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void adicionarItem_textoLivreSemCatalogo_naoAfetaValor_semRegressao() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), "Chegou atrasado", null, null, null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.ocorrencia()).isEqualTo("Chegou atrasado");
        assertThat(resp.ocorrenciaId()).isNull();
        assertThat(resp.ocorrenciaValorCentavos()).isNull();
        assertThat(resp.totalItemCentavos()).isEqualTo(160000L);
    }

    @Test
    void atualizarItem_trocaParaOcorrenciaComValor_recalculaTotal() {
        UUID ocorrenciaId = UUID.randomUUID();
        TomadorOcorrencia ocorrencia = ocorrenciaFixture(ocorrenciaId, "FIXO", null, 3000L);
        when(ocorrenciaRepo.findById(ocorrenciaId)).thenReturn(Optional.of(ocorrencia));

        UUID freqId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        FrequenciaItem item = new FrequenciaItem();
        setId(item, itemId);
        item.setFrequenciaId(freqId);
        item.setModalidadeId(modalidadeId);
        item.setValorUnitarioCentavos(150000L);
        item.setDeslocamentoCentavos(10000L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 6), null, null, null, ocorrenciaId);

        FrequenciaItemResponse resp = service.atualizarItem(freqId, itemId, req);

        assertThat(resp.ocorrenciaValorCentavos()).isEqualTo(3000L);
        assertThat(resp.totalItemCentavos()).isEqualTo(150000L + 10000L + 3000L);
    }

    // PINSAUDE-13.22: a modalidade META e o acompanhamento por "bloco" foram removidos —
    // o acompanhamento semanal do tipo Diarista é implementado em PINSAUDE-13.23, junto com
    // os testes correspondentes de progressoSemanal.

    // ─── Remover item ─────────────────────────────────────────────────────────

    @Test
    void removerItem_valido_deletaDoRepositorio() {
        UUID freqId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        FrequenciaItem item = new FrequenciaItem();
        item.setFrequenciaId(freqId);
        setId(item, itemId);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));

        service.removerItem(freqId, itemId);

        verify(itemRepo).delete(item);
    }

    @Test
    void removerItem_itemDeOutraFrequencia_lanca404() {
        UUID freqId   = UUID.randomUUID();
        UUID outraId  = UUID.randomUUID();
        UUID itemId   = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        FrequenciaItem item = new FrequenciaItem();
        item.setFrequenciaId(outraId); // item pertence a outra frequência
        setId(item, itemId);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.removerItem(freqId, itemId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ─── Gerar PDF ────────────────────────────────────────────────────────────

    @Test
    void gerarPdf_rascunho_mudaStatusParaAguardandoAssinatura() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(frequenciaRepo.save(any())).thenReturn(f);
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(any())).thenReturn(List.of());

        FrequenciaMedicaResponse resp = service.gerarPdf(freqId);

        assertThat(resp.status()).isEqualTo("AGUARDANDO_ASSINATURA");
        verify(frequenciaRepo).save(any());
    }

    @Test
    void gerarPdf_aguardandoAssinatura_idempotente() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("AGUARDANDO_ASSINATURA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(any())).thenReturn(List.of());

        FrequenciaMedicaResponse resp = service.gerarPdf(freqId);

        assertThat(resp.status()).isEqualTo("AGUARDANDO_ASSINATURA");
        verify(frequenciaRepo, never()).save(any());
    }

    @Test
    void gerarPdf_faturada_lanca422() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("FATURADA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.gerarPdf(freqId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ─── Receber Documento Assinado ───────────────────────────────────────────

    @Test
    void receberDocumentoAssinado_aguardandoAssinatura_mudaStatusParaAssinadaRecebida() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("AGUARDANDO_ASSINATURA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(storageService.upload(anyString(), any())).thenReturn("frequencias/" + freqId + "/doc.pdf");
        when(frequenciaRepo.save(any())).thenReturn(f);
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(any())).thenReturn(List.of());

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "doc.pdf", "application/pdf", new byte[10]);
        FrequenciaMedicaResponse resp = service.receberDocumentoAssinado(freqId, arquivo);

        assertThat(resp.status()).isEqualTo("ASSINADA_RECEBIDA");
        assertThat(resp.documentoAssinado()).isTrue();
        verify(storageService).upload(anyString(), any());
        verify(storageService, never()).delete(any());
    }

    @Test
    void receberDocumentoAssinado_reupload_deletaArquigoAnterior() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("AGUARDANDO_ASSINATURA");
        f.setDocumentoAssinadoKey("frequencias/" + freqId + "/antigo.pdf");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(storageService.upload(anyString(), any())).thenReturn("frequencias/" + freqId + "/novo.pdf");
        when(frequenciaRepo.save(any())).thenReturn(f);
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(any())).thenReturn(List.of());

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "novo.pdf", "application/pdf", new byte[10]);
        service.receberDocumentoAssinado(freqId, arquivo);

        verify(storageService).delete("frequencias/" + freqId + "/antigo.pdf");
        verify(storageService).upload(anyString(), any());
    }

    @Test
    void receberDocumentoAssinado_substituicaoEmAssinadaRecebida_mantemStatus() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("ASSINADA_RECEBIDA");
        f.setDocumentoAssinadoKey("frequencias/" + freqId + "/antigo.pdf");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(storageService.upload(anyString(), any())).thenReturn("frequencias/" + freqId + "/novo.pdf");
        when(frequenciaRepo.save(any())).thenReturn(f);
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(any())).thenReturn(List.of());

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "novo.pdf", "application/pdf", new byte[10]);
        FrequenciaMedicaResponse resp = service.receberDocumentoAssinado(freqId, arquivo);

        // status não regride — permanece ASSINADA_RECEBIDA
        assertThat(resp.status()).isEqualTo("ASSINADA_RECEBIDA");
        verify(storageService).delete("frequencias/" + freqId + "/antigo.pdf");
        verify(storageService).upload(anyString(), any());
    }

    @Test
    void receberDocumentoAssinado_statusFaturada_lanca422() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("FATURADA");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "doc.pdf", "application/pdf", new byte[10]);
        assertThatThrownBy(() -> service.receberDocumentoAssinado(freqId, arquivo))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void receberDocumentoAssinado_statusRascunho_lanca422() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setStatus("RASCUNHO");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "doc.pdf", "application/pdf", new byte[10]);
        assertThatThrownBy(() -> service.receberDocumentoAssinado(freqId, arquivo))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void getDocumentoUrl_semDocumento_lanca404() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        // documentoAssinadoKey é null por padrão
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.getDocumentoUrl(freqId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDocumentoUrl_comDocumento_retornaUrl() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        f.setDocumentoAssinadoKey("frequencias/" + freqId + "/doc.pdf");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(storageService.getPresignedUrl(anyString())).thenReturn("http://minio/signed-url");

        String url = service.getDocumentoUrl(freqId);

        assertThat(url).isEqualTo("http://minio/signed-url");
        verify(storageService).getPresignedUrl("frequencias/" + freqId + "/doc.pdf");
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private FrequenciaMedica frequenciaFixture(UUID medicoId, UUID setorId, String competencia) {
        FrequenciaMedica f = new FrequenciaMedica();
        setId(f, UUID.randomUUID());
        f.setTomadorId(tomadorId);
        f.setMedicoId(medicoId);
        f.setServicoOperacionalId(setorId);
        f.setCompetencia(competencia);
        f.setEspecialidade("MEDICO PLANTONISTA");
        f.setStatus("RASCUNHO");
        f.setCnpjIdTenant("12345678000199");
        return f;
    }

    private FrequenciaItem itemFixture(UUID freqId, UUID modalidadeId, LocalDate dataExecucao,
                                       BigDecimal horasTrabalhadas, long valorUnitarioCentavos) {
        FrequenciaItem item = new FrequenciaItem();
        setId(item, UUID.randomUUID());
        item.setFrequenciaId(freqId);
        item.setModalidadeId(modalidadeId);
        item.setDataExecucao(dataExecucao);
        item.setHorasTrabalhadas(horasTrabalhadas);
        item.setValorUnitarioCentavos(valorUnitarioCentavos);
        item.setDeslocamentoCentavos(0L);
        return item;
    }

    private TomadorModalidade modalidadeDiaristaFixture(UUID id, long valorCentavos, String horasSemanais) {
        TomadorModalidade m = new TomadorModalidade();
        setId(m, id);
        m.setTomadorId(tomadorId);
        m.setNome("Diarista 20h/semana");
        m.setTipo("DIARISTA");
        m.setHorasSemanais(new BigDecimal(horasSemanais));
        m.setValorCentavos(valorCentavos);
        m.setDeslocamentoCentavos(0L);
        m.setAtivo(true);
        return m;
    }

    private TomadorOcorrencia ocorrenciaFixture(UUID id, String tipoValor, BigDecimal valorPercentual, Long valorCentavos) {
        TomadorOcorrencia o = new TomadorOcorrencia();
        setId(o, id);
        o.setTomadorId(tomadorId);
        o.setNome("Feriado");
        o.setTipoValor(tipoValor);
        o.setValorPercentual(valorPercentual);
        o.setValorCentavos(valorCentavos);
        o.setAtivo(true);
        return o;
    }

    private <T> void setId(T obj, UUID id) {
        try {
            var f = obj.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception ignored) {}
    }
}
