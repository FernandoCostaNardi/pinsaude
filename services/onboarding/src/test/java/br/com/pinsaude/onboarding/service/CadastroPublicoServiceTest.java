package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.CandidaturaPublicaRequest;
import br.com.pinsaude.onboarding.dto.CandidaturaPublicaResponse;
import br.com.pinsaude.onboarding.repository.DadosCivisMedicoRepository;
import br.com.pinsaude.onboarding.repository.HistoricoMedicoRepository;
import br.com.pinsaude.onboarding.repository.MedicoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CadastroPublicoServiceTest {

    @Mock MedicoRepository medicoRepo;
    @Mock DadosCivisMedicoRepository dadosCivisRepo;
    @Mock HistoricoMedicoRepository historicoRepo;
    @Mock CryptoService cryptoService;

    @InjectMocks CadastroPublicoService service;

    private static final UUID MEDICO_ID = UUID.randomUUID();

    // CPFs válidos gerados com o mesmo algoritmo de CpfValidator (base + dígitos verificadores)
    private static final String CPF_A = cpfValido("111444777");
    private static final String CPF_B = cpfValido("987654321");

    private CandidaturaPublicaRequest requestBase(String cpf, String crm, String canal, String indicador) {
        return new CandidaturaPublicaRequest(
            "Dra. Maria Teste", cpf, crm, "SP", "maria@exemplo.com", "11999998888",
            LocalDate.of(1985, 3, 20), "Brasileira", "Recife/PE", EstadoCivil.SOLTEIRO,
            "Ana Teste", "Jose Teste",
            "Rua das Flores", "123", null, "Boa Viagem", "Recife", "PE", "51020-000",
            "1234567", "SSP", "PE", "9876",
            canal, indicador,
            List.of("GRADUACAO", "RESIDENCIA"), "Cardiologia", "Consultas, ECG"
        );
    }

    private Medico medicoAutoCadastro(UUID id, String cpfHash, StatusMedico status) {
        Medico m = new Medico();
        m.setId(id);
        m.setNome("Dra. Maria Teste");
        m.setCrm("54321");
        m.setCrmUf("SP");
        m.setCpfHash(cpfHash);
        m.setCpfCriptografado(new byte[]{1, 2, 3});
        m.setOrigemCadastro("AUTO_CADASTRO");
        m.setStatus(status);
        return m;
    }

    // ── criar ──────────────────────────────────────────────────────────────

    @Test
    void criar_dadosValidos_criaMedicoEDadosCivisComOrigemAutoCadastro() {
        when(cryptoService.encrypt(any())).thenReturn(new byte[]{9, 9});
        when(medicoRepo.existsByCpfHash(any())).thenReturn(false);
        when(medicoRepo.existsByCrmAndCrmUf(any(), any())).thenReturn(false);
        when(medicoRepo.save(any())).thenAnswer(inv -> {
            Medico m = inv.getArgument(0);
            if (m.getId() == null) m.setId(MEDICO_ID);
            return m;
        });
        when(dadosCivisRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CandidaturaPublicaResponse resp = service.criar(requestBase(CPF_A, "54321", "Instagram", null));

        assertThat(resp.id()).isEqualTo(MEDICO_ID);
        assertThat(resp.status()).isEqualTo("RASCUNHO");
        assertThat(resp.estadoCivil()).isEqualTo(EstadoCivil.SOLTEIRO);
        assertThat(resp.situacaoFormacao()).containsExactly("GRADUACAO", "RESIDENCIA");

        ArgumentCaptor<Medico> medicoCaptor = ArgumentCaptor.forClass(Medico.class);
        verify(medicoRepo).save(medicoCaptor.capture());
        assertThat(medicoCaptor.getValue().getOrigemCadastro()).isEqualTo("AUTO_CADASTRO");
        assertThat(medicoCaptor.getValue().getStatus()).isEqualTo(StatusMedico.RASCUNHO);

        verify(historicoRepo).save(any(HistoricoMedico.class));
    }

    @Test
    void criar_canalIndicacao_persisteNomeIndicador() {
        when(cryptoService.encrypt(any())).thenReturn(new byte[]{9, 9});
        when(medicoRepo.existsByCpfHash(any())).thenReturn(false);
        when(medicoRepo.existsByCrmAndCrmUf(any(), any())).thenReturn(false);
        when(medicoRepo.save(any())).thenAnswer(inv -> {
            Medico m = inv.getArgument(0);
            if (m.getId() == null) m.setId(MEDICO_ID);
            return m;
        });
        ArgumentCaptor<DadosCivisMedico> captor = ArgumentCaptor.forClass(DadosCivisMedico.class);
        when(dadosCivisRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.criar(requestBase(CPF_A, "54321", "Indicação", "Dr. Fulano de Tal"));

        assertThat(captor.getValue().getNomeIndicador()).isEqualTo("Dr. Fulano de Tal");
    }

    @Test
    void criar_canalNaoIndicacao_ignoraNomeIndicador() {
        when(cryptoService.encrypt(any())).thenReturn(new byte[]{9, 9});
        when(medicoRepo.existsByCpfHash(any())).thenReturn(false);
        when(medicoRepo.existsByCrmAndCrmUf(any(), any())).thenReturn(false);
        when(medicoRepo.save(any())).thenAnswer(inv -> {
            Medico m = inv.getArgument(0);
            if (m.getId() == null) m.setId(MEDICO_ID);
            return m;
        });
        ArgumentCaptor<DadosCivisMedico> captor = ArgumentCaptor.forClass(DadosCivisMedico.class);
        when(dadosCivisRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // nomeIndicador informado mas canal != Indicação — deve ser descartado
        service.criar(requestBase(CPF_A, "54321", "Google", "Nao deveria salvar"));

        assertThat(captor.getValue().getNomeIndicador()).isNull();
    }

    @Test
    void criar_cpfDuplicado_lancaConflict() {
        when(medicoRepo.existsByCpfHash(any())).thenReturn(true);

        assertThatThrownBy(() -> service.criar(requestBase(CPF_A, "54321", "Google", null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));

        verify(medicoRepo, never()).save(any());
    }

    @Test
    void criar_crmDuplicado_lancaConflict() {
        when(medicoRepo.existsByCpfHash(any())).thenReturn(false);
        when(medicoRepo.existsByCrmAndCrmUf(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.criar(requestBase(CPF_A, "54321", "Google", null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));

        verify(medicoRepo, never()).save(any());
    }

    // ── atualizar ──────────────────────────────────────────────────────────

    @Test
    void atualizar_candidaturaInexistente_lancaNotFound() {
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(MEDICO_ID, requestBase(CPF_A, "54321", "Google", null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void atualizar_medicoCadastradoManualmente_lancaNotFound() {
        Medico manual = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        manual.setOrigemCadastro("MANUAL");
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(manual));

        assertThatThrownBy(() -> service.atualizar(MEDICO_ID, requestBase(CPF_A, "54321", "Google", null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void atualizar_statusJaAvancouAlemDeRascunho_lancaUnprocessableEntity() {
        Medico ativo = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.ATIVO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(ativo));

        assertThatThrownBy(() -> service.atualizar(MEDICO_ID, requestBase(CPF_A, "54321", "Google", null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(medicoRepo, never()).save(any());
    }

    @Test
    void atualizar_sucesso_atualizaDadosCivisEDoMedico() {
        String hashAtual = "hash-atual";
        Medico existente = medicoAutoCadastro(MEDICO_ID, hashAtual, StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(existente));
        when(cryptoService.encrypt(any())).thenReturn(new byte[]{7, 7});
        when(medicoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dadosCivisRepo.findById(MEDICO_ID)).thenReturn(Optional.empty());
        when(dadosCivisRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CandidaturaPublicaResponse resp = service.atualizar(MEDICO_ID,
            requestBase(CPF_A, "54321", "Google", null));

        assertThat(resp.nome()).isEqualTo("Dra. Maria Teste");
        assertThat(resp.cidade()).isEqualTo("Recife");
        verify(historicoRepo).save(any(HistoricoMedico.class));
    }

    @Test
    void atualizar_cpfMudouParaUmJaExistente_lancaConflict() {
        Medico existente = medicoAutoCadastro(MEDICO_ID, "hash-diferente-do-novo", StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(existente));
        when(medicoRepo.existsByCpfHash(any())).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(MEDICO_ID, requestBase(CPF_B, "54321", "Google", null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));

        verify(medicoRepo, never()).save(any());
    }

    // ── buscar ─────────────────────────────────────────────────────────────

    @Test
    void buscar_candidaturaInexistente_lancaNotFound() {
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(MEDICO_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void buscar_sucesso_retornaDadosDaCandidatura() {
        Medico medico = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        when(cryptoService.decrypt(any())).thenReturn(CPF_A);

        var dc = new DadosCivisMedico(MEDICO_ID);
        dc.setCidade("Recife");
        when(dadosCivisRepo.findById(MEDICO_ID)).thenReturn(Optional.of(dc));

        CandidaturaPublicaResponse resp = service.buscar(MEDICO_ID);

        assertThat(resp.id()).isEqualTo(MEDICO_ID);
        assertThat(resp.cpf()).isEqualTo(CPF_A);
        assertThat(resp.cidade()).isEqualTo("Recife");
    }

    @Test
    void buscar_permiteConsultaMesmoAposAtivado() {
        // buscar() é somente-leitura — deve funcionar mesmo depois do status avançar,
        // diferente de atualizar() que bloqueia. Usado pelo médico para ver o status.
        Medico ativo = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.ATIVO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(ativo));
        when(cryptoService.decrypt(any())).thenReturn(CPF_A);
        when(dadosCivisRepo.findById(MEDICO_ID)).thenReturn(Optional.empty());

        CandidaturaPublicaResponse resp = service.buscar(MEDICO_ID);

        assertThat(resp.status()).isEqualTo("ATIVO");
    }

    // ── helper: gera CPF válido a partir de uma base de 9 dígitos ──────────

    private static String cpfValido(String base9) {
        int[] firstWeights  = {10, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] secondWeights = {11, 10, 9, 8, 7, 6, 5, 4, 3, 2};
        int d1 = calcDigit(base9, firstWeights);
        int d2 = calcDigit(base9 + d1, secondWeights);
        return base9 + d1 + d2;
    }

    private static int calcDigit(String cpfPart, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (cpfPart.charAt(i) - '0') * weights[i];
        }
        int rem = (sum * 10) % 11;
        return rem == 10 || rem == 11 ? 0 : rem;
    }
}
