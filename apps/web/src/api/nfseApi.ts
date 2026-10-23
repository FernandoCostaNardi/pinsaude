const STORAGE_KEY = 'pinsaude_tokens'

function getToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

function authHeaders() {
  return { Authorization: `Bearer ${getToken()}`, 'Content-Type': 'application/json' }
}

export type StatusNota =
  | 'PENDENTE'
  | 'PROCESSANDO'
  | 'EMITIDA'
  | 'CANCELADA'
  | 'ERRO'
  | 'REJEITADA'
  | 'AGUARDANDO_EMISSAO_MANUAL'
  | 'AGUARDANDO_VALIDACAO'
  // Pseudo-status só de frontend (nunca vem do backend) — produção fechada/confirmada que ainda
  // não tem nenhuma NotaFiscal criada. Ver NotasPage.tsx: "quem manda em Notas é o menu de Notas".
  | 'AGUARDANDO_EMISSAO'

export interface NotaFiscal {
  notaId: string
  producaoId: string
  medicoId: string
  tomadorId: string
  tomadorNome: string | null
  competencia: string
  status: StatusNota
  numeroNota: string | null
  protocolo: string | null
  observacoes: string | null
  valorBruto: number | null
  taxaPin: number | null
  valorLiquidoMedico: number | null
  emitidaAt: string | null
  createdAt: string
  discriminacao?: string | null
  // Nota emitida fora do sistema (portal da prefeitura) e registrada com PDF/XML anexados
  emissaoExterna?: boolean
  temPdf?: boolean
  temXml?: boolean
}

export interface EmitirNfseRequest {
  producaoId: string
  medicoId: string | null   // null para produções multi-médico
  tomadorId: string
  competencia: string
  valorBruto: number
  taxaPin: number
  valorIss: number
  valorIr: number
  valorCsll: number
  valorPis: number
  valorCofins: number
  tomadorNome: string | null
  cnaeCodigo?: string | null
  discriminacao?: string | null
  cnpjEmpresaEmissora?: string | null  // tenant da nota quando o usuário não tem cnpj_id no token
}

export interface EmitirNfseResponse {
  notaId: string
  producaoId: string
  status: StatusNota
  numeroNota: string | null
  competencia: string
  primeiraNotaMedico: boolean
}

export async function emitirNfse(req: EmitirNfseRequest): Promise<EmitirNfseResponse> {
  const r = await fetch('/api/nfse/emitir', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(req),
  })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    throw new Error(txt || `Erro ${r.status}`)
  }
  return r.json()
}

async function enviarMultipart(url: string, method: 'POST' | 'PUT', form: FormData): Promise<NotaFiscal> {
  // Sem Content-Type: o navegador monta o boundary do multipart
  const r = await fetch(url, { method, headers: { Authorization: `Bearer ${getToken()}` }, body: form })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    let msg = txt
    try { msg = JSON.parse(txt).message || txt } catch { /* resposta não-JSON */ }
    throw new Error(msg || `Erro ${r.status}`)
  }
  return r.json()
}

export interface ArquivosNota {
  pdf: File | null
  xml: File | null
}

/**
 * Registra a NFS-e emitida no portal da prefeitura: mesmos dados da emissão pelo sistema, mais o
 * número e a data da nota e o PDF e/ou XML recebidos (ao menos um). A nota já nasce EMITIDA.
 */
export async function registrarEmissaoExterna(
  nota: EmitirNfseRequest, numeroNota: string, dataEmissao: string, arquivos: ArquivosNota,
): Promise<NotaFiscal> {
  const form = new FormData()
  form.append('dados', new Blob([JSON.stringify({ nota, numeroNota, dataEmissao })], { type: 'application/json' }))
  if (arquivos.pdf) form.append('pdf', arquivos.pdf)
  if (arquivos.xml) form.append('xml', arquivos.xml)
  return enviarMultipart('/api/nfse/emissao-externa', 'POST', form)
}

/** Anexa ou substitui o PDF e/ou XML de uma nota emitida; o que não for enviado é mantido. */
export async function anexarArquivosNota(notaId: string, arquivos: ArquivosNota): Promise<NotaFiscal> {
  const form = new FormData()
  if (arquivos.pdf) form.append('pdf', arquivos.pdf)
  if (arquivos.xml) form.append('xml', arquivos.xml)
  return enviarMultipart(`/api/nfse/${notaId}/arquivos`, 'PUT', form)
}

