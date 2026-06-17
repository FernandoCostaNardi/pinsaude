package br.com.pinsaude.onboarding.port;

import br.com.pinsaude.onboarding.domain.ContratoAssinatura;
import br.com.pinsaude.onboarding.domain.Medico;

public interface ContratoAssinaturaPort {
    ContratoAssinatura enviar(Medico medico, String emailMedico) throws Exception;
}
