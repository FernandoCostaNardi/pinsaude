import { useEffect, useMemo, useState } from 'react'
import { calcularFiscal } from '../api/fiscalApi'
import {
  interpretarCalculo, montarCalculoRequest, type EntradaCalculoNota, type NotaCalculada,
} from '../fiscal/calculoNota'

// CBS-21 — tributos de uma nota ainda não emitida, calculados pelo motor fiscal.
// Para nota já emitida, use o snapshot gravado nela (tributosDaNota), nunca este hook.

export interface UseCalculoNota {
  nota: NotaCalculada | null
  carregando: boolean
  erro: string | null
}

/**
 * @param entrada produção + tomador + serviço; null enquanto os dados não carregaram (não chama o motor)
 * @param cnpj    empresa emissora, só usada por quem não tem cnpj_id no token (gestão)
 */
export function useCalculoNota(entrada: EntradaCalculoNota | null, cnpj?: string | null): UseCalculoNota {
  const req = useMemo(() => (entrada ? montarCalculoRequest(entrada) : null), [entrada])
  const cnpjDigitos = (cnpj ?? '').replace(/\D/g, '')
  const chave = req ? `${JSON.stringify(req)}|${cnpjDigitos}` : null
  const [estado, setEstado] = useState<{ chave: string; nota: NotaCalculada | null; erro: string | null } | null>(null)

  useEffect(() => {
    if (!req || !chave) return
    let ativo = true
    calcularFiscal(req, cnpjDigitos || null)
      .then(calc => { if (ativo) setEstado({ chave, nota: interpretarCalculo(calc, req), erro: null }) })
      .catch((e: unknown) => {
        if (ativo) setEstado({ chave, nota: null, erro: e instanceof Error ? e.message : String(e) })
      })
    return () => { ativo = false }
  }, [chave]) // chave já combina o pedido e a empresa

  const atual = estado && estado.chave === chave ? estado : null
  return {
    nota: atual?.nota ?? null,
    carregando: !!chave && !atual,
    erro: atual?.erro ?? null,
  }
}
