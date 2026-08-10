package br.com.pinsaude.faturamento.frequencia;

import br.com.pinsaude.faturamento.domain.FrequenciaItem;
import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import br.com.pinsaude.faturamento.dto.FrequenciaItemRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaItemResponse;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaResponse;
import br.com.pinsaude.faturamento.repository.FrequenciaItemRepository;
import br.com.pinsaude.faturamento.repository.FrequenciaMedicaRepository;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.TomadorModalidadeRepository;
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

    @InjectMocks FrequenciaService service;

    private UUID tomadorId;
    private UUID medicoId;
    private UUID setorId;
    private UUID modalidadeId;
    private TomadorServicoOperacional setor;
    private TomadorModalidade modalidade;

    @BeforeEach
    void setUp() {
        tomadorId   = UUID.randomUUID();
        medicoId    = UUID.randomUUID();
        setorId     = UUID.randomUUID();
        modalidadeId = UUID.randomUUID();

        setor = new TomadorServicoOperacional();
        setId(setor, setorId);
        setor.setTomadorId(tomadorId);
        setor.setNome("Emergência Cardiológica");
        setor.setAtivo(true);

        modalidade = new TomadorModalidade();
        setId(modalidade, modalidadeId);
        modalidade.setTomadorId(tomadorId);
        modalidade.setNome("Plantão 12h Noturno");
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
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(true);
    }

    // ─── Criar frequência ─────────────────────────────────────────────────────

    @Test
    void criar_frequenciaValida_salvaNoBanco() {
        when(frequenciaRepo.existsByMedicoIdAndServicoOperacionalIdAndCompetencia(
                medicoId, setorId, "2026-07")).thenReturn(false);
        when(frequenciaRepo.save(any())).thenAnswer(inv -> {
            FrequenciaMedica f = inv.getArgument(0);
            setId(f, UUID.randomUUID());
            return f;
        });

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, setorId, "2026-07", "PLANTONISTA");

        FrequenciaMedicaResponse resp = service.criar(req);

        assertThat(resp.competencia()).isEqualTo("2026-07");
        assertThat(resp.tipoMedico()).isEqualTo("PLANTONISTA");
        assertThat(resp.status()).isEqualTo("RASCUNHO");
        assertThat(resp.servicoOperacionalNome()).isEqualTo("Emergência Cardiológica");
        assertThat(resp.totalValorCentavos()).isZero();
        verify(frequenciaRepo).save(any());
    }

    @Test
    void criar_duplicata_lanca409() {
        when(frequenciaRepo.existsByMedicoIdAndServicoOperacionalIdAndCompetencia(
                medicoId, setorId, "2026-07")).thenReturn(true);

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, setorId, "2026-07", "PLANTONISTA");

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void criar_setorInexistente_lanca404() {
        UUID setorInexistente = UUID.randomUUID();
        when(frequenciaRepo.existsByMedicoIdAndServicoOperacionalIdAndCompetencia(any(), any(), any()))
            .thenReturn(false);
        when(setorRepo.findById(setorInexistente)).thenReturn(Optional.empty());

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, setorInexistente, "2026-07", "MEDICO PLANTONISTA");

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void criar_setorDeOutroTomador_lanca422() {
        UUID outroTomadorId = UUID.randomUUID();
        when(frequenciaRepo.existsByMedicoIdAndServicoOperacionalIdAndCompetencia(any(), any(), any()))
            .thenReturn(false);

        // setor pertence a tomadorId, mas a requisição informa outroTomadorId
        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            outroTomadorId, medicoId, setorId, "2026-07", "PLANTONISTA");

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Setor operacional não pertence ao tomador informado")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void criar_medicoNaoAlocadoAoTomador_lanca422() {
        when(frequenciaRepo.existsByMedicoIdAndServicoOperacionalIdAndCompetencia(any(), any(), any()))
            .thenReturn(false);
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(false);

        FrequenciaMedicaRequest req = new FrequenciaMedicaRequest(
            tomadorId, medicoId, setorId, "2026-07", "PLANTONISTA");

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não está alocado a este tomador")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
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
            modalidadeId, LocalDate.of(2026, 7, 5), "Normal", null);

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
            modalidadeId, LocalDate.of(2026, 7, 5), null, null);

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
            modalInexistente, LocalDate.of(2026, 7, 5), null, null);

        assertThatThrownBy(() -> service.adicionarItem(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ─── Valoração proporcional (modalidade META) ────────────────────────────────

    @Test
    void adicionarItem_metaPorHora_calculaValorProporcional() {
        UUID metaId = UUID.randomUUID();
        TomadorModalidade metaHora = modalidadeMetaHora(metaId, 400000L, "40");
        when(modalidadeRepo.findById(metaId)).thenReturn(Optional.of(metaHora));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            metaId, LocalDate.of(2026, 7, 5), null, new BigDecimal("10"));

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        // valor do bloco (R$4.000 / 40h) x 10h lançadas = R$1.000 (100000 centavos)
        assertThat(resp.valorUnitarioCentavos()).isEqualTo(100000L);
        assertThat(resp.horasTrabalhadas()).isEqualByComparingTo("10");
    }

    @Test
    void adicionarItem_metaPorHora_excedeMeta_pagaProporcionalLinear() {
        UUID metaId = UUID.randomUUID();
        TomadorModalidade metaHora = modalidadeMetaHora(metaId, 400000L, "40");
        when(modalidadeRepo.findById(metaId)).thenReturn(Optional.of(metaHora));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        // 45h em um único lançamento — excede a meta de 40h, mas o pagamento continua
        // proporcional/linear (o "bloco" é conceito de acompanhamento, não muda o cálculo)
        FrequenciaItemRequest req = new FrequenciaItemRequest(
            metaId, LocalDate.of(2026, 7, 5), null, new BigDecimal("45"));

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.valorUnitarioCentavos()).isEqualTo(450000L);
    }

    @Test
    void adicionarItem_metaPorHora_semHorasTrabalhadas_lanca422() {
        UUID metaId = UUID.randomUUID();
        TomadorModalidade metaHora = modalidadeMetaHora(metaId, 400000L, "40");
        when(modalidadeRepo.findById(metaId)).thenReturn(Optional.of(metaHora));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            metaId, LocalDate.of(2026, 7, 5), null, null);

        assertThatThrownBy(() -> service.adicionarItem(freqId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("horas trabalhadas")
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void adicionarItem_metaPorDia_calculaValorProporcionalIgnorandoHoras() {
        UUID metaId = UUID.randomUUID();
        TomadorModalidade metaDia = modalidadeMetaDia(metaId, 800000L, 20);
        when(modalidadeRepo.findById(metaId)).thenReturn(Optional.of(metaDia));

        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        // META/DIA não exige horasTrabalhadas — cada item lançado equivale a 1 dia
        FrequenciaItemRequest req = new FrequenciaItemRequest(
            metaId, LocalDate.of(2026, 7, 5), null, null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        // R$8.000 / 20 dias = R$400 por dia (40000 centavos)
        assertThat(resp.valorUnitarioCentavos()).isEqualTo(40000L);
    }

    @Test
    void adicionarItem_plantao_valorPermaneceFlat_semRegressao() {
        // modalidade PLANTAO (fixture padrão do setUp) — comportamento anterior ao EPIC-13.19
        // deve continuar idêntico: valor flat, sem depender de horasTrabalhadas
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.save(any())).thenAnswer(inv -> {
            FrequenciaItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            return item;
        });

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            modalidadeId, LocalDate.of(2026, 7, 5), null, null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.valorUnitarioCentavos()).isEqualTo(150000L);
        assertThat(resp.horasTrabalhadas()).isNull();
    }

    @Test
    void atualizarItem_trocaParaMetaPorHora_recalculaValor() {
        UUID metaId = UUID.randomUUID();
        TomadorModalidade metaHora = modalidadeMetaHora(metaId, 400000L, "40");
        when(modalidadeRepo.findById(metaId)).thenReturn(Optional.of(metaHora));

        UUID freqId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        FrequenciaItem item = new FrequenciaItem();
        setId(item, itemId);
        item.setFrequenciaId(freqId);
        item.setModalidadeId(modalidadeId);
        item.setValorUnitarioCentavos(150000L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FrequenciaItemRequest req = new FrequenciaItemRequest(
            metaId, LocalDate.of(2026, 7, 6), null, new BigDecimal("20"));

        FrequenciaItemResponse resp = service.atualizarItem(freqId, itemId, req);

        // 20h de 40h do bloco de R$4.000 => R$2.000 (200000 centavos)
        assertThat(resp.valorUnitarioCentavos()).isEqualTo(200000L);
    }

    // ─── Progresso da meta (read-only) ────────────────────────────────────────

    @Test
    void buscarPorId_metaPorHora_calculaProgressoAbaixoDaMeta() {
        UUID metaId = UUID.randomUUID();
        TomadorModalidade metaHora = modalidadeMetaHora(metaId, 400000L, "40");
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        setId(f, freqId);

        FrequenciaItem item1 = itemFixture(freqId, metaId, new BigDecimal("15"), 150000L);
        FrequenciaItem item2 = itemFixture(freqId, metaId, new BigDecimal("10"), 100000L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId))
            .thenReturn(List.of(item1, item2));
        when(modalidadeRepo.findAllById(any())).thenReturn(List.of(metaHora));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        assertThat(resp.progressoMetas()).hasSize(1);
        var progresso = resp.progressoMetas().get(0);
        assertThat(progresso.modalidadeId()).isEqualTo(metaId);
        assertThat(progresso.acumuladoHoras()).isEqualByComparingTo("25");
        assertThat(progresso.blocosCompletos()).isZero();
        assertThat(progresso.restanteBlocoAtual()).isEqualByComparingTo("15");
    }

    @Test
    void buscarPorId_metaPorHora_excedeMeta_fechaBlocoEIniciaProximo() {
        UUID metaId = UUID.randomUUID();
        TomadorModalidade metaHora = modalidadeMetaHora(metaId, 400000L, "40");
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        setId(f, freqId);

        FrequenciaItem item1 = itemFixture(freqId, metaId, new BigDecimal("30"), 300000L);
        FrequenciaItem item2 = itemFixture(freqId, metaId, new BigDecimal("15"), 150000L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId))
            .thenReturn(List.of(item1, item2));
        when(modalidadeRepo.findAllById(any())).thenReturn(List.of(metaHora));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        var progresso = resp.progressoMetas().get(0);
        // acumulado 45h, meta 40h: 1 bloco completo, faltam 35h pro segundo bloco
        assertThat(progresso.acumuladoHoras()).isEqualByComparingTo("45");
        assertThat(progresso.blocosCompletos()).isEqualTo(1);
        assertThat(progresso.restanteBlocoAtual()).isEqualByComparingTo("35");
    }

    @Test
    void buscarPorId_metaPorDia_contaItensComoDias() {
        UUID metaId = UUID.randomUUID();
        TomadorModalidade metaDia = modalidadeMetaDia(metaId, 800000L, 20);
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        setId(f, freqId);

        FrequenciaItem item1 = itemFixture(freqId, metaId, null, 40000L);
        FrequenciaItem item2 = itemFixture(freqId, metaId, null, 40000L);
        FrequenciaItem item3 = itemFixture(freqId, metaId, null, 40000L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId))
            .thenReturn(List.of(item1, item2, item3));
        when(modalidadeRepo.findAllById(any())).thenReturn(List.of(metaDia));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        var progresso = resp.progressoMetas().get(0);
        assertThat(progresso.acumuladoDias()).isEqualTo(3);
        assertThat(progresso.blocosCompletos()).isZero();
        assertThat(progresso.restanteBlocoAtual()).isEqualByComparingTo("17");
    }

    @Test
    void buscarPorId_modalidadePlantao_naoApareceNoProgressoMetas() {
        UUID freqId = UUID.randomUUID();
        FrequenciaMedica f = frequenciaFixture(medicoId, setorId, "2026-07");
        setId(f, freqId);
        FrequenciaItem item = itemFixture(freqId, modalidadeId, null, 150000L);

        when(frequenciaRepo.findById(freqId)).thenReturn(Optional.of(f));
        when(itemRepo.findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(freqId))
            .thenReturn(List.of(item));

        FrequenciaMedicaResponse resp = service.buscarPorId(freqId);

        assertThat(resp.progressoMetas()).isEmpty();
    }

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

    private TomadorModalidade modalidadeMetaHora(UUID id, long valorCentavos, String metaHoras) {
        TomadorModalidade m = new TomadorModalidade();
        setId(m, id);
        m.setTomadorId(tomadorId);
        m.setNome("Diaria 40h");
        m.setTipo("META");
        m.setUnidadeCalculo("HORA");
        m.setMetaHoras(new BigDecimal(metaHoras));
        m.setValorCentavos(valorCentavos);
        m.setDeslocamentoCentavos(0L);
        m.setAtivo(true);
        return m;
    }

    private TomadorModalidade modalidadeMetaDia(UUID id, long valorCentavos, int metaDias) {
        TomadorModalidade m = new TomadorModalidade();
        setId(m, id);
        m.setTomadorId(tomadorId);
        m.setNome("Evolucionista");
        m.setTipo("META");
        m.setUnidadeCalculo("DIA");
        m.setMetaDias(metaDias);
        m.setValorCentavos(valorCentavos);
        m.setDeslocamentoCentavos(0L);
        m.setAtivo(true);
        return m;
    }

    private FrequenciaItem itemFixture(UUID freqId, UUID modalidadeId, BigDecimal horasTrabalhadas, long valorUnitarioCentavos) {
        FrequenciaItem item = new FrequenciaItem();
        setId(item, UUID.randomUUID());
        item.setFrequenciaId(freqId);
        item.setModalidadeId(modalidadeId);
        item.setDataExecucao(LocalDate.of(2026, 7, 5));
        item.setHorasTrabalhadas(horasTrabalhadas);
        item.setValorUnitarioCentavos(valorUnitarioCentavos);
        item.setDeslocamentoCentavos(0L);
        return item;
    }

    private <T> void setId(T obj, UUID id) {
        try {
            var f = obj.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception ignored) {}
    }
}
