package br.com.pinsaude.gestao.dto;

import java.util.List;

public record PerfilCustomizadoRequest(
    String nome,
    List<String> permissoes
) {}
