package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Setor Operacional é um catálogo por tomador (sem grupo próprio) — para vincular a um Grupo de
// Faturamento, usar TomadorGrupoSetorRequest via POST /api/tomadores/{id}/grupos/{grupoId}/setores.
// categoria é texto livre (opcional) — ex: "Emergência", "UTI", "Ambulatório".
public record TomadorServicoOperacionalRequest(
    @NotBlank @Size(max = 150) String nome,
    @Size(max = 100) String categoria,
    boolean ativo
) {}