export async function getNotaStatus(producaoId: string): Promise<NotaFiscal> {
  const r = await fetch(`/api/nfse/status/${producaoId}`, { headers: authHeaders() })
  if (!r.ok) throw new Error(`Erro ao buscar status: ${r.status}`)
  return r.json()
}

export async function listarNotas(): Promise<NotaFiscal[]> {
  const r = await fetch('/api/nfse', { headers: authHeaders() })
  if (!r.ok) throw new Error(`Erro ao listar notas: ${r.status}`)
  return r.json()
}

export async function aprovarNota(notaId: string): Promise<void> {
  const r = await fetch(`/api/nfse/${notaId}/aprovar`, {
    method: 'POST',
    headers: authHeaders(),
  })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    throw new Error(txt || `Erro ${r.status}`)
  }
}

export async function cancelarNota(notaId: string, motivo: string): Promise<void> {
  const r = await fetch(`/api/nfse/${notaId}/cancelar`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ motivo }),
  })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    throw new Error(txt || `Erro ${r.status}`)
  }
}

export async function rejeitarNota(notaId: string, motivo: string): Promise<void> {
  const r = await fetch(`/api/nfse/${notaId}/rejeitar`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ motivo }),
  })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    throw new Error(txt || `Erro ${r.status}`)
  }
}

export async function listarExcecoes(): Promise<NotaFiscal[]> {
  const r = await fetch('/api/nfse/excecoes', { headers: authHeaders() })
  if (!r.ok) throw new Error(`Erro ao listar exceções: ${r.status}`)
  return r.json()
}

export function downloadXmlUrl(notaId: string): string {
  return `/api/nfse/${notaId}/download/xml`
}

export function downloadPdfUrl(notaId: string): string {
  return `/api/nfse/${notaId}/download/pdf`
}

export async function reprocessarNota(notaId: string): Promise<void> {
  const r = await fetch(`/api/nfse/${notaId}/reprocessar`, {
    method: 'POST',
    headers: authHeaders(),
  })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    throw new Error(txt || `Erro ${r.status}`)
  }
}

// ─── Lote (EPIC-05.7 / 05.8) ──────────────────────────────────────────────────

export type StatusLote = 'EM_ANDAMENTO' | 'CONCLUIDO' | 'FALHA'

export interface LoteProgressoResponse {
  loteId: string
  competencia: string
  total: number
  emitidas: number
  falhas: number
  emProcessamento: number
  status: StatusLote
  percentualConcluido: number
  iniciadoEm: string
  concluidoEm: string | null
}

export async function emitirLote(competencia: string): Promise<LoteProgressoResponse> {
  const r = await fetch('/api/nfse/lote/emitir', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ competencia }),
  })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    throw new Error(txt || `Erro ${r.status}`)
  }
  return r.json()
}

export async function getProgressoLote(loteId: string): Promise<LoteProgressoResponse> {
  const r = await fetch(`/api/nfse/lote/${loteId}/progresso`, { headers: authHeaders() })
  if (!r.ok) throw new Error(`Erro ao buscar progresso: ${r.status}`)
  return r.json()
}

export async function listarLotes(): Promise<LoteProgressoResponse[]> {
  const r = await fetch('/api/nfse/lote', { headers: authHeaders() })
  if (!r.ok) throw new Error(`Erro ao listar lotes: ${r.status}`)
  return r.json()
}

export async function listarErrosLote(loteId: string): Promise<NotaFiscal[]> {
  const r = await fetch(`/api/nfse/lote/${loteId}/erros`, { headers: authHeaders() })
  if (!r.ok) throw new Error(`Erro ao buscar erros do lote: ${r.status}`)
  return r.json()
}

export async function downloadComAuth(url: string, filename: string): Promise<void> {
  const r = await fetch(url, { headers: { Authorization: `Bearer ${getToken()}` } })
  if (!r.ok) throw new Error(`Erro ao baixar arquivo: ${r.status}`)
  const blob = await r.blob()
  const objUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = objUrl
  a.download = filename
  a.click()
  URL.revokeObjectURL(objUrl)
}
