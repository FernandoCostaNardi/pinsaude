const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

export type StatusMedico = 'RASCUNHO' | 'ATIVO' | 'INATIVO' | 'SUSPENSO'
export type StatusJuntaComercial = 'AGUARDANDO' | 'EM_ANALISE' | 'APROVADO' | 'RECUSADO'
export type StatusContrato = 'AGUARDANDO_ENVIO' | 'ENVIADO' | 'VISUALIZADO' | 'ASSINADO' | 'RECUSADO'
export type TipoPix = 'CPF' | 'CNPJ' | 'EMAIL' | 'TELEFONE' | 'ALEATORIA'
export type TipoRecebimento = 'PIX' | 'TED'
export type TipoConta = 'CORRENTE' | 'POUPANCA'
export type TipoDocumentoMedico = 'CRM' | 'DIPLOMA' | 'IDENTIDADE' | 'RESIDENCIA' | 'CONTRATO' | 'ESPECIALIDADES'
export type StatusValidacaoDocumento = 'PENDENTE' | 'APROVADO' | 'REPROVADO'

export interface DadosBancariosMedico {
  tipoRecebimento?: TipoRecebimento
  // PIX
  tipoPix?: TipoPix
  chavePix?: string
  cpfsAdicionaisSplit?: string
  // TED
  bancoCodigo?: string
  bancoNome?: string
  agencia?: string
  conta?: string
  tipoConta?: TipoConta
}

export interface DocumentoMedico {
  id: string
  tipo: TipoDocumentoMedico
  nomeArquivo: string
  caminhoStorage: string
  statusValidacao: StatusValidacaoDocumento
  motivoReprovacao?: string
  createdAt: string
}

export interface ChecklistConduta {
  numeroConselhoVerificado: boolean
  registrosDisciplinares: boolean
  processosMedicos: boolean
  completo: boolean
  verificadoPor?: string
  verificadoEm?: string
}

export interface VinculoEmpresa {
  empresaId: string
  cnpj: string
  razaoSocial: string
  statusSocietario?: string
  dataAtivacao?: string
  createdAt: string
}

export interface Medico {
  id: string
  cpf: string
  nome: string
  crm: string
  crmUf: string
  especialidade?: string
  email?: string
  telefone?: string
  taxaPinPct: number
  status: StatusMedico
  statusJuntaComercial: StatusJuntaComercial
  empresaId?: string
  empresas?: VinculoEmpresa[]
  dadosBancarios?: DadosBancariosMedico
  documentos?: DocumentoMedico[]
  checklist?: ChecklistConduta
  contratoAssinatura?: ContratoAssinatura
  ultimoConvite?: ConviteMedico
  createdAt: string
  updatedAt: string
}

export interface HistoricoTaxaPin {
  id: string
  taxaAnterior: number
  taxaNova: number
  alteradoPor?: string
  alteradoEm: string
}

export interface MedicoRequest {
  cpf: string
  nome: string
  crm: string
  crmUf: string
  especialidade: string
  email: string
  telefone: string
  empresaId?: string
  taxaPinPct?: number
}

export interface DadosBancariosMedicoRequest {
  tipoRecebimento: TipoRecebimento
  // PIX
  tipoPix: TipoPix | null
  chavePix: string | null
  cpfsAdicionaisSplit: string | null
  // TED
  bancoCodigo: string | null
  bancoNome: string | null
  agencia: string | null
  conta: string | null
  tipoConta: TipoConta | null
  confirmarAlteracao: true
}

export interface ConviteMedico {
  id: string
  emailDestino?: string
  status: string
  enviadoEm?: string
  expiraEm?: string
}

export interface ContratoAssinatura {
  id: string
  status: StatusContrato
  linkAssinatura?: string
  enviadoEm?: string
  assinadoEm?: string
}

export interface HistoricoMedico {
  id: string
  tipoAcao: string
  descricao: string
  usuario?: string
  createdAt: string
}

export interface MedicoPage {
  content: Medico[]
  page: number
  size: number
  totalElements: number
  totalPages: number
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
  if (res.status === 204) return undefined as T
  return res.json()
}

async function listar(page = 0, size = 1000, status?: string): Promise<MedicoPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) params.set('status', status)
  const res = await fetch(`/api/medicos?${params}`, { headers: authHeaders() })
  return handleResponse<MedicoPage>(res)
}

async function buscarPorId(id: string): Promise<Medico> {
  const res = await fetch(`/api/medicos/${id}`, { headers: authHeaders() })
  return handleResponse<Medico>(res)
}

async function criar(data: MedicoRequest): Promise<Medico> {
  const res = await fetch('/api/medicos', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<Medico>(res)
}

async function atualizar(id: string, data: MedicoRequest): Promise<Medico> {
  const res = await fetch(`/api/medicos/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<Medico>(res)
}

async function ativar(id: string): Promise<Medico> {
  const res = await fetch(`/api/medicos/${id}/ativar`, {
    method: 'PUT',
    headers: authHeaders(),
  })
  return handleResponse<Medico>(res)
}

async function inativar(id: string): Promise<Medico> {
  const res = await fetch(`/api/medicos/${id}/inativar`, {
    method: 'PUT',
    headers: authHeaders(),
  })
  return handleResponse<Medico>(res)
}

async function atualizarDadosBancarios(id: string, data: DadosBancariosMedicoRequest): Promise<DadosBancariosMedico> {
  const res = await fetch(`/api/medicos/${id}/dados-bancarios`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<DadosBancariosMedico>(res)
}

async function listarDocumentos(medicoId: string): Promise<DocumentoMedico[]> {
  const res = await fetch(`/api/medicos/${medicoId}/documentos`, { headers: authHeaders() })
  return handleResponse<DocumentoMedico[]>(res)
}

async function uploadDocumento(medicoId: string, tipo: TipoDocumentoMedico, arquivo: File): Promise<DocumentoMedico> {
  const token = JSON.parse(sessionStorage.getItem('pinsaude_tokens') ?? '{}').accessToken ?? ''
  const formData = new FormData()
  formData.append('arquivo', arquivo)
  const res = await fetch(`/api/medicos/${medicoId}/documentos?tipo=${tipo}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,
  })
  return handleResponse<DocumentoMedico>(res)
}

