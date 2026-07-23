package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.*;
import br.com.pinsaude.onboarding.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
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
    @Mock DadosBancariosMedicoRepository dadosBancariosRepo;
    @Mock DocumentoMedicoRepository documentoRepo;
    @Mock DeclaracoesLgpdMedicoRepository declaracoesLgpdRepo;
    @Mock HistoricoMedicoRepository historicoRepo;
    @Mock CryptoService cryptoService;
    @Mock StorageService storageService;
    @Mock NotificacaoService notificacaoService;
    @Mock KeycloakAdminService keycloakAdminService;

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
        m.setEmail("maria@exemplo.com");
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

    // ── uploadDocumento ────────────────────────────────────────────────────

    @Test
    void uploadDocumento_sucesso_salvaSemLimiteDeQuantidade() {
        Medico medico = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        when(storageService.upload(any(), any(), any())).thenReturn("documentos/x/CRM/123-arq.pdf");
        when(documentoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var arquivo = new MockMultipartFile("arquivo", "crm.pdf", "application/pdf", new byte[]{1, 2, 3});

        DocumentoMedicoResponse resp = service.uploadDocumento(MEDICO_ID, TipoDocumentoMedico.CRM, arquivo);

        assertThat(resp.tipo()).isEqualTo(TipoDocumentoMedico.CRM);
        assertThat(resp.statusValidacao()).isEqualTo(StatusValidacaoDocumento.PENDENTE);
        verify(historicoRepo).save(any(HistoricoMedico.class));
        // Sem verify(documentoRepo, times(N)).save — não há checagem de contagem/limite.
    }

    @Test
    void uploadDocumento_arquivoVazio_lancaBadRequest() {
        Medico medico = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        var vazio = new MockMultipartFile("arquivo", "vazio.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.uploadDocumento(MEDICO_ID, TipoDocumentoMedico.CRM, vazio))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadDocumento_candidaturaNaoEditavel_lancaUnprocessableEntity() {
        Medico ativo = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.ATIVO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(ativo));
        var arquivo = new MockMultipartFile("arquivo", "crm.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.uploadDocumento(MEDICO_ID, TipoDocumentoMedico.CRM, arquivo))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ── atualizarDadosBancarios ────────────────────────────────────────────

    @Test
    void atualizarDadosBancarios_pix_persisteChavePixSemConfirmarAlteracao() {
        Medico medico = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        when(cryptoService.encrypt(any())).thenReturn(new byte[]{9, 9});
        when(cryptoService.decrypt(any())).thenReturn("medico@exemplo.com");
        when(dadosBancariosRepo.findByMedicoId(MEDICO_ID)).thenReturn(Optional.empty());
        when(dadosBancariosRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new CandidaturaDadosBancariosRequest(
            "PIX", TipoPix.EMAIL, "medico@exemplo.com", null, null, null, null, null, null);

        DadosBancariosMedicoResponse resp = service.atualizarDadosBancarios(MEDICO_ID, req);

        assertThat(resp.tipoRecebimento()).isEqualTo("PIX");
        assertThat(resp.tipoPix()).isEqualTo(TipoPix.EMAIL);
        verify(historicoRepo).save(any(HistoricoMedico.class));
    }

    @Test
    void atualizarDadosBancarios_candidaturaNaoEditavel_lancaUnprocessableEntity() {
        Medico ativo = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.ATIVO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(ativo));

        var req = new CandidaturaDadosBancariosRequest(
            "TED", null, null, null, "341", "Itaú", "1234", "56789-0", "CORRENTE");

        assertThatThrownBy(() -> service.atualizarDadosBancarios(MEDICO_ID, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ── registrarDeclaracaoLgpd ────────────────────────────────────────────

    @Test
    void registrarDeclaracaoLgpd_sucesso_persisteAssinaturaEIp() {
        Medico medico = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        when(declaracoesLgpdRepo.findById(MEDICO_ID)).thenReturn(Optional.empty());
        ArgumentCaptor<DeclaracoesLgpdMedico> captor = ArgumentCaptor.forClass(DeclaracoesLgpdMedico.class);
        when(declaracoesLgpdRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        var req = new DeclaracaoLgpdRequest(true, true, true, true, "Maria Teste");

        DeclaracaoLgpdResponse resp = service.registrarDeclaracaoLgpd(MEDICO_ID, req, "200.100.50.25");

        assertThat(resp.assinaturaNome()).isEqualTo("Maria Teste");
        assertThat(captor.getValue().getIpOrigem()).isEqualTo("200.100.50.25");
        assertThat(captor.getValue().isCompleto()).isTrue();
        verify(historicoRepo).save(any(HistoricoMedico.class));
    }

    // ── finalizar ──────────────────────────────────────────────────────────

    @Test
    void finalizar_todosRequisitosCumpridos_criaUsuarioKeycloakDesabilitado() {
        Medico medico = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        when(medicoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DocumentoMedico crm = new DocumentoMedico();
        crm.setTipo(TipoDocumentoMedico.CRM);
        DocumentoMedico comprovante = new DocumentoMedico();
        comprovante.setTipo(TipoDocumentoMedico.COMPROVANTE_ENDERECO);
        when(documentoRepo.findByMedicoId(MEDICO_ID)).thenReturn(List.of(crm, comprovante));

        var lgpd = new DeclaracoesLgpdMedico(MEDICO_ID);
        lgpd.setAceiteDeclaracaoVeracidade(true);
        lgpd.setAutorizacaoUsoDados(true);
        lgpd.setAutorizacaoCompartilhamento(true);
        lgpd.setAvisoPrivacidadeLido(true);
        when(declaracoesLgpdRepo.findById(MEDICO_ID)).thenReturn(Optional.of(lgpd));

        when(keycloakAdminService.createUserDesabilitado("maria@exemplo.com", "Dra. Maria Teste", null))
            .thenReturn("kc-user-123");

        FinalizarCandidaturaResponse resp = service.finalizar(MEDICO_ID);

        assertThat(resp.id()).isEqualTo(MEDICO_ID);
        assertThat(resp.status()).isEqualTo("RASCUNHO");
        assertThat(medico.getKeycloakUserId()).isEqualTo("kc-user-123");
        verify(keycloakAdminService).createUserDesabilitado("maria@exemplo.com", "Dra. Maria Teste", null);
        verify(notificacaoService).notificarCandidaturaRecebida(medico);
        verify(historicoRepo).save(any(HistoricoMedico.class));
    }

    @Test
    void finalizar_keycloakUserIdJaExistente_naoCriaUsuarioDeNovo() {
        // Idempotência: retry de finalizar() não deve criar um segundo usuário Keycloak.
        Medico medico = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        medico.setKeycloakUserId("kc-user-ja-existente");
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));

        DocumentoMedico crm = new DocumentoMedico();
        crm.setTipo(TipoDocumentoMedico.CRM);
        DocumentoMedico comprovante = new DocumentoMedico();
        comprovante.setTipo(TipoDocumentoMedico.COMPROVANTE_ENDERECO);
        when(documentoRepo.findByMedicoId(MEDICO_ID)).thenReturn(List.of(crm, comprovante));

        var lgpd = new DeclaracoesLgpdMedico(MEDICO_ID);
        lgpd.setAceiteDeclaracaoVeracidade(true);
        lgpd.setAutorizacaoUsoDados(true);
        lgpd.setAutorizacaoCompartilhamento(true);
        lgpd.setAvisoPrivacidadeLido(true);
        when(declaracoesLgpdRepo.findById(MEDICO_ID)).thenReturn(Optional.of(lgpd));

        service.finalizar(MEDICO_ID);

        verify(keycloakAdminService, never()).createUserDesabilitado(any(), any(), any());
        verify(medicoRepo, never()).save(any());
    }

    @Test
    void finalizar_falhaAoCriarUsuarioKeycloak_lancaBadGatewayENaoNotifica() {
        Medico medico = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));

        DocumentoMedico crm = new DocumentoMedico();
        crm.setTipo(TipoDocumentoMedico.CRM);
        DocumentoMedico comprovante = new DocumentoMedico();
        comprovante.setTipo(TipoDocumentoMedico.COMPROVANTE_ENDERECO);
        when(documentoRepo.findByMedicoId(MEDICO_ID)).thenReturn(List.of(crm, comprovante));

        var lgpd = new DeclaracoesLgpdMedico(MEDICO_ID);
        lgpd.setAceiteDeclaracaoVeracidade(true);
        lgpd.setAutorizacaoUsoDados(true);
        lgpd.setAutorizacaoCompartilhamento(true);
        lgpd.setAvisoPrivacidadeLido(true);
        when(declaracoesLgpdRepo.findById(MEDICO_ID)).thenReturn(Optional.of(lgpd));

        when(keycloakAdminService.createUserDesabilitado(any(), any(), any()))
            .thenThrow(new RuntimeException("Keycloak indisponível"));

        assertThatThrownBy(() -> service.finalizar(MEDICO_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY));

        assertThat(medico.getKeycloakUserId()).isNull();
        verify(medicoRepo, never()).save(any());
        verify(historicoRepo, never()).save(any());
        verify(notificacaoService, never()).notificarCandidaturaRecebida(any());
    }

    @Test
    void finalizar_semDocumentosObrigatorios_lancaUnprocessableEntity() {
        Medico medico = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));
        when(documentoRepo.findByMedicoId(MEDICO_ID)).thenReturn(List.of());
        when(declaracoesLgpdRepo.findById(MEDICO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.finalizar(MEDICO_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                var rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(rse.getReason()).contains("CRM").contains("COMPROVANTE_ENDERECO").contains("LGPD");
            });

        verify(notificacaoService, never()).notificarCandidaturaRecebida(any());
    }

    @Test
    void finalizar_lgpdIncompleta_lancaUnprocessableEntity() {
        Medico medico = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.RASCUNHO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(medico));

        DocumentoMedico crm = new DocumentoMedico();
        crm.setTipo(TipoDocumentoMedico.CRM);
        DocumentoMedico comprovante = new DocumentoMedico();
        comprovante.setTipo(TipoDocumentoMedico.COMPROVANTE_ENDERECO);
        when(documentoRepo.findByMedicoId(MEDICO_ID)).thenReturn(List.of(crm, comprovante));

        var lgpdIncompleta = new DeclaracoesLgpdMedico(MEDICO_ID);
        lgpdIncompleta.setAceiteDeclaracaoVeracidade(true);
        // autorizacaoUsoDados/autorizacaoCompartilhamento/avisoPrivacidadeLido continuam false
        when(declaracoesLgpdRepo.findById(MEDICO_ID)).thenReturn(Optional.of(lgpdIncompleta));

        assertThatThrownBy(() -> service.finalizar(MEDICO_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(notificacaoService, never()).notificarCandidaturaRecebida(any());
    }

    @Test
    void finalizar_candidaturaJaAtivada_lancaUnprocessableEntity() {
        Medico ativo = medicoAutoCadastro(MEDICO_ID, "hash-x", StatusMedico.ATIVO);
        when(medicoRepo.findById(MEDICO_ID)).thenReturn(Optional.of(ativo));

        assertThatThrownBy(() -> service.finalizar(MEDICO_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(notificacaoService, never()).notificarCandidaturaRecebida(any());
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
