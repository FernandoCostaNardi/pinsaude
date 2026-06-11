package br.com.pinsaude.gestao.dto;

public record ConviteRequest(
    String email,
    String nome,
    String perfil
) {}
