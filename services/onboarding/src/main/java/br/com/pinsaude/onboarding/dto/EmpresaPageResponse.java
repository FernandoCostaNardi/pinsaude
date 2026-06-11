package br.com.pinsaude.onboarding.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record EmpresaPageResponse(
    List<EmpresaResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static EmpresaPageResponse from(Page<EmpresaResponse> page) {
        return new EmpresaPageResponse(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
