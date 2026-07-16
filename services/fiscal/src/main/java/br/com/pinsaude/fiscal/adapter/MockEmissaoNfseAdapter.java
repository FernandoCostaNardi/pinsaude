package br.com.pinsaude.fiscal.adapter;

import br.com.pinsaude.fiscal.port.DadosNota;
import br.com.pinsaude.fiscal.port.EmissaoNfsePort;
import br.com.pinsaude.fiscal.port.ResultadoEmissao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Primary
@ConditionalOnProperty(name = "nfse.mock.enabled", havingValue = "true")
public class MockEmissaoNfseAdapter implements EmissaoNfsePort {

    private static final Logger log = LoggerFactory.getLogger(MockEmissaoNfseAdapter.class);

    @Override
    public ResultadoEmissao emitir(DadosNota dados) {
        String numero = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String protocolo = "PROTO-" + System.currentTimeMillis();
        String xml = "<NfseInfNfse><Numero>" + numero + "</Numero>"
                + "<Competencia>" + dados.competencia() + "</Competencia>"
                + "<ValorServicos>" + dados.valorBruto() + "</ValorServicos></NfseInfNfse>";

        log.info("[MOCK NFS-e] Emissão simulada — numero={} producaoId={}", numero, dados.producaoId());
        return ResultadoEmissao.sucesso(numero, protocolo, xml, null);
    }
}
