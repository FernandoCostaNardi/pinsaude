package br.com.pinsaude.onboarding.controller;

import java.util.List;

public record ApiError(String mensagem, List<String> erros) {

    public static ApiError of(String mensagem) {
        return new ApiError(mensagem, List.of());
    }

    public static ApiError of(String mensagem, List<String> erros) {
        return new ApiError(mensagem, erros);
    }
}
