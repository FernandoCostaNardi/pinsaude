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
      msg = body.message ?? msg
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export type TipoTomador = 'HOSPITAL' | 'CLINICA' | 'OPERADORA' | 'PACIENTE_PF'

export interface TomadorAliquota {
  id: string
  tipoTributo: 'ISS' | 'IR' | 'CSLL' | 'PIS' | 'COFINS'
  valorAliquota: number  // percentual: 5.0000 = 5%
}

export interface TomadorCnae {
  id: string
  codigoCnae: string
  descricao: string | null
}

export interface TomadorServico {
  id: string          // id do vínculo tomador↔serviço
  servicoId: string   // id do serviço no catálogo LC 116
  codigoLc116: string | null
  descricaoPadrao: string | null
}

export interface Tomador {
  id: string
  tipo: TipoTomador
  cnpjCpf: string
  razaoSocialNome: string
  nomeFantasia: string | null
  municipio: string | null
  inscricaoMunicipal: string | null
  indicadorRetencaoFederal: boolean
  indicadorRetencaoIss: boolean
  email: string | null
  telefone: string | null
  logradouro: string | null
  bairro: string | null
  cep: string | null
  uf: string | null
  pais: string | null
  aliquotas: TomadorAliquota[]
  cnaes: TomadorCnae[]
  servicos: TomadorServico[]
  temGrupoFaturamento: boolean
  empresas: TomadorEmpresa[]
}

export interface TomadorRequest {
  tipo: string
  cnpjCpf: string
  razaoSocialNome: string
  nomeFantasia: string
  municipio: string
  inscricaoMunicipal: string
  indicadorRetencaoFederal: boolean
  indicadorRetencaoIss: boolean
  email: string
  telefone: string
  logradouro: string
  bairro: string
  cep: string
  uf: string
  pais: string
}

export interface ReceitaFederalData {
  cnpj: string
  razaoSocial: string
  nomeFantasia: string | null
  municipio: string | null
  uf: string | null
  logradouro: string | null
  numero: string | null
  bairro: string | null
  cep: string | null
  email: string | null
  telefone: string | null
}

