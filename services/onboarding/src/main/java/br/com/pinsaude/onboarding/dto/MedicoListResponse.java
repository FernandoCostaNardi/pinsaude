package br.com.pinsaude.onboarding.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record MedicoListResponse(
    List<MedicoSummaryResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static MedicoListResponse from(Page<MedicoSummaryResponse> page) {
        return new MedicoListResponse(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
