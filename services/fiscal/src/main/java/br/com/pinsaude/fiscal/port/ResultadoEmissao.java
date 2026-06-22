package br.com.pinsaude.fiscal.port;

public record ResultadoEmissao(
    boolean sucesso,
    String numeroNota,
    String protocolo,
    String xmlNota,
    byte[] pdfNota,
    String mensagemErro,
    boolean aguardandoEmissaoManual
) {
    public static ResultadoEmissao sucesso(String numero, String protocolo, String xml, byte[] pdf) {
        return new ResultadoEmissao(true, numero, protocolo, xml, pdf, null, false);
    }

    public static ResultadoEmissao aguardandoManual(String motivo) {
        return new ResultadoEmissao(false, null, null, null, null, motivo, true);
    }

    public static ResultadoEmissao erro(String mensagem) {
        return new ResultadoEmissao(false, null, null, null, null, mensagem, false);
    }
}
