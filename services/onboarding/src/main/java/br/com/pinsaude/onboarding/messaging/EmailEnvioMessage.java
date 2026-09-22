package br.com.pinsaude.onboarding.messaging;

import java.util.List;
import java.util.Map;

/**
 * Mensagem publicada na fila email.envio.
 * destinatario: e-mail direto, ou null quando medicoId está presente.
 * medicoId: UUID do médico como string — consumer resolve o e-mail via MedicoRepository.
 * copias: destinatários em cópia (CC). Pode vir null — mensagens publicadas por outros
 * serviços (fiscal) usam uma cópia própria do record, sem esse campo.
 */
public record EmailEnvioMessage(
    String tipo,
    String destinatario,
    String medicoId,
    String assunto,
    Map<String, Object> dados,
    List<String> copias
) {}
