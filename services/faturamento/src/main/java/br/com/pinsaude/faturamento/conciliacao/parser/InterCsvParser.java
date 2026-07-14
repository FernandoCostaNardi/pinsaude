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
import java.util.List;

/**
 * Parser para extratos CSV do Banco Inter.
 *
 * Formato esperado (separador ';', encoding UTF-8 ou ISO-8859-1):
 *   Data;Tipo;Descrição;Valor;Saldo
 *   01/06/2026;Entrada;PIX RECEBIDO - EMPRESA X;1500,00;10000,00
 *   05/06/2026;Saída;PIX ENVIADO - PAGAMENTO Y;-500,00;9500,00
 *
 * Valores em reais com vírgula decimal. Positivo = crédito, negativo = débito.
 * A coluna "Tipo" é usada como fallback caso o valor não tenha sinal.
 */
@Component
public class InterCsvParser implements ExtratoBancarioParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SEPARATOR = ";";

    @Override
    public boolean suporta(BancoEnum banco, String nomeArquivo) {
        return banco == BancoEnum.INTER ||
               (banco == BancoEnum.OUTRO && nomeArquivo != null &&
                nomeArquivo.toLowerCase().contains("inter"));
    }

    @Override
    public List<LancamentoParseado> parse(byte[] content) throws ParseException {
        String text = decodeContent(content);
        String[] lines = text.lines().toArray(String[]::new);

        if (lines.length < 2) {
            throw new ParseException("CSV do Inter não possui linhas de dados após o cabeçalho", 0);
        }

        List<LancamentoParseado> result = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;

            String[] cols = line.split(SEPARATOR, -1);
            if (cols.length < 4) continue;

            try {
                LocalDate data     = LocalDate.parse(cols[0].trim(), DATE_FMT);
                String tipoCol     = cols.length > 1 ? cols[1].trim() : "";
                String descricao   = cols.length > 2 ? cols[2].trim() : cols[1].trim();
                String valorStr    = cols.length > 3 ? cols[3].trim() : cols[2].trim();

                long centavos = parseBrDecimal(valorStr);
                TipoLancamentoExtrato tipo = inferirTipo(centavos, tipoCol);
                long valorAbs = Math.abs(centavos);

                String fitid = "INTER-" + data + "-" + i + "-" + valorAbs;
                result.add(new LancamentoParseado(data, descricao, valorAbs, tipo, fitid));
            } catch (Exception e) {
                throw new ParseException("Erro na linha " + (i + 1) + " do CSV Inter: " + e.getMessage(), i);
            }
        }

        if (result.isEmpty()) {
            throw new ParseException("Nenhum lançamento encontrado no CSV do Inter", 0);
        }
        return result;
    }

    private String decodeContent(byte[] content) {
        // Remove BOM UTF-8 se presente
        if (content.length >= 3 &&
                (content[0] & 0xFF) == 0xEF &&
                (content[1] & 0xFF) == 0xBB &&
                (content[2] & 0xFF) == 0xBF) {
            content = java.util.Arrays.copyOfRange(content, 3, content.length);
        }
        // Tenta UTF-8 primeiro; fallback para ISO-8859-1
        String utf8 = new String(content, StandardCharsets.UTF_8);
        if (utf8.contains("??") || utf8.contains("�")) {
            return new String(content, Charset.forName("ISO-8859-1"));
        }
        return utf8;
    }

    private long parseBrDecimal(String valorStr) {
        // Remove R$, espaços, pontos de milhar; troca vírgula por ponto
        String clean = valorStr.replaceAll("[R$\\s]", "")
                               .replace(".", "")
                               .replace(",", ".");
        return new BigDecimal(clean)
                .multiply(BigDecimal.valueOf(100))
                .longValue();
    }

    private TipoLancamentoExtrato inferirTipo(long centavos, String tipoCol) {
        if (centavos < 0) return TipoLancamentoExtrato.DEBITO;
        if (centavos > 0) return TipoLancamentoExtrato.CREDITO;
        // Valor zero: usa coluna tipo como tiebreaker
        String t = tipoCol.toLowerCase();
        return (t.contains("saí") || t.contains("sai") || t.contains("débito") || t.contains("debito"))
               ? TipoLancamentoExtrato.DEBITO
               : TipoLancamentoExtrato.CREDITO;
    }
}
