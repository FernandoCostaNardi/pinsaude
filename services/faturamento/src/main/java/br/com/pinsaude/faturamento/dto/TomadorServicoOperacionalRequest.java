package br.com.pinsaude.faturamento.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Setor Operacional é um catálogo por tomador (sem grupo próprio) — para vincular a um Grupo de
// Faturamento, usar TomadorGrupoSetorRequest via POST /api/tomadores/{id}/grupos/{grupoId}/setores.
// categoria é texto livre (opcional) — ex: "Emergência", "UTI", "Ambulatório".
//
// vinculos: pedido do cliente — o setor define explicitamente qual(is) Modalidade(s) daquele
// setor (usadas pra derivar o Tipo de Escala da Frequência automaticamente quando só há 1, ou pra
// oferecer a escolha quando há mais de 1 — sem precisar mais perguntar isso do zero na tela de
// Nova Frequência). Cada par (modalidadeId, tipo) é um vínculo independente — uma modalidade que
// suporta 2 Tipos de Escala (ver TomadorModalidade.tipos) pode aparecer 2x aqui, uma vez por
// tipo, se o operador quiser oferecer os dois nesse setor. O campo "Tipo de Escala" do PDF é 100%
// calculado a partir do tipoMedico da frequência + nome do setor — não há mais texto
// customizável aqui.
public record TomadorServicoOperacionalRequest(
    @NotBlank @Size(max = 150) String nome,
    @Size(max = 100) String categoria,
    boolean ativo,
    @NotEmpty List<@Valid VinculoModalidade> vinculos
) {
    public record VinculoModalidade(
        @NotNull UUID modalidadeId,
        @NotBlank @Pattern(regexp = "PLANTONISTA|DIARISTA|EVOLUCIONISTA|EVOLUCIONISTA_FDS",
            message = "tipo deve ser PLANTONISTA, DIARISTA, EVOLUCIONISTA ou EVOLUCIONISTA_FDS") String tipo
    ) {}
}
