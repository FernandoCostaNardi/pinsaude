package br.com.pinsaude.gestao.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handle(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
            .body(new ApiError(ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handle(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ApiError("Acesso negado. Você não tem permissão para esta operação."));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ApiError> handle(HttpClientErrorException ex) {
        String mensagem = switch (ex.getStatusCode().value()) {
            case 401 -> "Falha na autenticação com o servidor de identidade. Verifique as credenciais do admin.";
            case 403 -> "Sem permissão para realizar esta operação no servidor de identidade.";
            case 404 -> "Recurso não encontrado no servidor de identidade.";
            case 409 -> "Já existe um usuário com este e-mail cadastrado.";
            default  -> "Erro ao comunicar com o servidor de identidade (código " + ex.getStatusCode().value() + ").";
        };
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(mensagem));
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ApiError> handle(HttpServerErrorException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ApiError("O servidor de identidade retornou um erro interno. Tente novamente em instantes."));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiError> handle(ResourceAccessException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiError("Não foi possível conectar ao servidor de identidade. Verifique se o Keycloak está em execução."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handle(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError("Ocorreu um erro inesperado. Tente novamente ou contate o suporte."));
    }
}
