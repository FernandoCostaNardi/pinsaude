import { useEffect, useState } from 'react'
import { getRegimeCompetencia, type RegimeCompetencia } from '../api/fiscalApi'
import { TRIBUTOS_LEGADO, type RegimeTributario, type TipoTributo } from '../fiscal/tributos'

// CBS-20 — regime da empresa numa competência (GET /api/fiscal/regime, CBS-15).
// Use para telas de prévia/emissão, onde a nota ainda não existe. Para nota já emitida, use
// tributosDaNota(nota): o regime gravado na nota vale mais que o parâmetro atual da empresa.

export interface UseRegimeFiscal {
  /** Resposta completa do backend; null enquanto carrega, sem competência ou em erro */
  dados: RegimeCompetencia | null
  regime: RegimeTributario
  /** Tributos a mostrar. Enquanto carrega ou em erro, a lista do regime antigo. */
  tributosVisiveis: TipoTributo[]
  carregando: boolean
  erro: string | null
}

// Cache por empresa+competência durante a sessão da página: várias telas e linhas pedem o mesmo regime.
const cache = new Map<string, Promise<RegimeCompetencia>>()

function chave(competencia: string, cnpj?: string | null) {
  return `${competencia}|${(cnpj ?? '').replace(/\D/g, '')}`
}

export function buscarRegimeFiscal(competencia: string, cnpj?: string | null): Promise<RegimeCompetencia> {
  const k = chave(competencia, cnpj)
  let p = cache.get(k)
  if (!p) {
    p = getRegimeCompetencia(competencia, cnpj)
    // Erro não fica no cache: a próxima tela tenta de novo.
    p.catch(() => cache.delete(k))
    cache.set(k, p)
  }
  return p
}

/** Limpa o cache (ex.: depois de ligar, desligar ou homologar a chave CBS/IBS). */
export function limparCacheRegimeFiscal() {
  cache.clear()
}

const COMPETENCIA = /^\d{4}-(0[1-9]|1[0-2])$/

/**
 * @param competencia AAAA-MM; vazio/null não consulta e devolve o regime antigo
 * @param cnpj        empresa, só para quem não tem cnpj_id no token (gestão)
 */
export function useRegimeFiscal(competencia: string | null | undefined, cnpj?: string | null): UseRegimeFiscal {
  const valida = !!competencia && COMPETENCIA.test(competencia)
  const [estado, setEstado] = useState<{ k: string; dados: RegimeCompetencia | null; erro: string | null } | null>(null)
  const k = valida ? chave(competencia!, cnpj) : null

  useEffect(() => {
    if (!k) return
    let ativo = true
    buscarRegimeFiscal(competencia!, cnpj)
      .then(d => { if (ativo) setEstado({ k, dados: d, erro: null }) })
      .catch((e: unknown) => {
        if (ativo) setEstado({ k, dados: null, erro: e instanceof Error ? e.message : String(e) })
      })
    return () => { ativo = false }
  }, [k]) // k já combina competencia e cnpj

  const atual = estado && estado.k === k ? estado : null
  const dados = atual?.dados ?? null
  return {
    dados,
    regime: dados?.regime ?? 'LEGADO',
    tributosVisiveis: dados?.tributosVisiveis ?? [...TRIBUTOS_LEGADO],
    carregando: !!k && !atual,
    erro: atual?.erro ?? null,
  }
}
