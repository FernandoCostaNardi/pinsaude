package br.com.pinsaude.gestao.dto;

public record UsuarioDto(
    String id,
    String email,
    String nome,
    String perfil,
    boolean ativo,
    boolean pendente
) {}
