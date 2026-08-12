package br.com.pinsaude.faturamento.frequencia;

import br.com.pinsaude.faturamento.domain.FrequenciaItem;
import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import br.com.pinsaude.faturamento.dto.FrequenciaItemRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaItemResponse;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaRequest;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaResponse;
import br.com.pinsaude.faturamento.repository.FrequenciaItemRepository;
import br.com.pinsaude.faturamento.repository.FrequenciaMedicaRepository;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
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
            modalidadeId, LocalDate.of(2026, 7, 5), "Normal", null, null);

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
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, null);

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
            modalInexistente, LocalDate.of(2026, 7, 5), null, null, null);

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
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, null);

        FrequenciaItemResponse resp = service.adicionarItem(freqId, req);

        assertThat(resp.valorUnitarioCentavos()).isEqualTo(150000L);
        assertThat(resp.horasTrabalhadas()).isNull();
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
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, ocorrenciaId);

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
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, ocorrenciaId);

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
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, ocorrenciaId);

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
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, ocorrenciaId);

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
            modalidadeId, LocalDate.of(2026, 7, 5), null, null, ocorrenciaId);

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
            modalidadeId, LocalDate.of(2026, 7, 5), "Chegou atrasado", null, null);

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
            modalidadeId, LocalDate.of(2026, 7, 6), null, null, ocorrenciaId);

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
