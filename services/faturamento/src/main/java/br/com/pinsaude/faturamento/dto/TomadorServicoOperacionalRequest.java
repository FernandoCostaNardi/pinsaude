package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Setor Operacional é um catálogo por tomador (sem grupo próprio) — para vincular a um Grupo de
// Faturamento, usar TomadorGrupoSetorRequest via POST /api/tomadores/{id}/grupos/{grupoId}/setores.
// categoria é texto livre (opcional) — ex: "Emergência", "UTI", "Ambulatório".
//
// modalidadeId/tipoEscalaLabel: pedido do cliente — o setor passa a definir explicitamente qual a
// Modalidade daquele setor (usada pra derivar o Tipo de Escala da Frequência automaticamente,
// sem precisar mais perguntar isso na tela de Nova Frequência) e o texto exibido no campo
// "Tipo de Escala" do PDF (sugestão default "Modalidade - Setor", montada no frontend, editável).
public record TomadorServicoOperacionalRequest(
    @NotBlank @Size(max = 150) String nome,
    @Size(max = 100) String categoria,
    boolean ativo,
    @NotNull UUID modalidadeId,
    @NotBlank @Size(max = 150) String tipoEscalaLabel
) {}