async function validarDocumento(
  medicoId: string,
  docId: string,
  data: { statusValidacao: StatusValidacaoDocumento; motivoReprovacao?: string }
): Promise<DocumentoMedico> {
  const res = await fetch(`/api/medicos/${medicoId}/documentos/${docId}/validar`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<DocumentoMedico>(res)
}

async function getDocumentoUrl(medicoId: string, docId: string): Promise<string> {
  const res = await fetch(`/api/medicos/${medicoId}/documentos/${docId}/url`, { headers: authHeaders() })
  const data = await handleResponse<{ url: string }>(res)
  return data.url
}

async function listarHistorico(medicoId: string): Promise<HistoricoMedico[]> {
  const res = await fetch(`/api/medicos/${medicoId}/historico`, { headers: authHeaders() })
  return handleResponse<HistoricoMedico[]>(res)
}

async function listarHistoricoTaxaPin(medicoId: string): Promise<HistoricoTaxaPin[]> {
  const res = await fetch(`/api/medicos/${medicoId}/taxa-pin/historico`, { headers: authHeaders() })
  return handleResponse<HistoricoTaxaPin[]>(res)
}

async function downloadDocumento(medicoId: string, docId: string): Promise<Blob> {
  const res = await fetch(`/api/medicos/${medicoId}/documentos/${docId}/download`, {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  })
  if (!res.ok) {
    let msg = `Erro ${res.status}`
    try { const b = await res.json(); msg = b.mensagem ?? b.message ?? msg } catch { /* ignore */ }
    throw new Error(msg)
  }
  return res.blob()
}

async function deletarDocumento(medicoId: string, docId: string): Promise<void> {
  const res = await fetch(`/api/medicos/${medicoId}/documentos/${docId}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse<void>(res)
}

async function enviarConvite(medicoId: string): Promise<ConviteMedico> {
  const res = await fetch(`/api/medicos/${medicoId}/convite`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse<ConviteMedico>(res)
}

async function enviarContrato(medicoId: string): Promise<ContratoAssinatura> {
  const res = await fetch(`/api/medicos/${medicoId}/contrato/enviar`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse<ContratoAssinatura>(res)
}

async function atualizarJuntaComercial(
  medicoId: string,
  status: StatusJuntaComercial,
  observacao?: string
): Promise<Medico> {
  const res = await fetch(`/api/medicos/${medicoId}/junta-comercial`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ status, observacao }),
  })
  return handleResponse<Medico>(res)
}

async function assinarContratoManual(medicoId: string): Promise<ContratoAssinatura> {
  const res = await fetch(`/api/medicos/${medicoId}/contrato/assinar`, {
    method: 'PUT',
    headers: authHeaders(),
  })
  return handleResponse<ContratoAssinatura>(res)
}

async function listarFilaAprovacao(): Promise<Medico[]> {
  const res = await fetch('/api/medicos/fila-aprovacao', { headers: authHeaders() })
  return handleResponse<Medico[]>(res)
}

export interface ChecklistCondutaRequest {
  numeroConselhoVerificado: boolean
  registrosDisciplinares: boolean
  processosMedicos: boolean
  verificadoPor?: string
}

async function atualizarChecklist(medicoId: string, data: ChecklistCondutaRequest): Promise<ChecklistConduta> {
  const res = await fetch(`/api/medicos/${medicoId}/checklist`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<ChecklistConduta>(res)
}

async function listarVinculos(medicoId: string): Promise<VinculoEmpresa[]> {
  const res = await fetch(`/api/medicos/${medicoId}/vinculos`, { headers: authHeaders() })
  return handleResponse<VinculoEmpresa[]>(res)
}

async function adicionarVinculo(medicoId: string, empresaId: string): Promise<VinculoEmpresa> {
  const res = await fetch(`/api/medicos/${medicoId}/vinculos`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ empresaId }),
  })
  return handleResponse<VinculoEmpresa>(res)
}

async function removerVinculo(medicoId: string, empresaId: string): Promise<void> {
  const res = await fetch(`/api/medicos/${medicoId}/vinculos/${empresaId}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse<void>(res)
}

export const medicosApi = {
  listar, buscarPorId, criar, atualizar, ativar, inativar,
  atualizarDadosBancarios, listarDocumentos, uploadDocumento,
  validarDocumento, getDocumentoUrl, deletarDocumento, listarHistorico,
  listarHistoricoTaxaPin, downloadDocumento,
  enviarConvite, enviarContrato, atualizarJuntaComercial,
  assinarContratoManual, listarFilaAprovacao, atualizarChecklist,
  listarVinculos, adicionarVinculo, removerVinculo,
}
