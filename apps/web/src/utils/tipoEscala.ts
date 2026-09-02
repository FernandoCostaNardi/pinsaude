// Ponto único de verdade pro mapeamento Tipo de Escala → label pt-BR — usado em todo lugar que
// hoje tem (ou tinha) um ternário binário `tipo === 'DIARISTA' ? 'Diarista' : 'Plantonista'`.
// Evita que um valor novo caia silenciosamente rotulado como "Plantonista" sem erro nenhum
// (achado real da investigação que motivou este arquivo — ver CLAUDE.md).
export type TipoEscala = 'PLANTONISTA' | 'DIARISTA' | 'EVOLUCIONISTA' | 'EVOLUCIONISTA_FDS'

export const TIPO_ESCALA_LABEL: Record<TipoEscala, string> = {
  PLANTONISTA: 'Plantonista',
  DIARISTA: 'Diarista',
  EVOLUCIONISTA: 'Evolucionista',
  EVOLUCIONISTA_FDS: 'Evolucionista FDS',
}

export function labelTipoEscala(tipo: string | null | undefined): string {
  if (!tipo) return ''
  return TIPO_ESCALA_LABEL[tipo as TipoEscala] ?? tipo
}

// Duas famílias de comportamento, não 4 independentes: "fixa" (modalidade escolhida uma única
// vez na criação da frequência, nunca por lançamento, nunca mistura tipos dentro da mesma
// frequência) vs "por lançamento" (cada plantão escolhe sua própria modalidade). EVOLUCIONISTA
// se comporta como DIARISTA (fixa); EVOLUCIONISTA_FDS se comporta como PLANTONISTA (por
// lançamento) — apesar do nome parecido com EVOLUCIONISTA, o comportamento é o oposto.
export const TIPOS_MODALIDADE_FIXA: TipoEscala[] = ['DIARISTA', 'EVOLUCIONISTA']

export function isTipoModalidadeFixa(tipo: string | null | undefined): boolean {
  return !!tipo && (TIPOS_MODALIDADE_FIXA as string[]).includes(tipo)
}

// Todos os 4 tipos, na ordem de exibição usada em botões de seleção (cadastro de Modalidade).
export const TODOS_TIPOS_ESCALA: TipoEscala[] = ['PLANTONISTA', 'DIARISTA', 'EVOLUCIONISTA', 'EVOLUCIONISTA_FDS']
