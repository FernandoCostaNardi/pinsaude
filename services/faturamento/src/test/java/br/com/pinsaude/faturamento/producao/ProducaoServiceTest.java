package br.com.pinsaude.faturamento.producao;

import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.dto.ParticipacaoRequest;
import br.com.pinsaude.faturamento.dto.ProducaoRequest;
import br.com.pinsaude.faturamento.dto.ProducaoResponse;
import br.com.pinsaude.faturamento.repository.MedicoTomadorRepository;
import br.com.pinsaude.faturamento.repository.ParticipacaoRepository;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import br.com.pinsaude.faturamento.repository.TomadorGrupoFaturamentoRepository;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import br.com.pinsaude.faturamento.service.ProducaoService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProducaoServiceTest {

    @Mock ProducaoRepository producaoRepo;
    @Mock ParticipacaoRepository participacaoRepo;
    @Mock TomadorRepository tomadorRepo;
    @Mock ServicoRepository servicoRepo;
    @Mock MedicoTomadorRepository medicoTomadorRepo;
    @Mock TomadorGrupoFaturamentoRepository grupoRepo;

    @InjectMocks ProducaoService service;

    private UUID tomadorId;
    private UUID servicoId;
    private UUID medicoId;
    private Tomador tomador;
    private Servico servico;

    @BeforeEach
    void setUp() {
        tomadorId = UUID.randomUUID();
        servicoId = UUID.randomUUID();
        medicoId  = UUID.randomUUID();

        tomador = new Tomador();
        tomador.setId(tomadorId);
        tomador.setIndicadorRetencaoFederal(false);
        tomador.setIndicadorRetencaoIss(false);

        servico = new Servico();
        servico.setId(servicoId);

        when(producaoRepo.save(any())).thenAnswer(inv -> {
            Producao p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
    }

    // ─── criar — validação de médico alocado ao tomador (EPIC-15.7) ───────────

    @Test
    void criar_medicoNaoAlocado_lanca422() {
        when(tomadorRepo.findById(tomadorId)).thenReturn(Optional.of(tomador));
        when(servicoRepo.findById(servicoId)).thenReturn(Optional.of(servico));
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(false);

        ProducaoRequest req = new ProducaoRequest(tomadorId, servicoId, "2026-07", null, null, null,
            List.of(new ParticipacaoRequest(medicoId, 100000L, null)));

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não está alocado a este tomador");
    }

    @Test
    void criar_medicoAlocado_salvaComSucesso() {
        when(tomadorRepo.findById(tomadorId)).thenReturn(Optional.of(tomador));
        when(servicoRepo.findById(servicoId)).thenReturn(Optional.of(servico));
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(true);

        ProducaoRequest req = new ProducaoRequest(tomadorId, servicoId, "2026-07", null, null, null,
            List.of(new ParticipacaoRequest(medicoId, 100000L, null)));

        ProducaoResponse result = service.criar(req);

        assertThat(result).isNotNull();
        assertThat(result.valorBruto()).isEqualTo(100000L);
    }

    @Test
    void criar_multiplosParticipantes_umNaoAlocado_lanca422() {
        UUID medicoAlocado = UUID.randomUUID();
        UUID medicoNaoAlocado = UUID.randomUUID();
        when(tomadorRepo.findById(tomadorId)).thenReturn(Optional.of(tomador));
        when(servicoRepo.findById(servicoId)).thenReturn(Optional.of(servico));
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoAlocado)).thenReturn(true);
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoNaoAlocado)).thenReturn(false);

        ProducaoRequest req = new ProducaoRequest(tomadorId, servicoId, "2026-07", null, null, null,
            List.of(
                new ParticipacaoRequest(medicoAlocado, 60000L, null),
                new ParticipacaoRequest(medicoNaoAlocado, 40000L, null)
            ));

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não está alocado a este tomador");
    }

    @Test
    void criar_multiplosParticipantesTodosAlocados_salvaComSucesso() {
        UUID medicoA = UUID.randomUUID();
        UUID medicoB = UUID.randomUUID();
        when(tomadorRepo.findById(tomadorId)).thenReturn(Optional.of(tomador));
        when(servicoRepo.findById(servicoId)).thenReturn(Optional.of(servico));
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoA)).thenReturn(true);
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoB)).thenReturn(true);

        ProducaoRequest req = new ProducaoRequest(tomadorId, servicoId, "2026-07", null, null, null,
            List.of(
                new ParticipacaoRequest(medicoA, 60000L, null),
                new ParticipacaoRequest(medicoB, 40000L, null)
            ));

        ProducaoResponse result = service.criar(req);

        assertThat(result.valorBruto()).isEqualTo(100000L);
    }

    // ─── criar — validação de tomador com faturamento por grupo (PINSAUDE-13.11) ─

    @Test
    void criar_tomadorComGrupoFaturamento_lanca422() {
        when(tomadorRepo.findById(tomadorId)).thenReturn(Optional.of(tomador));
        when(servicoRepo.findById(servicoId)).thenReturn(Optional.of(servico));
        when(grupoRepo.existsByTomadorIdAndAtivoTrue(tomadorId)).thenReturn(true);

        ProducaoRequest req = new ProducaoRequest(tomadorId, servicoId, "2026-07", null, null, null,
            List.of(new ParticipacaoRequest(medicoId, 100000L, null)));

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("faturamento por grupo configurado");
    }

    @Test
    void criar_tomadorSemGrupoFaturamento_salvaComSucesso() {
        when(tomadorRepo.findById(tomadorId)).thenReturn(Optional.of(tomador));
        when(servicoRepo.findById(servicoId)).thenReturn(Optional.of(servico));
        when(grupoRepo.existsByTomadorIdAndAtivoTrue(tomadorId)).thenReturn(false);
        when(medicoTomadorRepo.existsByTomadorIdAndMedicoId(tomadorId, medicoId)).thenReturn(true);

        ProducaoRequest req = new ProducaoRequest(tomadorId, servicoId, "2026-07", null, null, null,
            List.of(new ParticipacaoRequest(medicoId, 100000L, null)));

        ProducaoResponse result = service.criar(req);

        assertThat(result.valorBruto()).isEqualTo(100000L);
    }

    @Test
    void criar_tomadorInexistente_lanca404() {
        when(tomadorRepo.findById(tomadorId)).thenReturn(Optional.empty());

        ProducaoRequest req = new ProducaoRequest(tomadorId, servicoId, "2026-07", null, null, null,
            List.of(new ParticipacaoRequest(medicoId, 100000L, null)));

        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void criar_valorTotalZero_lanca400() {
        ProducaoRequest req = new ProducaoRequest(tomadorId, servicoId, "2026-07", null, null, null,
            List.of(new ParticipacaoRequest(medicoId, 0L, null)));

        // valorBruto=0 falha a validação @Min(1) do DTO antes de chegar aqui em produção real,
        // mas o service também guarda essa invariante para chamadas diretas (defesa em profundidade).
        assertThatThrownBy(() -> service.criar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Valor total deve ser maior que zero");
    }
}
