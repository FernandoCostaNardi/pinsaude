package br.com.pinsaude.faturamento.fechamento;

import br.com.pinsaude.faturamento.domain.*;
import br.com.pinsaude.faturamento.dto.FechamentoPreviewResponse;
import br.com.pinsaude.faturamento.dto.FechamentoRequest;
import br.com.pinsaude.faturamento.dto.FechamentoResponse;
import br.com.pinsaude.faturamento.repository.*;
import br.com.pinsaude.faturamento.service.FechamentoService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FechamentoServiceTest {

    @Mock FechamentoRepository fechamentoRepo;
    @Mock FrequenciaMedicaRepository frequenciaRepo;
    @Mock FrequenciaItemRepository itemRepo;
    @Mock TomadorServicoOperacionalRepository setorRepo;
    @Mock TomadorGrupoFaturamentoRepository grupoRepo;
    @Mock TomadorModalidadeRepository modalidadeRepo;
    @Mock TomadorRepository tomadorRepo;
    @Mock ServicoRepository servicoRepo;
    @Mock ProducaoRepository producaoRepo;
    @Mock ParticipacaoRepository participacaoRepo;

    @InjectMocks FechamentoService service;

    private UUID tomadorId;
    private UUID medico1Id;
    private UUID setorId;
    private UUID grupoId;
    private UUID servicoLc116Id;

    private TomadorServicoOperacional setor;
    private TomadorGrupoFaturamento grupo;
    private Servico servico;
    private Tomador tomador;

    private static final String COMPETENCIA = "2026-07";

    @BeforeEach
    void setUp() {
        tomadorId      = UUID.randomUUID();
        medico1Id      = UUID.randomUUID();
        setorId        = UUID.randomUUID();
        grupoId        = UUID.randomUUID();
        servicoLc116Id = UUID.randomUUID();

        setor = new TomadorServicoOperacional();
        setId(setor, setorId);
        setor.setTomadorId(tomadorId);
        setor.setGrupoId(grupoId);
        setor.setNome("Emergência Cardiológica");
        setor.setAtivo(true);

        grupo = new TomadorGrupoFaturamento();
        setId(grupo, grupoId);
        grupo.setTomadorId(tomadorId);
        grupo.setServicoLc116Id(servicoLc116Id);
        grupo.setNome("Plantões e Diárias");
        grupo.setDescricaoNota("Serviços médicos referente a {competencia}.");
        grupo.setOrdem(1);
        grupo.setAtivo(true);

        servico = new Servico();
        setId(servico, servicoLc116Id);
        servico.setCodigoLc116("14.01");
        servico.setDescricaoPadrao("Medicina e biomedicina");
        servico.setAliquotaIss(new BigDecimal("5.0000"));
        servico.setAliquotaIr(new BigDecimal("1.5000"));
        servico.setAliquotaCsll(new BigDecimal("1.0000"));
        servico.setAliquotaPis(new BigDecimal("0.6500"));
        servico.setAliquotaCofins(new BigDecimal("3.0000"));

        tomador = new Tomador();
        setId(tomador, tomadorId);
        tomador.setNomeFantasia("Hospital do Coração");

        when(tomadorRepo.findById(tomadorId)).thenReturn(Optional.of(tomador));
        when(servicoRepo.findById(servicoLc116Id)).thenReturn(Optional.of(servico));
        when(setorRepo.findAllById(any())).thenReturn(List.of(setor));
        when(grupoRepo.findAllById(any())).thenReturn(List.of(grupo));
        when(fechamentoRepo.findByTomadorIdAndCompetencia(any(), any())).thenReturn(Optional.empty());
        when(fechamentoRepo.save(any())).thenAnswer(inv -> {
            Fechamento f = inv.getArgument(0);
            if (f.getId() == null) setId(f, UUID.randomUUID());
            return f;
        });
        when(producaoRepo.save(any())).thenAnswer(inv -> {
            Producao p = inv.getArgument(0);
            if (p.getId() == null) setId(p, UUID.randomUUID());
            return p;
        });
        when(participacaoRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(frequenciaRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── interpolarDescricao ─────────────────────────────────────────────────

    @Test
    void interpolarDescricao_substituiMesEAno() {
        String resultado = FechamentoService.interpolarDescricao(
            "Serviços referente a {competencia}.", "2026-07");
        assertThat(resultado).isEqualTo("Serviços referente a JULHO DE 2026.");
    }

    @Test
    void interpolarDescricao_semMarcador_retornaTemplateSemAlteracao() {
        String template = "Serviços médicos hospitalares.";
        String resultado = FechamentoService.interpolarDescricao(template, "2026-01");
        assertThat(resultado).isEqualTo(template);
    }

    @Test
    void interpolarDescricao_janeiro_corretamente() {
        assertThat(FechamentoService.interpolarDescricao("{competencia}", "2026-01"))
            .isEqualTo("JANEIRO DE 2026");
    }

    @Test
    void interpolarDescricao_dezembro_corretamente() {
        assertThat(FechamentoService.interpolarDescricao("{competencia}", "2025-12"))
            .isEqualTo("DEZEMBRO DE 2025");
    }

    // ─── Preview ──────────────────────────────────────────────────────────────

    @Test
    void preview_semFrequencias_retornaPreviewVazio() {
        when(frequenciaRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(List.of());

        FechamentoPreviewResponse resp = service.preview(tomadorId, COMPETENCIA);

        assertThat(resp.tomadorId()).isEqualTo(tomadorId);
        assertThat(resp.competencia()).isEqualTo(COMPETENCIA);
        assertThat(resp.grupos()).isEmpty();
        assertThat(resp.totalCentavos()).isZero();
        assertThat(resp.totalFrequencias()).isZero();
    }

    @Test
    void preview_comFrequenciasEItens_agregaPorGrupo() {
        FrequenciaMedica freq = frequenciaFixture(medico1Id, setorId, COMPETENCIA, "RASCUNHO");
        FrequenciaItem item1 = itemFixture(freq.getId(), 150_000L, 10_000L);
        FrequenciaItem item2 = itemFixture(freq.getId(), 150_000L, 0L);

        when(frequenciaRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(List.of(freq));
        when(itemRepo.findByFrequenciaIdIn(List.of(freq.getId())))
            .thenReturn(List.of(item1, item2));

        FechamentoPreviewResponse resp = service.preview(tomadorId, COMPETENCIA);

        assertThat(resp.totalFrequencias()).isEqualTo(1);
        assertThat(resp.grupos()).hasSize(1);

        FechamentoPreviewResponse.GrupoPreview grupoPreview = resp.grupos().get(0);
        assertThat(grupoPreview.grupoId()).isEqualTo(grupoId);
        assertThat(grupoPreview.nome()).isEqualTo("Plantões e Diárias");
        assertThat(grupoPreview.descricaoInterpolada()).isEqualTo("Serviços médicos referente a JULHO DE 2026.");
        assertThat(grupoPreview.totalCentavos()).isEqualTo(310_000L); // 150+10 + 150+0

        assertThat(resp.totalCentavos()).isEqualTo(310_000L);

        assertThat(grupoPreview.medicos()).hasSize(1);
        assertThat(grupoPreview.medicos().get(0).medicoId()).isEqualTo(medico1Id);
        assertThat(grupoPreview.medicos().get(0).totalCentavos()).isEqualTo(310_000L);
    }

    @Test
    void preview_comOcorrenciaValorCentavos_somaNoTotal() {
        FrequenciaMedica freq = frequenciaFixture(medico1Id, setorId, COMPETENCIA, "RASCUNHO");
        FrequenciaItem item = itemFixture(freq.getId(), 150_000L, 10_000L);
        item.setOcorrenciaValorCentavos(15_000L); // ex: 10% de uma ocorrência sobre a modalidade

        when(frequenciaRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(List.of(freq));
        when(itemRepo.findByFrequenciaIdIn(List.of(freq.getId())))
            .thenReturn(List.of(item));

        FechamentoPreviewResponse resp = service.preview(tomadorId, COMPETENCIA);

        // 150.000 (valor) + 10.000 (deslocamento) + 15.000 (ocorrência) = 175.000
        assertThat(resp.totalCentavos()).isEqualTo(175_000L);
        assertThat(resp.grupos().get(0).totalCentavos()).isEqualTo(175_000L);
    }

    @Test
    void preview_frequenciaFaturadaExcluida() {
        FrequenciaMedica faturada = frequenciaFixture(medico1Id, setorId, COMPETENCIA, "FATURADA");

        when(frequenciaRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(List.of(faturada));

        FechamentoPreviewResponse resp = service.preview(tomadorId, COMPETENCIA);

        assertThat(resp.grupos()).isEmpty();
        assertThat(resp.totalFrequencias()).isZero(); // faturada foi excluída
    }

    @Test
    void preview_doisMedicosNoMesmoGrupo_somaIndividual() {
        UUID medico2Id = UUID.randomUUID();
        FrequenciaMedica freq1 = frequenciaFixture(medico1Id, setorId, COMPETENCIA, "ASSINADA_RECEBIDA");
        FrequenciaMedica freq2 = frequenciaFixture(medico2Id, setorId, COMPETENCIA, "ENVIADA_TOMADOR");
        FrequenciaItem item1 = itemFixture(freq1.getId(), 100_000L, 0L);
        FrequenciaItem item2 = itemFixture(freq2.getId(), 200_000L, 5_000L);

        when(frequenciaRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(List.of(freq1, freq2));
        when(itemRepo.findByFrequenciaIdIn(any()))
            .thenReturn(List.of(item1, item2));

        FechamentoPreviewResponse resp = service.preview(tomadorId, COMPETENCIA);

        assertThat(resp.totalCentavos()).isEqualTo(305_000L); // 100000 + 200000 + 5000
        FechamentoPreviewResponse.GrupoPreview g = resp.grupos().get(0);
        assertThat(g.medicos()).hasSize(2);

        long totalMedico1 = g.medicos().stream()
            .filter(m -> m.medicoId().equals(medico1Id))
            .mapToLong(FechamentoPreviewResponse.MedicoParticipacao::totalCentavos)
            .sum();
        assertThat(totalMedico1).isEqualTo(100_000L);

        long totalMedico2 = g.medicos().stream()
            .filter(m -> m.medicoId().equals(medico2Id))
            .mapToLong(FechamentoPreviewResponse.MedicoParticipacao::totalCentavos)
            .sum();
        assertThat(totalMedico2).isEqualTo(205_000L);
    }

    // ─── Executar ─────────────────────────────────────────────────────────────

    @Test
    void executar_competenciaJaFechada_lanca409() {
        Fechamento fechadoExistente = new Fechamento();
        setId(fechadoExistente, UUID.randomUUID());
        fechadoExistente.setStatus("FECHADO");
        fechadoExistente.setTomadorId(tomadorId);
        fechadoExistente.setCompetencia(COMPETENCIA);

        when(fechamentoRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(Optional.of(fechadoExistente));

        FechamentoRequest req = new FechamentoRequest(tomadorId, COMPETENCIA);

        assertThatThrownBy(() -> service.executar(req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void executar_semFrequencias_lanca422() {
        when(frequenciaRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(List.of());

        FechamentoRequest req = new FechamentoRequest(tomadorId, COMPETENCIA);

        assertThatThrownBy(() -> service.executar(req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void executar_feliz_criaProducaoMarcaFrequenciaFaturada() {
        FrequenciaMedica freq = frequenciaFixture(medico1Id, setorId, COMPETENCIA, "ASSINADA_RECEBIDA");
        FrequenciaItem item = itemFixture(freq.getId(), 200_000L, 15_000L);

        when(frequenciaRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(List.of(freq));
        when(itemRepo.findByFrequenciaIdIn(any())).thenReturn(List.of(item));

        FechamentoRequest req = new FechamentoRequest(tomadorId, COMPETENCIA);
        FechamentoResponse resp = service.executar(req);

        assertThat(resp.status()).isEqualTo("FECHADO");
        assertThat(resp.totalCentavos()).isEqualTo(215_000L);
        assertThat(resp.producoes()).hasSize(1);
        assertThat(resp.producoes().get(0).grupoNome()).isEqualTo("Plantões e Diárias");
        assertThat(resp.producoes().get(0).totalCentavos()).isEqualTo(215_000L);

        // Frequência deve ter sido marcada como FATURADA
        assertThat(freq.getStatus()).isEqualTo("FATURADA");
        assertThat(freq.getFechamentoId()).isNotNull();
        assertThat(freq.getProducaoId()).isNotNull();

        verify(producaoRepo).save(any());
        verify(participacaoRepo).saveAll(any());
        verify(frequenciaRepo).saveAll(any());
        verify(fechamentoRepo, times(2)).save(any()); // ABERTO e depois FECHADO
    }

    @Test
    void executar_multiplosMedicos_criaParticipacoesSeparadas() {
        UUID medico2Id = UUID.randomUUID();
        FrequenciaMedica freq1 = frequenciaFixture(medico1Id, setorId, COMPETENCIA, "ASSINADA_RECEBIDA");
        FrequenciaMedica freq2 = frequenciaFixture(medico2Id, setorId, COMPETENCIA, "ENVIADA_TOMADOR");
        FrequenciaItem item1 = itemFixture(freq1.getId(), 100_000L, 0L);
        FrequenciaItem item2 = itemFixture(freq2.getId(), 80_000L, 5_000L);

        when(frequenciaRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(List.of(freq1, freq2));
        when(itemRepo.findByFrequenciaIdIn(any())).thenReturn(List.of(item1, item2));

        FechamentoRequest req = new FechamentoRequest(tomadorId, COMPETENCIA);
        FechamentoResponse resp = service.executar(req);

        assertThat(resp.totalCentavos()).isEqualTo(185_000L); // 100000 + 80000 + 5000
        assertThat(resp.producoes()).hasSize(1);
        // 2 médicos → 2 participações devem ser salvas
        verify(participacaoRepo).saveAll(any());
    }

    @Test
    void executar_descricaoInterpolada_passadaNaProducao() {
        FrequenciaMedica freq = frequenciaFixture(medico1Id, setorId, COMPETENCIA, "RASCUNHO");
        FrequenciaItem item = itemFixture(freq.getId(), 100_000L, 0L);

        when(frequenciaRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(List.of(freq));
        when(itemRepo.findByFrequenciaIdIn(any())).thenReturn(List.of(item));

        service.executar(new FechamentoRequest(tomadorId, COMPETENCIA));

        ArgumentCaptor<Producao> captor = ArgumentCaptor.forClass(Producao.class);
        verify(producaoRepo).save(captor.capture());
        Producao salva = captor.getValue();
        assertThat(salva.getDescricaoComplementar())
            .isEqualTo("Serviços médicos referente a JULHO DE 2026.");
        assertThat(salva.getValorBruto()).isEqualTo(100_000L);
        assertThat(salva.getCompetencia()).isEqualTo(COMPETENCIA);
    }

    @Test
    void executar_grupoComValorZero_naoGeraProducao() {
        // Frequência sem itens → valor zero → não deve criar producao
        FrequenciaMedica freq = frequenciaFixture(medico1Id, setorId, COMPETENCIA, "RASCUNHO");

        when(frequenciaRepo.findByTomadorIdAndCompetencia(tomadorId, COMPETENCIA))
            .thenReturn(List.of(freq));
        when(itemRepo.findByFrequenciaIdIn(any())).thenReturn(List.of()); // sem itens

        FechamentoRequest req = new FechamentoRequest(tomadorId, COMPETENCIA);
        FechamentoResponse resp = service.executar(req);

        assertThat(resp.producoes()).isEmpty();
        assertThat(resp.totalCentavos()).isZero();
        verify(producaoRepo, never()).save(any());
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private FrequenciaMedica frequenciaFixture(UUID medicoId, UUID setorId,
                                               String competencia, String status) {
        FrequenciaMedica f = new FrequenciaMedica();
        setId(f, UUID.randomUUID());
        f.setTomadorId(tomadorId);
        f.setMedicoId(medicoId);
        f.setServicoOperacionalId(setorId);
        f.setCompetencia(competencia);
        f.setEspecialidade("MEDICO PLANTONISTA");
        f.setStatus(status);
        f.setCnpjIdTenant("12345678000199");
        return f;
    }

    private FrequenciaItem itemFixture(UUID frequenciaId, long valor, long deslocamento) {
        FrequenciaItem item = new FrequenciaItem();
        setId(item, UUID.randomUUID());
        item.setFrequenciaId(frequenciaId);
        item.setModalidadeId(UUID.randomUUID());
        item.setDataExecucao(LocalDate.of(2026, 7, 10));
        item.setValorUnitarioCentavos(valor);
        item.setDeslocamentoCentavos(deslocamento);
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
