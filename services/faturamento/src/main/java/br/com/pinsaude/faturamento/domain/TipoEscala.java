package br.com.pinsaude.faturamento.domain;

import java.util.Map;
import java.util.Set;

/**
 * Constantes compartilhadas dos 4 Tipos de Escala (tipo de {@link TomadorModalidade} e
 * tipoMedico de {@code FrequenciaMedica}) — nunca modelados como enum Java (são {@code String}
 * simples, validados via {@code @Pattern} no request e {@code CHECK} no banco, ver migrations
 * V40__add_tipo_evolucionista.sql e V41__evolucionista_fds_como_plantonista.sql).
 *
 * Duas famílias de comportamento, não 4 independentes (pedido do cliente, V41):
 * - "fixa" (DIARISTA, EVOLUCIONISTA): modalidade escolhida uma única vez na criação da
 *   frequência, valor mensal fixo, exige horas semanais.
 * - "por lançamento" (PLANTONISTA, EVOLUCIONISTA_FDS): cada plantão escolhe sua própria
 *   modalidade (podendo variar dentro da mesma frequência), exige turno/horário/horas.
 * EVOLUCIONISTA_FDS reaproveita exatamente as regras do PLANTONISTA, não do DIARISTA — apesar
 * do nome parecido com EVOLUCIONISTA, o comportamento por trás dos panos é o oposto.
 */
public final class TipoEscala {

    public static final String PLANTONISTA = "PLANTONISTA";
    public static final String DIARISTA = "DIARISTA";
    public static final String EVOLUCIONISTA = "EVOLUCIONISTA";
    public static final String EVOLUCIONISTA_FDS = "EVOLUCIONISTA_FDS";

    /** Tipos cuja modalidade é fixada uma única vez na criação da frequência (nunca por lançamento). */
    public static final Set<String> TIPOS_MODALIDADE_FIXA = Set.of(DIARISTA, EVOLUCIONISTA);

    public static boolean isModalidadeFixa(String tipo) {
        return tipo != null && TIPOS_MODALIDADE_FIXA.contains(tipo);
    }

    // Label pt-BR pra mensagens de erro/exibição — nunca o valor bruto em caixa alta.
    private static final Map<String, String> LABELS = Map.of(
        PLANTONISTA, "Plantonista",
        DIARISTA, "Diarista",
        EVOLUCIONISTA, "Evolucionista",
        EVOLUCIONISTA_FDS, "Evolucionista FDS"
    );

    public static String label(String tipo) {
        return LABELS.getOrDefault(tipo, tipo);
    }

    private TipoEscala() {
    }
}
