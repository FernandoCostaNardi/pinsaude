const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

function authHeaders(): HeadersInit {
  return {
    Authorization: `Bearer ${getAccessToken()}`,
    'Content-Type': 'application/json',
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let msg = `Erro ${res.status}`
    try {
      const body = await res.json()
      msg = body.message ?? body.mensagem ?? msg
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

// ─── Tipos ──────────────────────────────────────────────────────────────────

export type TipoOrigemLedger = 'NOTA' | 'CONCILIACAO' | 'REPASSE' | 'AJUSTE'
export type TipoConta = 'ATIVO' | 'PASSIVO' | 'RECEITA' | 'DESPESA' | 'INTERMEDIARIO'
export type StatusAjuste = 'PENDENTE' | 'APROVADO' | 'REJEITADO'

/** Valores (valor, saldoApos) já vêm em REAIS com 2 casas (a API converte de centavos). */
export interface ExtratoItem {
  lancamentoId: string
  dataLancamento: string   // YYYY-MM-DD
  competencia: string
  tipoOrigem: TipoOrigemLedger
  origemId: string | null
  descricao: string
  valor: number            // efeito líquido no saldo do médico (+ crédito, − débito)
  saldoApos: number        // saldo running
}

export interface ContaLedger {
  id: string
  codigo: string
  nome: string
  tipo: TipoConta
}

export interface Ajuste {
  id: string
  medicoId: string | null
  competencia: string
  contaDebitoCodigo: string
  contaCreditoCodigo: string
  valor: number
  motivo: string
  solicitanteId: string
  solicitantePerfil: string
  aprovadorId: string | null
  aprovadorPerfil: string | null
  status: StatusAjuste
  lancamentoId: string | null
  motivoRejeicao: string | null
  createdAt: string
  decidedAt: string | null
}

export interface CriarAjusteRequest {
  medicoId: string | null
  competencia: string
  contaDebitoCodigo: string
  contaCreditoCodigo: string
  valorCentavos: number
  motivo: string
}

// ─── Endpoints ────────────────────────────────────────────────────────────────

export const ledgerApi = {
  async getExtrato(medicoId: string): Promise<ExtratoItem[]> {
    const res = await fetch(`/api/ledger/extrato/${medicoId}`, { headers: authHeaders() })
    return handleResponse<ExtratoItem[]>(res)
  },

  async listarContas(): Promise<ContaLedger[]> {
    const res = await fetch('/api/ledger/contas', { headers: authHeaders() })
    return handleResponse<ContaLedger[]>(res)
  },

  async listarAjustes(status?: StatusAjuste): Promise<Ajuste[]> {
    const params = status ? `?status=${status}` : ''
    const res = await fetch(`/api/ledger/ajustes${params}`, { headers: authHeaders() })
    return handleResponse<Ajuste[]>(res)
  },

  async criarAjuste(req: CriarAjusteRequest): Promise<Ajuste> {
    const res = await fetch('/api/ledger/ajustes', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<Ajuste>(res)
  },

  async aprovarAjuste(id: string): Promise<Ajuste> {
    const res = await fetch(`/api/ledger/ajustes/${id}/aprovar`, {
      method: 'POST',
      headers: authHeaders(),
    })
    return handleResponse<Ajuste>(res)
  },

  async rejeitarAjuste(id: string, motivo: string): Promise<Ajuste> {
    const res = await fetch(`/api/ledger/ajustes/${id}/rejeitar?motivo=${encodeURIComponent(motivo)}`, {
      method: 'POST',
      headers: authHeaders(),
    })
    return handleResponse<Ajuste>(res)
  },
}
