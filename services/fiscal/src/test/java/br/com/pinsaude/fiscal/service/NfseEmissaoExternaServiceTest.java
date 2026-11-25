package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.EmitirNfseRequest;
import br.com.pinsaude.fiscal.dto.NotaFiscalStatusResponse;
import br.com.pinsaude.fiscal.dto.RegistrarEmissaoExternaRequest;
import br.com.pinsaude.fiscal.messaging.EmailNotificacaoProducer;
import br.com.pinsaude.fiscal.messaging.FaturamentoProducer;
import br.com.pinsaude.fiscal.messaging.LedgerProducer;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.pinsaude.fiscal.domain.RegimeTributario;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NfseEmissaoExternaServiceTest {

    @Mock NotaFiscalRepository notaRepo;
    @Mock EmailNotificacaoProducer emailProducer;
    @Mock FaturamentoProducer faturamentoProducer;
    @Mock LedgerProducer ledgerProducer;
    @Mock MotorFiscalService motorFiscal;

    NfseEmissaoExternaService service;

    private static final String CNPJ = "12345678000195";
    private static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><NFSe><nNFSe>2026123</nNFSe></NFSe>";

    @BeforeEach
    void setUp() {
        service = new NfseEmissaoExternaService(notaRepo, emailProducer, faturamentoProducer, ledgerProducer, motorFiscal);
        lenient().when(motorFiscal.tributacaoIbsCbs(anyString(), anyString(), anyLong(), any(), any()))
            .thenReturn(TributacaoIbsCbs.legado(BigDecimal.ONE));
    }

    // 2027: CBS 11,2% e IBS 0,1% (UF 0,05% + mun 0,05%) com redução de 60% → 4,48% e 0,04%.
    private static TributacaoIbsCbs ibsCbs2027(long base) {
        long cbs = Math.round(base * 0.0448);
        long ibs = Math.round(base * 0.0004);
        long uf = Math.round(base * 0.0002);
        return new TributacaoIbsCbs(RegimeTributario.IBS_CBS, false, base,
            new BigDecimal("0.0448"), new BigDecimal("0.0004"), new BigDecimal("0.0002"), new BigDecimal("0.0002"),
            BigDecimal.ONE, cbs, ibs, uf, ibs - uf);
    }

    private static RegistrarEmissaoExternaRequest requestReforma(UUID producaoId, String cst, String cClassTrib,
                                                                 Long valorCbs, Long valorIbs) {
        var nota = new EmitirNfseRequest(producaoId, UUID.randomUUID(), UUID.randomUUID(), "2027-01",
            100_000L, 15_000L, 0L, 1_500L, 1_000L, 650L, 3_000L, "Hospital X", "8610-1/01", "Plantões", null,
            new BigDecimal("0.6"), cst, cClassTrib, null);
        return new RegistrarEmissaoExternaRequest(nota, "2027000001", LocalDate.of(2026, 11, 20), valorCbs, valorIbs);
    }

    private static RegistrarEmissaoExternaRequest request(UUID producaoId) {
        var nota = new EmitirNfseRequest(producaoId, UUID.randomUUID(), UUID.randomUUID(), "2026-10",
            100_000L, 15_000L, 2_000L, null, null, null, null, "Hospital X", "8610-1/01", "Plantões", null);
        return new RegistrarEmissaoExternaRequest(nota, " 2026123 ", LocalDate.of(2026, 10, 20));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("pdf", "nota.pdf", "application/pdf", PDF);
    }

    private static MockMultipartFile xml(String conteudo, java.nio.charset.Charset cs) {
        return new MockMultipartFile("xml", "nota.xml", "application/xml", conteudo.getBytes(cs));
    }

    private void saveDevolveArgumento() {
        when(notaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registrar_semNotaAnterior_criaNotaEmitidaComArquivos_eNotifica() {
        UUID producaoId = UUID.randomUUID();
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.empty());
        saveDevolveArgumento();

        NotaFiscalStatusResponse r = service.registrar(request(producaoId), pdf(), xml(XML, StandardCharsets.UTF_8), CNPJ);

        assertThat(r.status()).isEqualTo(StatusNota.EMITIDA);
        assertThat(r.numeroNota()).isEqualTo("2026123");
        assertThat(r.emissaoExterna()).isTrue();
        assertThat(r.temPdf()).isTrue();
        assertThat(r.temXml()).isTrue();
        assertThat(r.valorLiquidoMedico()).isEqualTo(85_000L);
        assertThat(r.emitidaAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        verify(emailProducer).notificarNotaEmitida(any(NotaFiscal.class));
        verify(faturamentoProducer).notificarProducaoEmitida(argThat(n -> producaoId.equals(n.getProducaoId())));
        verify(ledgerProducer).notificarNotaEmitida(argThat(n -> producaoId.equals(n.getProducaoId())));
    }

    @Test
    void registrar_soPdf_aceita() {
        UUID producaoId = UUID.randomUUID();
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.empty());
        saveDevolveArgumento();

        NotaFiscalStatusResponse r = service.registrar(request(producaoId), pdf(), null, CNPJ);

        assertThat(r.temPdf()).isTrue();
        assertThat(r.temXml()).isFalse();
    }

    @Test
    void registrar_semArquivos_retorna400() {
        assertThatThrownBy(() -> service.registrar(request(UUID.randomUUID()), null, null, CNPJ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("PDF e/ou o XML");
        verify(notaRepo, never()).save(any());
    }

    @Test
    void registrar_pdfInvalido_retorna400() {
        var falso = new MockMultipartFile("pdf", "nota.pdf", "application/pdf", "não é pdf".getBytes());
        assertThatThrownBy(() -> service.registrar(request(UUID.randomUUID()), falso, null, CNPJ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("PDF válido");
    }

    @Test
    void registrar_xmlMalFormado_retorna400() {
        assertThatThrownBy(() -> service.registrar(request(UUID.randomUUID()), null,
                xml("<NFSe><aberto></NFSe>", StandardCharsets.UTF_8), CNPJ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("XML válido");
    }

    @Test
    void registrar_xmlComDoctype_recusado() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>";
        assertThatThrownBy(() -> service.registrar(request(UUID.randomUUID()), null, xml(xxe, StandardCharsets.UTF_8), CNPJ))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void registrar_notaEmitidaJaExiste_retorna409() {
        UUID producaoId = UUID.randomUUID();
        var existente = new NotaFiscal();
        existente.setStatus(StatusNota.EMITIDA);
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.registrar(request(producaoId), pdf(), null, CNPJ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("EMITIDA");
        verify(notaRepo, never()).save(any());
    }

    @Test
    void registrar_notaAguardandoEmissaoManual_reaproveitaNota() {
        UUID producaoId = UUID.randomUUID();
        var existente = new NotaFiscal();
        existente.setProducaoId(producaoId);
        existente.setStatus(StatusNota.AGUARDANDO_EMISSAO_MANUAL);
        existente.setObservacoes("Agregador indisponível");
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.of(existente));
        saveDevolveArgumento();

        service.registrar(request(producaoId), pdf(), null, CNPJ);

        assertThat(existente.getStatus()).isEqualTo(StatusNota.EMITIDA);
        assertThat(existente.getObservacoes()).isNull();
        assertThat(existente.isEmissaoExterna()).isTrue();
    }

    @Test
    void registrar_xmlIso88591_decodificaPeloProlog() {
        UUID producaoId = UUID.randomUUID();
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.empty());
        when(notaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String latin = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><NFSe><Discriminacao>Serviços médicos</Discriminacao></NFSe>";

        service.registrar(request(producaoId), null, xml(latin, StandardCharsets.ISO_8859_1), CNPJ);

        verify(notaRepo).save(argThat(n -> n.getXmlNota().contains("Serviços médicos")));
    }

    @Test
    void anexarArquivos_notaEmitida_mantemOQueNaoFoiEnviado() {
        UUID notaId = UUID.randomUUID();
        var nota = new NotaFiscal();
        nota.setStatus(StatusNota.EMITIDA);
        nota.setPdfNota(PDF);
        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));
        saveDevolveArgumento();

        NotaFiscalStatusResponse r = service.anexarArquivos(notaId, null, xml(XML, StandardCharsets.UTF_8));

        assertThat(r.temPdf()).isTrue();
        assertThat(r.temXml()).isTrue();
    }

    @Test
    void anexarArquivos_notaNaoEmitida_retorna409() {
        UUID notaId = UUID.randomUUID();
        var nota = new NotaFiscal();
        nota.setStatus(StatusNota.CANCELADA);
        when(notaRepo.findById(notaId)).thenReturn(Optional.of(nota));

        assertThatThrownBy(() -> service.anexarArquivos(notaId, pdf(), null))
            .isInstanceOf(ResponseStatusException.class);
    }

    // ─── CBS-42: reforma tributária na nota emitida na prefeitura ──────────────

    @Test
    void registrar_competenciaLegado_gravaRegimeLegadoSemCbsIbs() {
        UUID producaoId = UUID.randomUUID();
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.empty());
        saveDevolveArgumento();

        NotaFiscalStatusResponse r = service.registrar(request(producaoId), pdf(), null, CNPJ);

        assertThat(r.regimeTributario()).isEqualTo(RegimeTributario.LEGADO);
        assertThat(r.valorCbs()).isZero();
        assertThat(r.valorIbs()).isZero();
    }

    @Test
    void registrar_competenciaIbsCbs_gravaCbsIbsDoMotor_eZeraPisCofins() {
        UUID producaoId = UUID.randomUUID();
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.empty());
        saveDevolveArgumento();
        var t = ibsCbs2027(95_310L);
        when(motorFiscal.tributacaoIbsCbs(eq(CNPJ), eq("2027-01"), eq(100_000L), any(), any())).thenReturn(t);

        service.registrar(requestReforma(producaoId, "200", "200029", null, null), pdf(), null, "12.345.678/0001-95");

        verify(notaRepo).save(argThat(n ->
            n.getRegimeTributario() == RegimeTributario.IBS_CBS
                && n.getValorCbs() == t.valorCbs() && n.getValorIbs() == t.valorIbs()
                && n.getValorIbsUf() + n.getValorIbsMun() == n.getValorIbs()
                && n.getValorPis() == 0L && n.getValorCofins() == 0L
                && n.getValorIr() == 1_500L && n.getValorCsll() == 1_000L
                && "200".equals(n.getCstIbsCbs()) && "200029".equals(n.getCClassTrib())
                && n.getBaseCalculoIbsCbs() == 95_310L
                && new BigDecimal("0.6").compareTo(n.getReducaoIbsCbs()) == 0));
        verify(ledgerProducer).notificarNotaEmitida(argThat(n -> n.getValorCbs() == t.valorCbs()));
    }

    @Test
    void registrar_competenciaIbsCbs_valoresDaNotaReal_substituemOCalculo() {
        UUID producaoId = UUID.randomUUID();
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.empty());
        saveDevolveArgumento();
        when(motorFiscal.tributacaoIbsCbs(anyString(), anyString(), anyLong(), any(), any()))
            .thenReturn(ibsCbs2027(95_310L));

        NotaFiscalStatusResponse r = service.registrar(
            requestReforma(producaoId, "200", "200029", 4_300L, 41L), pdf(), null, CNPJ);

        assertThat(r.valorCbs()).isEqualTo(4_300L);
        assertThat(r.valorIbs()).isEqualTo(41L);
        assertThat(r.valorIbsUf() + r.valorIbsMun()).isEqualTo(41L);
        assertThat(r.valorIbsUf()).isEqualTo(21L);   // metade (UF = mun), arredondado
    }

    @Test
    void registrar_competenciaIbsCbs_soCbsCorrigida_mantemIbsDoMotor() {
        UUID producaoId = UUID.randomUUID();
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.empty());
        saveDevolveArgumento();
        var t = ibsCbs2027(95_310L);
        when(motorFiscal.tributacaoIbsCbs(anyString(), anyString(), anyLong(), any(), any())).thenReturn(t);

        NotaFiscalStatusResponse r = service.registrar(
            requestReforma(producaoId, "200", "200029", 4_300L, null), pdf(), null, CNPJ);

        assertThat(r.valorCbs()).isEqualTo(4_300L);
        assertThat(r.valorIbs()).isEqualTo(t.valorIbs());
    }

    @Test
    void registrar_competenciaIbsCbs_servicoSemClassificacao_retorna422() {
        UUID producaoId = UUID.randomUUID();
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.empty());
        when(motorFiscal.tributacaoIbsCbs(anyString(), anyString(), anyLong(), any(), any()))
            .thenReturn(ibsCbs2027(95_310L));

        assertThatThrownBy(() -> service.registrar(requestReforma(producaoId, null, null, null, null), pdf(), null, CNPJ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("cClassTrib");
        verify(notaRepo, never()).save(any());
    }

    @Test
    void registrar_competenciaLegado_comValorCbsInformado_retorna422() {
        UUID producaoId = UUID.randomUUID();
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(requestReforma(producaoId, "200", "200029", 4_300L, null), pdf(), null, CNPJ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("não está no regime CBS/IBS");
        verify(notaRepo, never()).save(any());
    }

    @Test
    void registrar_notaReaproveitada_competenciaLegado_limpaCbsIbsAnteriores() {
        UUID producaoId = UUID.randomUUID();
        var existente = new NotaFiscal();
        existente.setProducaoId(producaoId);
        existente.setStatus(StatusNota.ERRO);
        existente.setRegimeTributario(RegimeTributario.IBS_CBS);
        existente.setValorCbs(4_270L);
        existente.setValorIbs(38L);
        existente.setValorIbsUf(19L);
        existente.setValorIbsMun(19L);
        existente.setCClassTrib("200029");
        when(notaRepo.findByProducaoId(producaoId)).thenReturn(Optional.of(existente));
        saveDevolveArgumento();

        service.registrar(request(producaoId), pdf(), null, CNPJ);

        assertThat(existente.getRegimeTributario()).isEqualTo(RegimeTributario.LEGADO);
        assertThat(existente.getValorCbs()).isZero();
        assertThat(existente.getValorIbs()).isZero();
        assertThat(existente.getValorIbsUf()).isZero();
        assertThat(existente.getCClassTrib()).isNull();
    }

    @Test
    void parteUf_semAliquotas_eZero_eProporcionalQuandoHa() {
        assertThat(ReformaNota.parteUf(100L, null, null)).isZero();
        assertThat(ReformaNota.parteUf(100L, new BigDecimal("0.0002"), new BigDecimal("0.0006"))).isEqualTo(25L);
    }
}
