// CBS-20 — tipos e listas de tributos num só lugar.
//
// Regra: a lista de tributos de uma tela vem do regime, nunca de um array fixo.
//  - Prévia/emissão (nota ainda não existe): useRegimeFiscal(competencia) → tributosVisiveis.
//  - Nota já emitida: tributosDaNota(nota), que lê o snapshot gravado na nota (CBS-11).
//    Notas antigas não têm regime gravado e continuam mostrando PIS/COFINS.

/** Todos os tributos que podem aparecer numa nota, no regime antigo ou no novo. */
export type TipoTributo = 'ISS' | 'IR' | 'CSLL' | 'PIS' | 'COFINS' | 'CBS' | 'IBS'

export type RegimeTributario = 'LEGADO' | 'IBS_CBS'

/** Regime antigo (até a virada da empresa): ISS, IR, CSLL, PIS e COFINS. */
export const TRIBUTOS_LEGADO: readonly TipoTributo[] = ['ISS', 'IR', 'CSLL', 'PIS', 'COFINS']

/** Regime novo: PIS/COFINS saem, CBS/IBS entram; ISS segue até o fator do ano chegar a zero (2033). */
export const TRIBUTOS_IBS_CBS: readonly TipoTributo[] = ['ISS', 'IR', 'CSLL', 'CBS', 'IBS']

/**
 * Tributos com alíquota cadastrada por empresa/competência ou por tomador (parametros_fiscais V2,
 * aliquotas do tomador). CBS/IBS não entram: vêm do cronograma da reforma e do serviço LC116.
 */
export const TRIBUTOS_COM_ALIQUOTA_CADASTRADA = ['ISS', 'IR', 'CSLL', 'PIS', 'COFINS'] as const
export type TributoComAliquotaCadastrada = typeof TRIBUTOS_COM_ALIQUOTA_CADASTRADA[number]

export const ROTULO_TRIBUTO: Record<TipoTributo, string> = {
  ISS: 'ISS',
  IR: 'IR',
  CSLL: 'CSLL',
  PIS: 'PIS',
  COFINS: 'COFINS',
  CBS: 'CBS',
  IBS: 'IBS',
}

const TODOS: readonly TipoTributo[] = ['ISS', 'IR', 'CSLL', 'PIS', 'COFINS', 'CBS', 'IBS']

export function isTipoTributo(v: unknown): v is TipoTributo {
  return typeof v === 'string' && (TODOS as readonly string[]).includes(v)
}

/**
 * Lista do regime quando não há resposta do backend (ex.: nota emitida).
 * {@code fatorIss} 0 = ISS extinto (2033): sai da lista no regime novo.
 */
export function tributosDoRegime(regime: RegimeTributario | null | undefined,
                                 fatorIss?: number | null): TipoTributo[] {
  if (regime !== 'IBS_CBS') return [...TRIBUTOS_LEGADO]
  const semIss = fatorIss != null && Number(fatorIss) <= 0
  return TRIBUTOS_IBS_CBS.filter(t => !(semIss && t === 'ISS'))
}

/** Tributos de uma nota já emitida, pelo regime gravado nela. Sem regime gravado = LEGADO. */
export function tributosDaNota(nota: { regimeTributario?: RegimeTributario | null; fatorIss?: number | null }):
  TipoTributo[] {
  return tributosDoRegime(nota.regimeTributario ?? 'LEGADO', nota.fatorIss)
}

/** Valores por tributo (centavos). Campo ausente vira 0. */
export type ValoresTributo = Partial<Record<TipoTributo, number>>

/** Monta o mapa de valores a partir de um objeto com valorIss, valorIr… (nota, prévia ou cálculo do motor). */
export function valoresTributo(src: {
  valorIss?: number | null
  valorIr?: number | null
  valorCsll?: number | null
  valorPis?: number | null
  valorCofins?: number | null
  valorCbs?: number | null
  valorIbs?: number | null
}): ValoresTributo {
  return {
    ISS: src.valorIss ?? 0,
    IR: src.valorIr ?? 0,
    CSLL: src.valorCsll ?? 0,
    PIS: src.valorPis ?? 0,
    COFINS: src.valorCofins ?? 0,
    CBS: src.valorCbs ?? 0,
    IBS: src.valorIbs ?? 0,
  }
}

/** Soma só os tributos visíveis (evita somar PIS/COFINS zerados de uma nota nova, ou CBS/IBS de uma antiga). */
export function totalTributos(tributos: readonly TipoTributo[], valores: ValoresTributo): number {
  return tributos.reduce((s, t) => s + (valores[t] ?? 0), 0)
}
