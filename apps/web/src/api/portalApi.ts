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
      msg = body.mensagem ?? body.message ?? msg
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  return res.json()
}

export interface NotaPortal {
  id: string
  producaoId: string
  competencia: string
  tomadorNome: string | null
  valorBrutoCentavos: number
  valorLiquidoMedicoCentavos: number
  taxaPinCentavos: number
  valorIss: number
  valorIr: number
  valorCsll: number
  valorPis: number
  valorCofins: number
  status: string
  numeroNota: string | null
  temXml: boolean
  temPdf: boolean
  protocolo: string | null
  emitidaAt: string | null
  createdAt: string
}

export interface DashboardPortal {
  saldoDisponivelCentavos: number
  valorAReceberCentavos: number
  totalProduzidoCentavos: number
  totalNotasEmitidas: number
  totalProducoes: number
  ultimasNotas: NotaPortal[]
  ultimosRepasses: unknown[]
}

export interface ProducaoPortal {
  id: string
  competencia: string
  tomadorNome: string
  servicoDescricao: string
  valorBrutoCentavos: number
  valorLiquidoEstimadoCentavos: number
  status: string
  createdAt: string
}

async function getDashboard(): Promise<DashboardPortal> {
  const res = await fetch('/api/portal/dashboard', { headers: authHeaders() })
  return handleResponse<DashboardPortal>(res)
}

async function getNotas(params?: { competencia?: string; status?: string }): Promise<NotaPortal[]> {
  const search = new URLSearchParams()
  if (params?.competencia) search.set('competencia', params.competencia)
  if (params?.status) search.set('status', params.status)
  const qs = search.toString() ? `?${search}` : ''
  const res = await fetch(`/api/portal/notas${qs}`, { headers: authHeaders() })
  return handleResponse<NotaPortal[]>(res)
}

async function getProducao(params?: { competencia?: string }): Promise<ProducaoPortal[]> {
  const search = new URLSearchParams()
  if (params?.competencia) search.set('competencia', params.competencia)
  const qs = search.toString() ? `?${search}` : ''
  const res = await fetch(`/api/portal/producao${qs}`, { headers: authHeaders() })
  return handleResponse<ProducaoPortal[]>(res)
}

async function downloadXml(notaId: string): Promise<void> {
  const res = await fetch(`/api/portal/notas/${notaId}/xml`, { headers: authHeaders() })
  if (!res.ok) throw new Error(`Erro ao baixar XML: ${res.status}`)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `nfse-${notaId}.xml`
  a.click()
  URL.revokeObjectURL(url)
}

async function downloadPdf(notaId: string): Promise<void> {
  const res = await fetch(`/api/portal/notas/${notaId}/pdf`, { headers: authHeaders() })
  if (!res.ok) throw new Error(`Erro ao baixar PDF: ${res.status}`)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `nfse-${notaId}.pdf`
  a.click()
  URL.revokeObjectURL(url)
}

export const portalApi = { getDashboard, getNotas, getProducao, downloadXml, downloadPdf }
