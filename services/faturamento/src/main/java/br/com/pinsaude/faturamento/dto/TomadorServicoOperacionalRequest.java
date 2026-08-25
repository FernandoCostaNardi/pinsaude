package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Setor Operacional é um catálogo por tomador (sem grupo próprio) — para vincular a um Grupo de
// Faturamento, usar TomadorGrupoSetorRequest via POST /api/tomadores/{id}/grupos/{grupoId}/setores.
// categoria é texto livre (opcional) — ex: "Emergência", "UTI", "Ambulatório".
//
// modalidadeIds/tipoEscalaLabel: pedido do cliente — o setor define explicitamente qual(is)
// Modalidade(s) daquele setor (usadas pra derivar o Tipo de Escala da Frequência automaticamente
// quando só há 1, ou pra oferecer a escolha quando há mais de 1 — sem precisar mais perguntar
// isso do zero na tela de Nova Frequência) e o texto exibido no campo "Tipo de Escala" do PDF
// (sugestão default "Plantonista/Diarista - Setor", montada no frontend, editável).
public record TomadorServicoOperacionalRequest(
    @NotBlank @Size(max = 150) String nome,
    @Size(max = 100) String categoria,
    boolean ativo,
    @NotEmpty List<UUID> modalidadeIds,
    @NotBlank @Size(max = 150) String tipoEscalaLabel
) {}
