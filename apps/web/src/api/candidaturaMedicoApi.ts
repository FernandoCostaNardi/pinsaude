// Módulo de API do auto-cadastro público de médico (EPIC-14.6).
// Diferente de todos os demais módulos de API do projeto: os endpoints em
// /api/onboarding/publico/** são liberados via permitAll (SecurityConfig do onboarding
// e do gateway) — não há usuário autenticado nesta jornada, logo nunca incluir
// Authorization/getAccessToken() aqui.

export type EstadoCivil =
  | 'SOLTEIRO'
  | 'CASADO_COMUNHAO_PARCIAL'
  | 'CASADO_SEPARACAO_TOTAL'
  | 'CASADO_COMUNHAO_UNIVERSAL'
  | 'UNIAO_ESTAVEL'
  | 'DIVORCIADO'
  | 'VIUVO'
  | 'PARTICIPACAO_FINAL_AQUESTOS'
  | 'OUTRO'

export type TipoDocumentoCandidatura =
  | 'CRM' | 'DIPLOMA' | 'IDENTIDADE' | 'RESIDENCIA' | 'CONTRATO' | 'ESPECIALIDADES'
  | 'CERTIDAO_CASAMENTO' | 'COMPROVANTE_ENDERECO' | 'RQE'

export type StatusValidacaoDocumento = 'PENDENTE' | 'APROVADO' | 'REPROVADO'

export type TipoPix = 'CPF' | 'CNPJ' | 'EMAIL' | 'TELEFONE' | 'ALEATORIA'
export type TipoConta = 'CORRENTE' | 'POUPANCA'

export interface CandidaturaPublicaRequest {
  nome: string
  cpf: string
  crm: string
  crmUf: string
  email: string
  telefone: string | null

  dataNascimento: string | null // "YYYY-MM-DD"
  nacionalidade: string | null
  naturalidade: string | null
  estadoCivil: EstadoCivil | null
  nomeMae: string | null
  nomePai: string | null

  logradouro: string | null
  numero: string | null
  complemento: string | null
  bairro: string | null
  cidade: string | null
  uf: string | null
  cep: string | null

  rgNumero: string | null
  rgOrgaoExpedidor: string | null
  rgUf: string | null
  rqe: string | null

  canalOrigem: string | null
  nomeIndicador: string | null
  situacaoFormacao: string[] | null
  areasAtuacao: string | null
  procedimentosRealiza: string | null
}

export interface CandidaturaPublicaResponse extends CandidaturaPublicaRequest {
  id: string
  status: string
}

export interface DocumentoCandidatura {
  id: string
  tipo: TipoDocumentoCandidatura
  nomeArquivo: string
  caminhoStorage: string
  statusValidacao: StatusValidacaoDocumento
  motivoReprovacao?: string
  createdAt: string
}

export interface CandidaturaDadosBancariosRequest {
  tipoRecebimento: 'PIX' | 'TED'
  tipoPix: TipoPix | null
  chavePix: string | null
  cpfsAdicionaisSplit: string | null
  bancoCodigo: string | null
  bancoNome: string | null
  agencia: string | null
  conta: string | null
  tipoConta: TipoConta | null
}

export interface DadosBancariosCandidaturaResponse {
  id: string
  tipoRecebimento: string
  tipoPix?: TipoPix
  chavePix?: string
  cpfsAdicionaisSplit?: string
  bancoCodigo?: string
  bancoNome?: string
  agencia?: string
  conta?: string
  tipoConta?: string
  updatedAt: string
}

export interface DeclaracaoLgpdRequest {
  aceiteDeclaracaoVeracidade: boolean
  autorizacaoUsoDados: boolean
  autorizacaoCompartilhamento: boolean
  avisoPrivacidadeLido: boolean
  assinaturaNome: string
}

export interface DeclaracaoLgpdResponse {
  aceiteDeclaracaoVeracidade: boolean
  autorizacaoUsoDados: boolean
  autorizacaoCompartilhamento: boolean
  avisoPrivacidadeLido: boolean
  assinaturaNome: string
  assinadoEm: string
}

export interface FinalizarCandidaturaResponse {
  id: string
  status: string
  mensagem: string
}

const BASE_URL = '/api/onboarding/publico/candidaturas'

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

async function criar(data: CandidaturaPublicaRequest): Promise<CandidaturaPublicaResponse> {
  const res = await fetch(BASE_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  return handleResponse<CandidaturaPublicaResponse>(res)
}

async function atualizar(id: string, data: CandidaturaPublicaRequest): Promise<CandidaturaPublicaResponse> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  return handleResponse<CandidaturaPublicaResponse>(res)
}

async function buscar(id: string): Promise<CandidaturaPublicaResponse> {
  const res = await fetch(`${BASE_URL}/${id}`)
  return handleResponse<CandidaturaPublicaResponse>(res)
}

async function uploadDocumento(
  id: string,
  tipo: TipoDocumentoCandidatura,
  arquivo: File
): Promise<DocumentoCandidatura> {
  const formData = new FormData()
  formData.append('arquivo', arquivo)
  // Sem Content-Type manual — o browser define o boundary do multipart automaticamente.
  const res = await fetch(`${BASE_URL}/${id}/documentos?tipo=${tipo}`, {
    method: 'POST',
    body: formData,
  })
  return handleResponse<DocumentoCandidatura>(res)
}

async function atualizarDadosBancarios(
  id: string,
  data: CandidaturaDadosBancariosRequest
): Promise<DadosBancariosCandidaturaResponse> {
  const res = await fetch(`${BASE_URL}/${id}/dados-bancarios`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  return handleResponse<DadosBancariosCandidaturaResponse>(res)
}

async function registrarDeclaracaoLgpd(
  id: string,
  data: DeclaracaoLgpdRequest
): Promise<DeclaracaoLgpdResponse> {
  const res = await fetch(`${BASE_URL}/${id}/declaracoes-lgpd`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  return handleResponse<DeclaracaoLgpdResponse>(res)
}

async function finalizar(id: string): Promise<FinalizarCandidaturaResponse> {
  const res = await fetch(`${BASE_URL}/${id}/finalizar`, { method: 'POST' })
  return handleResponse<FinalizarCandidaturaResponse>(res)
}

export const candidaturaMedicoApi = {
  criar, atualizar, buscar, uploadDocumento,
  atualizarDadosBancarios, registrarDeclaracaoLgpd, finalizar,
}
