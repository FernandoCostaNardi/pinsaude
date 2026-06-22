package br.com.pinsaude.fiscal.adapter;

record NfseRpsResponse(
    String numeroNota,
    String protocolo,
    String xmlNota,
    String pdfBase64
) {}
