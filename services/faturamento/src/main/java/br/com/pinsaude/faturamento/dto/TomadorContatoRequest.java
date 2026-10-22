package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Regra "e-mail OU telefone" é validada no TomadorContatoService (e reforçada por CHECK em V51).
public record TomadorContatoRequest(
    @NotBlank(message = "apelido é obrigatório") @Size(max = 80) String apelido,
    @Size(max = 150) String nome,
    @Email(message = "e-mail inválido") @Size(max = 255) String email,
    @Size(max = 30) String telefone,
    @Size(max = 2000) String observacao
) {}
