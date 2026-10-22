package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorContato;

import java.util.UUID;

public record TomadorContatoResponse(
    UUID id,
    UUID tomadorId,
    String apelido,
    String nome,
    String email,
    String telefone,
    String observacao
) {
    public static TomadorContatoResponse from(TomadorContato c) {
        return new TomadorContatoResponse(
            c.getId(),
            c.getTomadorId(),
            c.getApelido(),
            c.getNome(),
            c.getEmail(),
            c.getTelefone(),
            c.getObservacao()
        );
    }
}
