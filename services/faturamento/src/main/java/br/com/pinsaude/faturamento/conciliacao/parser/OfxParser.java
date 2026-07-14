package br.com.pinsaude.faturamento.conciliacao.parser;

import br.com.pinsaude.faturamento.domain.BancoEnum;
import br.com.pinsaude.faturamento.domain.TipoLancamentoExtrato;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser para arquivos OFX (Open Financial Exchange).
 *
 * Suporta dois formatos:
 *  - OFX 1.x (SGML) — cabeçalho "OFXHEADER:100", sem XML declaration
 *  - OFX 2.x (XML)  — inicia com <?xml ...> ou <OFX>
 *
 * Extrai STMTTRN com campos: TRNTYPE, DTPOSTED, TRNAMT, FITID, NAME/MEMO.
 * Valores em dólares/reais com ponto decimal (padrão OFX).
 * DTPOSTED: 8 primeiros dígitos como yyyyMMdd.
 */
@Component
public class OfxParser implements ExtratoBancarioParser {

    private static final DateTimeFormatter OFX_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern STMTTRN_PATTERN =
            Pattern.compile("<STMTTRN>.*?</STMTTRN>|<STMTTRN>.*?(?=<STMTTRN>|</BANKTRANLIST>)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_PATTERN =
            Pattern.compile("<([A-Z]+)>([^<\n\r]+)", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean suporta(BancoEnum banco, String nomeArquivo) {
        return nomeArquivo != null && (
               nomeArquivo.toLowerCase().endsWith(".ofx") ||
               nomeArquivo.toLowerCase().endsWith(".qfx"));
    }

    @Override
    public List<LancamentoParseado> parse(byte[] content) throws ParseException {
        String text = new String(content, StandardCharsets.UTF_8);
        // Remove BOM se houver
        if (text.startsWith("﻿")) text = text.substring(1);
        text = text.trim();

        if (isXml(text)) {
            return parseXml(content);
        }
        return parseSgml(text);
    }

    private boolean isXml(String text) {
        return text.startsWith("<?xml") || text.startsWith("<OFX>") || text.startsWith("<ofx>");
    }

    // ─── SGML 1.x ─────────────────────────────────────────────────────────────

    private List<LancamentoParseado> parseSgml(String text) throws ParseException {
        // Converte SGML para XML injetando tags de fechamento
        String xmlLike = convertSgmlToXml(text);
        List<LancamentoParseado> result = new ArrayList<>();

        Matcher m = STMTTRN_PATTERN.matcher(xmlLike);
        int idx = 0;
        while (m.find()) {
            String block = m.group();
            result.add(parseTrn(block, ++idx));
        }

        if (result.isEmpty()) {
            throw new ParseException("Nenhum lançamento (STMTTRN) encontrado no arquivo OFX", 0);
        }
        return result;
    }

    private String convertSgmlToXml(String text) {
        // Remove cabeçalho OFXHEADER até a primeira linha em branco
        int headerEnd = text.indexOf("\n\n");
        if (headerEnd < 0) headerEnd = text.indexOf("\r\n\r\n");
        String body = headerEnd >= 0 ? text.substring(headerEnd).trim() : text;

        // Tags SGML (ex: <TAG>valor\n) — adiciona </TAG> antes da próxima tag ou bloco
        return body.replaceAll("(?m)^<([A-Z]+)>([^\n<]+)$", "<$1>$2</$1>");
    }

    // ─── XML 2.x ──────────────────────────────────────────────────────────────

    private List<LancamentoParseado> parseXml(byte[] content) throws ParseException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
            NodeList stmtTrnList = doc.getElementsByTagName("STMTTRN");
            if (stmtTrnList.getLength() == 0) {
                throw new ParseException("Nenhum lançamento (STMTTRN) encontrado no XML OFX", 0);
            }

            List<LancamentoParseado> result = new ArrayList<>();
            for (int i = 0; i < stmtTrnList.getLength(); i++) {
                String block = stmtTrnList.item(i).getTextContent();
                // textContent aplana tudo — vamos re-parsear como SGML simples
                // O bloco vem como texto plano com os valores
                NodeList children = stmtTrnList.item(i).getChildNodes();
                String trnType = "", dtPosted = "", trnAmt = "", fitId = "", name = "";
                for (int j = 0; j < children.getLength(); j++) {
                    var node = children.item(j);
                    switch (node.getNodeName().toUpperCase()) {
                        case "TRNTYPE"  -> trnType  = node.getTextContent().trim();
                        case "DTPOSTED" -> dtPosted  = node.getTextContent().trim();
                        case "TRNAMT"   -> trnAmt    = node.getTextContent().trim();
                        case "FITID"    -> fitId     = node.getTextContent().trim();
                        case "NAME"     -> name      = node.getTextContent().trim();
                        case "MEMO"     -> { if (name.isBlank()) name = node.getTextContent().trim(); }
                    }
                }
                result.add(buildLancamento(trnType, dtPosted, trnAmt, fitId, name, i + 1));
            }
            return result;
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Erro ao parsear OFX XML: " + e.getMessage(), 0);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private LancamentoParseado parseTrn(String block, int idx) throws ParseException {
        String trnType = "", dtPosted = "", trnAmt = "", fitId = "", name = "";
        Matcher m = TAG_PATTERN.matcher(block);
        while (m.find()) {
            switch (m.group(1).toUpperCase()) {
                case "TRNTYPE"  -> trnType  = m.group(2).trim();
                case "DTPOSTED" -> dtPosted  = m.group(2).trim();
                case "TRNAMT"   -> trnAmt    = m.group(2).trim();
                case "FITID"    -> fitId     = m.group(2).trim();
                case "NAME"     -> name      = m.group(2).trim();
                case "MEMO"     -> { if (name.isBlank()) name = m.group(2).trim(); }
            }
        }
        return buildLancamento(trnType, dtPosted, trnAmt, fitId, name, idx);
    }

    private LancamentoParseado buildLancamento(String trnType, String dtPosted,
                                               String trnAmt, String fitId,
                                               String name, int idx) throws ParseException {
        if (dtPosted.length() < 8) {
            throw new ParseException("DTPOSTED inválido no lançamento #" + idx + ": " + dtPosted, idx);
        }
        if (trnAmt.isBlank()) {
            throw new ParseException("TRNAMT ausente no lançamento #" + idx, idx);
        }

        LocalDate data = LocalDate.parse(dtPosted.substring(0, 8), OFX_DATE);

        long centavos = new BigDecimal(trnAmt.replace(",", "."))
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        TipoLancamentoExtrato tipo;
        if ("CREDIT".equalsIgnoreCase(trnType) || "DEP".equalsIgnoreCase(trnType)
                || "XFER".equalsIgnoreCase(trnType) && centavos > 0) {
            tipo = TipoLancamentoExtrato.CREDITO;
        } else if ("DEBIT".equalsIgnoreCase(trnType) || "CHECK".equalsIgnoreCase(trnType)
                || "PAYMENT".equalsIgnoreCase(trnType)) {
            tipo = TipoLancamentoExtrato.DEBITO;
        } else {
            tipo = centavos >= 0 ? TipoLancamentoExtrato.CREDITO : TipoLancamentoExtrato.DEBITO;
        }

        long valorAbs = Math.abs(centavos);
        String descricao = name.isBlank() ? trnType : name;
        String identificador = fitId.isBlank() ? "OFX-" + data + "-" + idx : fitId;

        return new LancamentoParseado(data, descricao, valorAbs, tipo, identificador);
    }
}
