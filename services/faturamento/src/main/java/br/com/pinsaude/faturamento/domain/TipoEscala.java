package br.com.pinsaude.faturamento.domain;

import java.util.Map;
import java.util.Set;

/**
 * Constantes compartilhadas dos 5 Tipos de Escala (tipo de {@link TomadorModalidade} e
 * tipoMedico de {@code FrequenciaMedica}) — nunca modelados como enum Java (são {@code String}
 * simples, validados via {@code @Pattern} no request e {@code CHECK} no banco, ver migrations
 * V40__add_tipo_evolucionista.sql, V41__evolucionista_fds_como_plantonista.sql e
 * V44__add_tipo_servicos.sql).
 *
 * Três famílias de comportamento, não 5 independentes:
 * - "fixa" (DIARISTA, EVOLUCIONISTA): modalidade escolhida uma única vez na criação da
 *   frequência, valor mensal fixo, exige horas semanais.
 * - "por lançamento" (PLANTONISTA, EVOLUCIONISTA_FDS): cada plantão escolhe sua própria
 *   modalidade (podendo variar dentro da mesma frequência), exige turno/horário/horas.
 * - "por serviço" (SERVICOS): igual "por lançamento" na mecânica (modalidade escolhida a cada
 *   item, sem fixar na criação da frequência, sem checagem de duplicidade), mas paga
 *   quantidade × valorCentavos por lançamento em vez de valor flat — o item pede uma quantidade
 *   de serviços realizados, não turno/horário/horas. Cadastro da Modalidade só exige valor
 *   (preço unitário do serviço).
 * EVOLUCIONISTA_FDS reaproveita exatamente as regras do PLANTONISTA, não do DIARISTA — apesar
 * do nome parecido com EVOLUCIONISTA, o comportamento por trás dos panos é o oposto.
 */
public final class TipoEscala {

    public static final String PLANTONISTA = "PLANTONISTA";
    public static final String DIARISTA = "DIARISTA";
    public static final String EVOLUCIONISTA = "EVOLUCIONISTA";
    public static final String EVOLUCIONISTA_FDS = "EVOLUCIONISTA_FDS";
    public static final String SERVICOS = "SERVICOS";

    /** Tipos cuja modalidade é fixada uma única vez na criação da frequência (nunca por lançamento). */
    public static final Set<String> TIPOS_MODALIDADE_FIXA = Set.of(DIARISTA, EVOLUCIONISTA);

    /** Tipos pagos por quantidade de serviço realizado (nunca fixados na criação da frequência). */
    public static final Set<String> TIPOS_MODALIDADE_SERVICO = Set.of(SERVICOS);

    public static boolean isModalidadeFixa(String tipo) {
        return tipo != null && TIPOS_MODALIDADE_FIXA.contains(tipo);
    }

    public static boolean isModalidadeServico(String tipo) {
        return tipo != null && TIPOS_MODALIDADE_SERVICO.contains(tipo);
    }

    // Label pt-BR pra mensagens de erro/exibição — nunca o valor bruto em caixa alta.
    private static final Map<String, String> LABELS = Map.of(
        PLANTONISTA, "Plantonista",
        DIARISTA, "Diarista",
        EVOLUCIONISTA, "Evolucionista",
        EVOLUCIONISTA_FDS, "Evolucionista FDS",
        SERVICOS, "Serviços"
    );

    public static String label(String tipo) {
        return LABELS.getOrDefault(tipo, tipo);
    }

    private TipoEscala() {
    }
}
