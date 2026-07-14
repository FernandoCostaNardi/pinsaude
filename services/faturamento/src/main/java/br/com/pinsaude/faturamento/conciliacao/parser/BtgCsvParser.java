package br.com.pinsaude.faturamento.conciliacao.parser;

import br.com.pinsaude.faturamento.domain.BancoEnum;
import br.com.pinsaude.faturamento.domain.TipoLancamentoExtrato;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parser para extratos CSV do BTG Pactual.
 *
 * Formato esperado (separador ',', encoding UTF-8):
 *   Data,Lançamento,Valor,Saldo
 *   01/06/2026,PIX RECEBIDO - EMPRESA X,"1.500,00","10.000,00"
 *   05/06/2026,PIX ENVIADO - PAGAMENTO Y,"-500,00","9.500,00"
 *
 * Valores podem estar entre aspas e usar ponto de milhar + vírgula decimal.
 * Negativo = débito, positivo = crédito.
 */
@Component
public class BtgCsvParser implements ExtratoBancarioParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SEPARATOR = ",";

    @Override
    public boolean suporta(BancoEnum banco, String nomeArquivo) {
        return banco == BancoEnum.BTG ||
               (banco == BancoEnum.OUTRO && nomeArquivo != null &&
                nomeArquivo.toLowerCase().contains("btg"));
    }

    @Override
    public List<LancamentoParseado> parse(byte[] content) throws ParseException {
        String text = decodeContent(content);
        String[] lines = text.lines().toArray(String[]::new);

        if (lines.length < 2) {
            throw new ParseException("CSV do BTG não possui linhas de dados após o cabeçalho", 0);
        }

        List<LancamentoParseado> result = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;

            String[] cols = splitCsv(line);
            if (cols.length < 3) continue;

            try {
                LocalDate data    = LocalDate.parse(cols[0].trim(), DATE_FMT);
                String descricao  = unquote(cols[1].trim());
                String valorStr   = unquote(cols[2].trim());

                long centavos = parseBrDecimal(valorStr);
                TipoLancamentoExtrato tipo = centavos < 0
                        ? TipoLancamentoExtrato.DEBITO
                        : TipoLancamentoExtrato.CREDITO;
                long valorAbs = Math.abs(centavos);

                String fitid = "BTG-" + data + "-" + i + "-" + valorAbs;
                result.add(new LancamentoParseado(data, descricao, valorAbs, tipo, fitid));
            } catch (Exception e) {
                throw new ParseException("Erro na linha " + (i + 1) + " do CSV BTG: " + e.getMessage(), i);
            }
        }

        if (result.isEmpty()) {
            throw new ParseException("Nenhum lançamento encontrado no CSV do BTG", 0);
        }
        return result;
    }

    // Split simples respeitando aspas duplas (campos com vírgula dentro de "...")
    private String[] splitCsv(String line) {
        List<String> tokens = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }

    private String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private String decodeContent(byte[] content) {
        if (content.length >= 3 &&
                (content[0] & 0xFF) == 0xEF &&
                (content[1] & 0xFF) == 0xBB &&
                (content[2] & 0xFF) == 0xBF) {
            content = Arrays.copyOfRange(content, 3, content.length);
        }
        String utf8 = new String(content, StandardCharsets.UTF_8);
        if (utf8.contains("�")) {
            return new String(content, Charset.forName("ISO-8859-1"));
        }
        return utf8;
    }

    private long parseBrDecimal(String valorStr) {
        String clean = valorStr.replaceAll("[R$\\s\"]", "")
                               .replace(".", "")
                               .replace(",", ".");
        return new BigDecimal(clean)
                .multiply(BigDecimal.valueOf(100))
                .longValue();
    }
}
