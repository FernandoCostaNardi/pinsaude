package br.com.pinsaude.faturamento.controller;

import java.util.List;

public record ApiError(String message, List<String> details) {

    public static ApiError of(String message) {
        return new ApiError(message, List.of());
    }

    public static ApiError of(String message, List<String> details) {
        return new ApiError(message, details);
    }
}
