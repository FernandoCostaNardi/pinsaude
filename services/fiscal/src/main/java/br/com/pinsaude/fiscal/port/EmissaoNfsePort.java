package br.com.pinsaude.fiscal.port;

public interface EmissaoNfsePort {
    ResultadoEmissao emitir(DadosNota dados);
}
