package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.EmitirNfseRequest;
import br.com.pinsaude.fiscal.dto.NotaFiscalStatusResponse;
import br.com.pinsaude.fiscal.dto.RegistrarEmissaoExternaRequest;
import br.com.pinsaude.fiscal.messaging.EmailNotificacaoProducer;
import br.com.pinsaude.fiscal.messaging.FaturamentoProducer;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NfseEmissaoExternaServiceTest {

    @Mock NotaFiscalRepository notaRepo;
    @Mock EmailNotificacaoProducer emailProducer;
    @Mock FaturamentoProducer faturamentoProducer;

    NfseEmissaoExternaService service;

    private static final String CNPJ = "12345678000195";
    private static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><NFSe><nNFSe>2026123</nNFSe></NFSe>";

    @BeforeEach
    void setUp() {
        service = new NfseEmissaoExternaService(notaRepo, emailProducer, faturamentoProducer);
    }

    private static RegistrarEmissaoExternaRequest request(UUID producaoId) {
        var nota = new EmitirNfseRequest(producaoId, UUID.randomUUID(), UUID.randomUUID(), "2026-10",
            100_000L, 15_000L, 2_000L, null, null, null, null, "Hospital X", "8610-1/01", "Plantões");
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
        verify(faturamentoProducer).notificarProducaoEmitida(producaoId, CNPJ);
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
}
