package br.com.pinsaude.onboarding.messaging;

import java.util.Map;

/**
 * Mensagem publicada na fila email.envio.
 * destinatario: e-mail direto, ou null quando medicoId está presente.
 * medicoId: UUID do médico como string — consumer resolve o e-mail via MedicoRepository.
 */
public record EmailEnvioMessage(
    String tipo,
    String destinatario,
    String medicoId,
    String assunto,
    Map<String, Object> dados
) {}
