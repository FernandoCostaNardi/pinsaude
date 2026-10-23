package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.EmitirNfseRequest;
import br.com.pinsaude.fiscal.dto.NotaFiscalStatusResponse;
import br.com.pinsaude.fiscal.dto.RegistrarEmissaoExternaRequest;
import br.com.pinsaude.fiscal.messaging.EmailNotificacaoProducer;
import br.com.pinsaude.fiscal.messaging.FaturamentoProducer;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emissão externa de NFS-e: enquanto a emissão automática pelo agregador não está em uso, a nota é
 * emitida no portal da prefeitura e a operação registra aqui número, data e o PDF e/ou XML recebidos.
 * A nota já nasce EMITIDA e dispara os mesmos eventos da emissão pelo sistema (e-mail ao médico e
 * ProducaoEmitida para o faturamento).
 */
@Service
public class NfseEmissaoExternaService {

    private static final Logger log = LoggerFactory.getLogger(NfseEmissaoExternaService.class);

    static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Pattern XML_ENCODING = Pattern.compile("^<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

    // Nota criada pelo fluxo antigo (fila do agregador) que ainda não virou documento fiscal pode ser
    // substituída pelo registro externo; EMITIDA, CANCELADA e PROCESSANDO não.
    private static final Set<StatusNota> SUBSTITUIVEIS = EnumSet.of(
        StatusNota.PENDENTE, StatusNota.AGUARDANDO_VALIDACAO, StatusNota.AGUARDANDO_EMISSAO_MANUAL,
        StatusNota.ERRO, StatusNota.REJEITADA);

    private final NotaFiscalRepository notaRepo;
    private final EmailNotificacaoProducer emailProducer;
    private final FaturamentoProducer faturamentoProducer;

    public NfseEmissaoExternaService(NotaFiscalRepository notaRepo,
                                     EmailNotificacaoProducer emailProducer,
                                     FaturamentoProducer faturamentoProducer) {
        this.notaRepo            = notaRepo;
        this.emailProducer       = emailProducer;
        this.faturamentoProducer = faturamentoProducer;
    }

    @Transactional
    public NotaFiscalStatusResponse registrar(RegistrarEmissaoExternaRequest req,
                                              MultipartFile pdf, MultipartFile xml,
                                              String cnpjTenant) {
        byte[] pdfBytes = lerPdf(pdf);
        String xmlTexto = lerXml(xml);
        if (pdfBytes == null && xmlTexto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anexe o PDF e/ou o XML da nota emitida");
        }

        EmitirNfseRequest dados = req.nota();
        NotaFiscal nota = notaRepo.findByProducaoId(dados.producaoId()).orElse(null);
        if (nota != null && !SUBSTITUIVEIS.contains(nota.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Esta produção já tem nota com status " + nota.getStatus());
        }
        if (nota == null) {
            nota = new NotaFiscal();
            nota.setProducaoId(dados.producaoId());
        }

        nota.setCnpjIdTenant(cnpjTenant);
        nota.setMedicoId(dados.medicoId());
        nota.setTomadorId(dados.tomadorId());
        nota.setCompetencia(dados.competencia());
        nota.setValorBruto(dados.valorBruto());
        nota.setTaxaPin(dados.taxaPin());
        nota.setValorLiquidoMedico(dados.valorBruto() - dados.taxaPin());
        nota.setValorIss(valorOuZero(dados.valorIss()));
        nota.setValorIr(valorOuZero(dados.valorIr()));
        nota.setValorCsll(valorOuZero(dados.valorCsll()));
        nota.setValorPis(valorOuZero(dados.valorPis()));
        nota.setValorCofins(valorOuZero(dados.valorCofins()));
        nota.setTomadorNome(dados.tomadorNome());
        nota.setCnaeCodigo(dados.cnaeCodigo());
        nota.setDiscriminacao(dados.discriminacao());

        nota.setStatus(StatusNota.EMITIDA);
        nota.setEmissaoExterna(true);
        nota.setNumeroNota(req.numeroNota().trim());
        nota.setProtocoloEmissao(null);
        nota.setObservacoes(null);
        // Só a data importa (o portal da prefeitura é a fonte); meio-dia evita virar o dia em fuso.
        nota.setEmitidaAt(req.dataEmissao().atTime(LocalTime.NOON).atZone(FUSO).toOffsetDateTime());
        nota.setPdfNota(pdfBytes);
        nota.setXmlNota(xmlTexto);

        NotaFiscal salva = notaRepo.save(nota);
        log.info("NotaFiscal {} registrada como emitida externamente: numero={} producaoId={} pdf={} xml={}",
            salva.getId(), salva.getNumeroNota(), salva.getProducaoId(), pdfBytes != null, xmlTexto != null);

        aposCommit(() -> {
            emailProducer.notificarNotaEmitida(salva);
            faturamentoProducer.notificarProducaoEmitida(salva.getProducaoId(), salva.getCnpjIdTenant());
        });
        return NotaFiscalStatusResponse.from(salva);
    }

    /**
     * Anexa ou substitui o PDF e/ou XML de uma nota já emitida (ex.: registrou só o PDF e o XML
     * chegou depois). O que não for enviado é mantido.
     */
    @Transactional
    public NotaFiscalStatusResponse anexarArquivos(UUID notaId, MultipartFile pdf, MultipartFile xml) {
        byte[] pdfBytes = lerPdf(pdf);
        String xmlTexto = lerXml(xml);
        if (pdfBytes == null && xmlTexto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione o PDF e/ou o XML");
        }
        NotaFiscal nota = notaRepo.findById(notaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nota não encontrada"));
        if (nota.getStatus() != StatusNota.EMITIDA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Só é possível anexar arquivos a notas emitidas (status atual: " + nota.getStatus() + ")");
        }
        if (pdfBytes != null) nota.setPdfNota(pdfBytes);
        if (xmlTexto != null) nota.setXmlNota(xmlTexto);
        log.info("Arquivos anexados à NotaFiscal {}: pdf={} xml={}", notaId, pdfBytes != null, xmlTexto != null);
        return NotaFiscalStatusResponse.from(notaRepo.save(nota));
    }

    private static long valorOuZero(Long v) {
        return v != null ? v : 0L;
    }

    static byte[] lerPdf(MultipartFile arquivo) {
        byte[] bytes = lerBytes(arquivo, "PDF");
        if (bytes == null) return null;
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo enviado como PDF não é um PDF válido");
        }
        return bytes;
    }

    static String lerXml(MultipartFile arquivo) {
        byte[] bytes = lerBytes(arquivo, "XML");
        if (bytes == null) return null;
        String texto = decodificarXml(bytes);
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.newDocumentBuilder().parse(new InputSource(new StringReader(texto)));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo enviado como XML não é um XML válido");
        }
        return texto;
    }

    // Respeita o encoding declarado no prólogo (alguns portais municipais geram ISO-8859-1);
    // sem declaração, XML é UTF-8. O BOM, se houver, é descartado.
    static String decodificarXml(byte[] bytes) {
        int inicio = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            inicio = 3;
        }
        String cabecalho = new String(bytes, inicio, Math.min(200, bytes.length - inicio), StandardCharsets.US_ASCII);
        return new String(bytes, inicio, bytes.length - inicio, charsetDeclarado(cabecalho));
    }

    /** Charset do prólogo do XML (encoding="..."), ou UTF-8 se ausente/desconhecido. */
    public static Charset charsetDeclarado(String xml) {
        Matcher m = XML_ENCODING.matcher(xml.stripLeading());
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (Exception ignored) {
                // encoding desconhecido: segue como UTF-8 e o parser decide se é válido
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static byte[] lerBytes(MultipartFile arquivo, String tipo) {
        if (arquivo == null || arquivo.isEmpty()) return null;
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "O " + tipo + " deve ter no máximo 10 MB");
        }
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o " + tipo + " enviado");
        }
    }

    private static void aposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
        } else {
            acao.run();
        }
    }
}
