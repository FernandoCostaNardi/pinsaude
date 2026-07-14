package br.com.pinsaude.faturamento.conciliacao;

import br.com.pinsaude.faturamento.conciliacao.parser.BtgCsvParser;
import br.com.pinsaude.faturamento.conciliacao.parser.InterCsvParser;
import br.com.pinsaude.faturamento.conciliacao.parser.LancamentoParseado;
import br.com.pinsaude.faturamento.conciliacao.parser.OfxParser;
import br.com.pinsaude.faturamento.domain.BancoEnum;
import br.com.pinsaude.faturamento.domain.TipoLancamentoExtrato;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ParserTest {

    // ─── Inter CSV ────────────────────────────────────────────────────────────

    private static final String INTER_CSV = """
            Data;Tipo;Descrição;Valor;Saldo
            01/06/2026;Entrada;PIX RECEBIDO - EMPRESA X;1500,00;10000,00
            05/06/2026;Saída;PIX ENVIADO - PAGAMENTO Y;-500,00;9500,00
            10/06/2026;Entrada;TED RECEBIDA;2000,50;11500,50
            """;

    @Test
    void interCsvParser_suporta_bancoINTER() {
        var parser = new InterCsvParser();
        assertThat(parser.suporta(BancoEnum.INTER, "extrato.csv")).isTrue();
        assertThat(parser.suporta(BancoEnum.BTG,   "extrato.csv")).isFalse();
    }

    @Test
    void interCsvParser_parse_3lancamentos() throws ParseException {
        var parser = new InterCsvParser();
        List<LancamentoParseado> result = parser.parse(INTER_CSV.getBytes(StandardCharsets.UTF_8));

        assertThat(result).hasSize(3);

        LancamentoParseado credito = result.get(0);
        assertThat(credito.tipo()).isEqualTo(TipoLancamentoExtrato.CREDITO);
        assertThat(credito.valorCentavos()).isEqualTo(150_000L);
        assertThat(credito.descricao()).isEqualTo("PIX RECEBIDO - EMPRESA X");

        LancamentoParseado debito = result.get(1);
        assertThat(debito.tipo()).isEqualTo(TipoLancamentoExtrato.DEBITO);
        assertThat(debito.valorCentavos()).isEqualTo(50_000L);

        LancamentoParseado terceiro = result.get(2);
        assertThat(terceiro.valorCentavos()).isEqualTo(200_050L);
    }

    @Test
    void interCsvParser_cabeçalhoVazio_lançaParseException() {
        var parser = new InterCsvParser();
        assertThatThrownBy(() -> parser.parse("Data;Tipo;Descrição;Valor;Saldo\n"
                .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("CSV do Inter");
    }

    @Test
    void interCsvParser_arquivoVazio_lançaParseException() {
        var parser = new InterCsvParser();
        assertThatThrownBy(() -> parser.parse("".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ParseException.class);
    }

    // ─── BTG CSV ──────────────────────────────────────────────────────────────

    private static final String BTG_CSV = """
            Data,Lançamento,Valor,Saldo
            01/06/2026,PIX RECEBIDO - EMPRESA X,"1.500,00","10.000,00"
            05/06/2026,PIX ENVIADO - PAGAMENTO Y,"-500,00","9.500,00"
            """;

    @Test
    void btgCsvParser_suporta_bancoBTG() {
        var parser = new BtgCsvParser();
        assertThat(parser.suporta(BancoEnum.BTG,   "extrato.csv")).isTrue();
        assertThat(parser.suporta(BancoEnum.INTER, "extrato.csv")).isFalse();
    }

    @Test
    void btgCsvParser_parse_2lancamentos() throws ParseException {
        var parser = new BtgCsvParser();
        List<LancamentoParseado> result = parser.parse(BTG_CSV.getBytes(StandardCharsets.UTF_8));

        assertThat(result).hasSize(2);

        LancamentoParseado credito = result.get(0);
        assertThat(credito.tipo()).isEqualTo(TipoLancamentoExtrato.CREDITO);
        assertThat(credito.valorCentavos()).isEqualTo(150_000L);
        assertThat(credito.descricao()).isEqualTo("PIX RECEBIDO - EMPRESA X");

        LancamentoParseado debito = result.get(1);
        assertThat(debito.tipo()).isEqualTo(TipoLancamentoExtrato.DEBITO);
        assertThat(debito.valorCentavos()).isEqualTo(50_000L);
    }

    @Test
    void btgCsvParser_semDados_lançaParseException() {
        var parser = new BtgCsvParser();
        assertThatThrownBy(() -> parser.parse("Data,Lancamento,Valor,Saldo\n"
                .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ParseException.class);
    }

    // ─── OFX ──────────────────────────────────────────────────────────────────

    private static final String OFX_SGML = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102

            <OFX>
            <BANKMSGSRSV1>
            <STMTTRNRS>
            <STMTRS>
            <BANKTRANLIST>
            <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20260601000000
            <TRNAMT>1500.00
            <FITID>FIT001
            <NAME>EMPRESA X
            </STMTTRN>
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20260605000000
            <TRNAMT>-500.00
            <FITID>FIT002
            <NAME>PAGAMENTO Y
            </STMTTRN>
            </BANKTRANLIST>
            </STMTRS>
            </STMTTRNRS>
            </BANKMSGSRSV1>
            </OFX>
            """;

    @Test
    void ofxParser_suporta_extensaoOfx() {
        var parser = new OfxParser();
        assertThat(parser.suporta(BancoEnum.OUTRO, "extrato.ofx")).isTrue();
        assertThat(parser.suporta(BancoEnum.OUTRO, "extrato.qfx")).isTrue();
        assertThat(parser.suporta(BancoEnum.OUTRO, "extrato.csv")).isFalse();
    }

    @Test
    void ofxParser_parseSgml_2lancamentos() throws ParseException {
        var parser = new OfxParser();
        List<LancamentoParseado> result = parser.parse(OFX_SGML.getBytes(StandardCharsets.UTF_8));

        assertThat(result).hasSize(2);

        LancamentoParseado credito = result.get(0);
        assertThat(credito.tipo()).isEqualTo(TipoLancamentoExtrato.CREDITO);
        assertThat(credito.valorCentavos()).isEqualTo(150_000L);
        assertThat(credito.identificadorExterno()).isEqualTo("FIT001");
        assertThat(credito.descricao()).isEqualTo("EMPRESA X");

        LancamentoParseado debito = result.get(1);
        assertThat(debito.tipo()).isEqualTo(TipoLancamentoExtrato.DEBITO);
        assertThat(debito.valorCentavos()).isEqualTo(50_000L);
        assertThat(debito.identificadorExterno()).isEqualTo("FIT002");
    }

    @Test
    void ofxParser_semTransacoes_lançaParseException() {
        var parser = new OfxParser();
        assertThatThrownBy(() -> parser.parse("<OFX><BANKMSGSRSV1></BANKMSGSRSV1></OFX>"
                .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("Nenhum lançamento");
    }
}
