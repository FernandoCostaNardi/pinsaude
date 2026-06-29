package br.com.pinsaude.fiscal.messaging;

import java.util.Map;

public record EmailEnvioMessage(
    String tipo,
    String destinatario,
    String medicoId,
    String assunto,
    Map<String, Object> dados
) {}
