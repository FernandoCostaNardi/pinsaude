package br.com.pinsaude.faturamento.port;

import java.util.UUID;

/**
 * Resolve o id do médico (onboarding.medicos.id) do usuário logado com role medico. O JWT só traz
 * o e-mail; quem faz esse mapeamento é o serviço Portal (GET /api/portal/perfil).
 */
public interface MedicoLogadoPort {

    /** Id do médico logado. Lança {@link NotaFiscalConsultaPort.ConsultaIndisponivelException} se o Portal não respondeu. */
    UUID medicoIdAtual();
}