export const tomadoresApi = {
  async listar(q?: string, medicoId?: string, empresaId?: string): Promise<Tomador[]> {
    const params = new URLSearchParams()
    if (q) params.set('q', q)
    if (medicoId) params.set('medicoId', medicoId)
    if (empresaId) params.set('empresaId', empresaId)
    const qs = params.toString()
    const res = await fetch(`/api/tomadores${qs ? `?${qs}` : ''}`, {
      headers: authHeaders(),
    })
    return handleResponse<Tomador[]>(res)
  },

  async buscarPorId(id: string): Promise<Tomador> {
    const res = await fetch(`/api/tomadores/${id}`, {
      headers: authHeaders(),
    })
    return handleResponse<Tomador>(res)
  },

  async criar(req: TomadorRequest): Promise<Tomador> {
    const res = await fetch('/api/tomadores', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<Tomador>(res)
  },

  async atualizar(id: string, req: TomadorRequest): Promise<Tomador> {
    const res = await fetch(`/api/tomadores/${id}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<Tomador>(res)
  },

  async deletar(id: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${id}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  async consultarReceita(cnpj: string): Promise<ReceitaFederalData | null> {
    const digits = cnpj.replace(/\D/g, '')
    const res = await fetch(`/api/tomadores/receita/${digits}`, {
      headers: authHeaders(),
    })
    if (res.status === 404) return null
    return handleResponse<ReceitaFederalData>(res)
  },

  async listarAliquotas(tomadorId: string): Promise<TomadorAliquota[]> {
    const res = await fetch(`/api/tomadores/${tomadorId}/aliquotas`, { headers: authHeaders() })
    return handleResponse<TomadorAliquota[]>(res)
  },

  async salvarAliquota(tomadorId: string, tipoTributo: string, valorAliquota: number): Promise<TomadorAliquota> {
    const res = await fetch(`/api/tomadores/${tomadorId}/aliquotas`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ tipoTributo, valorAliquota }),
    })
    return handleResponse<TomadorAliquota>(res)
  },

  async removerAliquota(tomadorId: string, aliquotaId: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${tomadorId}/aliquotas/${aliquotaId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  async listarCnaes(tomadorId: string): Promise<TomadorCnae[]> {
    const res = await fetch(`/api/tomadores/${tomadorId}/cnaes`, { headers: authHeaders() })
    return handleResponse<TomadorCnae[]>(res)
  },

  async adicionarCnae(tomadorId: string, codigoCnae: string, descricao: string): Promise<TomadorCnae> {
    const res = await fetch(`/api/tomadores/${tomadorId}/cnaes`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ codigoCnae, descricao }),
    })
    return handleResponse<TomadorCnae>(res)
  },

  async removerCnae(tomadorId: string, cnaeId: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${tomadorId}/cnaes/${cnaeId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  async listarServicos(tomadorId: string): Promise<TomadorServico[]> {
    const res = await fetch(`/api/tomadores/${tomadorId}/servicos`, { headers: authHeaders() })
    return handleResponse<TomadorServico[]>(res)
  },

  async adicionarServico(tomadorId: string, servicoId: string): Promise<TomadorServico> {
    const res = await fetch(`/api/tomadores/${tomadorId}/servicos`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ servicoId }),
    })
    return handleResponse<TomadorServico>(res)
  },

  async removerServico(tomadorId: string, vinculoId: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${tomadorId}/servicos/${vinculoId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  // ─── Grupos de faturamento ────────────────────────────────────────────────

  async listarGrupos(tomadorId: string): Promise<TomadorGrupoFaturamento[]> {
    const res = await fetch(`/api/tomadores/${tomadorId}/grupos`, { headers: authHeaders() })
    return handleResponse<TomadorGrupoFaturamento[]>(res)
  },

  async criarGrupo(tomadorId: string, req: TomadorGrupoFaturamentoRequest): Promise<TomadorGrupoFaturamento> {
    const res = await fetch(`/api/tomadores/${tomadorId}/grupos`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<TomadorGrupoFaturamento>(res)
  },

  async atualizarGrupo(tomadorId: string, grupoId: string, req: TomadorGrupoFaturamentoRequest): Promise<TomadorGrupoFaturamento> {
    const res = await fetch(`/api/tomadores/${tomadorId}/grupos/${grupoId}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<TomadorGrupoFaturamento>(res)
  },

  async removerGrupo(tomadorId: string, grupoId: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${tomadorId}/grupos/${grupoId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  // ─── Modalidades (tabela de preços) ───────────────────────────────────────

  async listarModalidades(tomadorId: string): Promise<TomadorModalidade[]> {
    const res = await fetch(`/api/tomadores/${tomadorId}/modalidades`, { headers: authHeaders() })
    return handleResponse<TomadorModalidade[]>(res)
  },

  async criarModalidade(tomadorId: string, req: TomadorModalidadeRequest): Promise<TomadorModalidade> {
    const res = await fetch(`/api/tomadores/${tomadorId}/modalidades`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<TomadorModalidade>(res)
  },

  async atualizarModalidade(tomadorId: string, modalidadeId: string, req: TomadorModalidadeRequest): Promise<TomadorModalidade> {
    const res = await fetch(`/api/tomadores/${tomadorId}/modalidades/${modalidadeId}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<TomadorModalidade>(res)
  },

  async removerModalidade(tomadorId: string, modalidadeId: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${tomadorId}/modalidades/${modalidadeId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  // ─── Ocorrências pré-cadastradas com valor ───────────────────────────────

  async listarOcorrencias(tomadorId: string): Promise<TomadorOcorrencia[]> {
    const res = await fetch(`/api/tomadores/${tomadorId}/ocorrencias`, { headers: authHeaders() })
    return handleResponse<TomadorOcorrencia[]>(res)
  },

  async criarOcorrencia(tomadorId: string, req: TomadorOcorrenciaRequest): Promise<TomadorOcorrencia> {
    const res = await fetch(`/api/tomadores/${tomadorId}/ocorrencias`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<TomadorOcorrencia>(res)
  },

  async atualizarOcorrencia(tomadorId: string, ocorrenciaId: string, req: TomadorOcorrenciaRequest): Promise<TomadorOcorrencia> {
    const res = await fetch(`/api/tomadores/${tomadorId}/ocorrencias/${ocorrenciaId}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<TomadorOcorrencia>(res)
  },

  async removerOcorrencia(tomadorId: string, ocorrenciaId: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${tomadorId}/ocorrencias/${ocorrenciaId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  // ─── Preenchimento rápido de turno ────────────────────────────────────────

  async listarHorariosPadrao(tomadorId: string): Promise<TomadorHorarioPadrao[]> {
    const res = await fetch(`/api/tomadores/${tomadorId}/turnos-padrao`, { headers: authHeaders() })
    return handleResponse<TomadorHorarioPadrao[]>(res)
  },

  async criarHorarioPadrao(tomadorId: string, req: TomadorHorarioPadraoRequest): Promise<TomadorHorarioPadrao> {
    const res = await fetch(`/api/tomadores/${tomadorId}/turnos-padrao`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<TomadorHorarioPadrao>(res)
  },

  async atualizarHorarioPadrao(tomadorId: string, horarioId: string, req: TomadorHorarioPadraoRequest): Promise<TomadorHorarioPadrao> {
    const res = await fetch(`/api/tomadores/${tomadorId}/turnos-padrao/${horarioId}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<TomadorHorarioPadrao>(res)
  },

  async removerHorarioPadrao(tomadorId: string, horarioId: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${tomadorId}/turnos-padrao/${horarioId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  // ─── Serviços operacionais (setores) ─────────────────────────────────────

  async criarServicoOperacional(tomadorId: string, req: TomadorServicoOperacionalRequest): Promise<TomadorServicoOperacional> {
    const res = await fetch(`/api/tomadores/${tomadorId}/servicos-operacionais`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<TomadorServicoOperacional>(res)
  },

  async removerServicoOperacional(tomadorId: string, setorId: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${tomadorId}/servicos-operacionais/${setorId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  // ─── Médicos alocados ao tomador (EPIC-15) ────────────────────────────────

  async listarMedicos(tomadorId: string): Promise<MedicoTomador[]> {
    const res = await fetch(`/api/tomadores/${tomadorId}/medicos`, { headers: authHeaders() })
    return handleResponse<MedicoTomador[]>(res)
  },

  async adicionarMedico(tomadorId: string, medicoId: string): Promise<MedicoTomador> {
    const res = await fetch(`/api/tomadores/${tomadorId}/medicos`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ medicoId }),
    })
    return handleResponse<MedicoTomador>(res)
  },

  async removerMedico(tomadorId: string, medicoId: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${tomadorId}/medicos/${medicoId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  // ─── Empresas Pin vinculadas ao tomador (PINSAUDE-13.12) ──────────────────

  async listarEmpresas(tomadorId: string): Promise<TomadorEmpresa[]> {
    const res = await fetch(`/api/tomadores/${tomadorId}/empresas`, { headers: authHeaders() })
    return handleResponse<TomadorEmpresa[]>(res)
  },

  async adicionarEmpresa(tomadorId: string, empresaId: string): Promise<TomadorEmpresa> {
    const res = await fetch(`/api/tomadores/${tomadorId}/empresas`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ empresaId }),
    })
    return handleResponse<TomadorEmpresa>(res)
  },

  async removerEmpresa(tomadorId: string, empresaId: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${tomadorId}/empresas/${empresaId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },
}

// ─── Tipos para grupos / modalidades / setores ────────────────────────────────

export interface TomadorServicoOperacional {
  id: string
  tomadorId: string
  grupoId: string
  nome: string
  ativo: boolean
}

export interface TomadorGrupoFaturamento {
  id: string
  tomadorId: string
  servicoLc116Id: string
  codigoLc116: string | null
  descricaoServico: string | null
  nome: string
  descricaoNota: string
  ordem: number
  ativo: boolean
  servicosOperacionais: TomadorServicoOperacional[]
}

export interface TomadorGrupoFaturamentoRequest {
  servicoLc116Id: string
  nome: string
  descricaoNota: string
  ordem: number
  ativo: boolean
}

export interface TomadorModalidade {
  id: string
  tomadorId: string
  nome: string
  tipo: 'PLANTAO' | 'MENSAL' | 'META'
  turno: 'DIURNO' | 'NOTURNO' | null
  horario: string | null
  horas: number | null
  valorCentavos: number
  deslocamentoCentavos: number
  ativo: boolean
  // Campos do tipo META (pagamento proporcional por hora/dia)
  unidadeCalculo: 'HORA' | 'DIA' | null
  metaHoras: number | null
  metaDias: number | null
}

export interface TomadorModalidadeRequest {
  nome: string
  tipo: 'PLANTAO' | 'MENSAL' | 'META'
  turno: 'DIURNO' | 'NOTURNO' | null
  horario: string | null
  horas: number | null
  valorCentavos: number
  deslocamentoCentavos: number
  ativo: boolean
  unidadeCalculo: 'HORA' | 'DIA' | null
  metaHoras: number | null
  metaDias: number | null
}

export interface TomadorServicoOperacionalRequest {
  grupoId: string
  nome: string
  ativo: boolean
}

export interface TomadorOcorrencia {
  id: string
  tomadorId: string
  nome: string
  tipoValor: 'PERCENTUAL' | 'FIXO' | 'SEM_VALOR'
  valorPercentual: number | null
  valorCentavos: number | null
  ativo: boolean
}

export interface TomadorOcorrenciaRequest {
  nome: string
  tipoValor: 'PERCENTUAL' | 'FIXO' | 'SEM_VALOR'
  valorPercentual: number | null
  valorCentavos: number | null
  ativo: boolean
}

export interface TomadorHorarioPadrao {
  id: string
  tomadorId: string
  turno: 'DIURNO' | 'NOTURNO'
  horas: number
  horario: string
  ordem: number
  ativo: boolean
}

export interface TomadorHorarioPadraoRequest {
  turno: 'DIURNO' | 'NOTURNO'
  horas: number
  horario: string
  ordem: number
  ativo: boolean
}

export interface MedicoTomador {
  medicoId: string
  tomadorId: string
  createdAt: string
}

export interface TomadorEmpresa {
  tomadorId: string
  empresaId: string
  createdAt: string
}
