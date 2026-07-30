import { useEffect, useState, ReactNode, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft, Pencil, UserCheck, UserX, FileText,
  User, Building2, CreditCard, Clock, CheckCircle2,
  XCircle, AlertCircle, Mail, Send, Landmark,
  Award, GraduationCap, Home, BookOpen, Hospital,
} from 'lucide-react'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import {
  Medico, StatusMedico, StatusJuntaComercial, StatusContrato,
  TipoDocumentoMedico, StatusValidacaoDocumento, HistoricoMedico,
  HistoricoTaxaPin, VinculoEmpresa, medicosApi,
} from '../api/medicosApi'
import { empresasApi, Empresa } from '../api/empresasApi'
import { Tomador, tomadoresApi } from '../api/tomadoresApi'
import { MedicoWizardModal, maskPixKey } from '../components/MedicoWizardModal'
import { MedicoInativarModal } from '../components/MedicoInativarModal'
import { DocumentosModal } from '../components/DocumentosModal'
import { ChecklistEditor } from '../components/ChecklistEditor'
import { formatCpf } from '../utils/cpf'
import { formatCnpj } from '../utils/cnpj'
import { useAuth } from '../auth/useAuth'
import { bancos } from '../components/BancoSelect'

// ─── Status ───────────────────────────────────────────────────────────────────

const STATUS_BADGE: Record<StatusMedico, string> = {
  RASCUNHO: 'bg-amber-50 text-amber-700 border border-amber-200',
  ATIVO:    'bg-green-50 text-green-700 border border-green-200',
  INATIVO:  'bg-gray-100 text-gray-500 border border-gray-200',
  SUSPENSO: 'bg-red-50 text-red-600 border border-red-200',
}
const STATUS_LABELS: Record<StatusMedico, string> = {
  RASCUNHO: 'Rascunho',
  ATIVO:    'Ativo',
  INATIVO:  'Inativo',
  SUSPENSO: 'Suspenso',
}

// ─── Tipos de ação (ícone + cor) ──────────────────────────────────────────────

const ACAO_CONFIG: Record<string, { label: string; cls: string; Icon: React.ElementType }> = {
  CADASTRO:                    { label: 'Cadastro',             cls: 'bg-primary-50 text-primary',     Icon: User },
  ATIVACAO:                    { label: 'Ativação',             cls: 'bg-green-50 text-green-600',     Icon: CheckCircle2 },
  ATIVACAO_AUTOMATICA:         { label: 'Ativação automática',  cls: 'bg-green-50 text-green-600',     Icon: CheckCircle2 },
  INATIVACAO:                  { label: 'Inativação',           cls: 'bg-gray-100 text-gray-500',      Icon: XCircle },
  ATUALIZACAO_DADOS:           { label: 'Dados atualizados',    cls: 'bg-blue-50 text-blue-600',       Icon: Pencil },
  ATUALIZACAO_DADOS_BANCARIOS: { label: 'Dados bancários',      cls: 'bg-purple-50 text-purple-600',   Icon: CreditCard },
  UPLOAD_DOCUMENTO:            { label: 'Documento enviado',    cls: 'bg-amber-50 text-amber-600',     Icon: FileText },
  VALIDACAO_DOCUMENTO:         { label: 'Documento validado',   cls: 'bg-teal-50 text-teal-600',       Icon: CheckCircle2 },
  EXCLUSAO_DOCUMENTO:          { label: 'Documento removido',   cls: 'bg-red-50 text-red-500',         Icon: XCircle },
  ATUALIZACAO_CHECKLIST:       { label: 'Checklist',            cls: 'bg-indigo-50 text-indigo-600',   Icon: BookOpen },
  ENVIO_CONVITE:               { label: 'Convite enviado',      cls: 'bg-blue-50 text-blue-600',       Icon: Mail },
  ENVIO_CONTRATO:              { label: 'Contrato Clicksign',   cls: 'bg-violet-50 text-violet-600',   Icon: Send },
  ATUALIZACAO_JUNTA_COMERCIAL: { label: 'Junta Comercial',      cls: 'bg-amber-50 text-amber-700',     Icon: Landmark },
}

// ─── Junta + Contrato badges ──────────────────────────────────────────────────

const JUNTA_CONFIG: Record<StatusJuntaComercial, { label: string; cls: string }> = {
  AGUARDANDO: { label: 'Aguardando',  cls: 'bg-amber-50 text-amber-700 border border-amber-200' },
  EM_ANALISE: { label: 'Em análise',  cls: 'bg-blue-50 text-blue-700 border border-blue-200' },
  APROVADO:   { label: 'Aprovado',    cls: 'bg-green-50 text-green-700 border border-green-200' },
  RECUSADO:   { label: 'Recusado',    cls: 'bg-red-50 text-red-700 border border-red-200' },
}

const CONTRATO_CONFIG: Record<StatusContrato, { label: string; cls: string }> = {
  AGUARDANDO_ENVIO: { label: 'Aguardando envio', cls: 'bg-gray-100 text-gray-500 border border-gray-200' },
  ENVIADO:          { label: 'Enviado',           cls: 'bg-amber-50 text-amber-700 border border-amber-200' },
  VISUALIZADO:      { label: 'Visualizado',       cls: 'bg-blue-50 text-blue-700 border border-blue-200' },
  ASSINADO:         { label: 'Assinado',          cls: 'bg-green-50 text-green-700 border border-green-200' },
  RECUSADO:         { label: 'Recusado',          cls: 'bg-red-50 text-red-700 border border-red-200' },
}

// ─── Documento row (inline no perfil) ────────────────────────────────────────

const DOC_TIPO_INFO: Record<TipoDocumentoMedico, { label: string; Icon: React.ElementType }> = {
  CRM:           { label: 'Registro CRM',               Icon: Award         },
  DIPLOMA:       { label: 'Diploma Médico',              Icon: GraduationCap },
  IDENTIDADE:    { label: 'Identidade (CNH ou RG)',      Icon: CreditCard    },
  RESIDENCIA:    { label: 'Comprovante de Residência',   Icon: Home          },
  CONTRATO:      { label: 'Contrato',                    Icon: FileText      },
  ESPECIALIDADES:{ label: 'Certificado de Especialidades', Icon: BookOpen   },
}
const DOC_STATUS_CONFIG: Record<StatusValidacaoDocumento, { label: string; cls: string; Icon: React.ElementType }> = {
  PENDENTE:  { label: 'Aguardando', cls: 'bg-gray-100 text-gray-500',  Icon: Clock },
  APROVADO:  { label: 'Aprovado',   cls: 'bg-green-50 text-green-600', Icon: CheckCircle2 },
  REPROVADO: { label: 'Reprovado',  cls: 'bg-red-50 text-red-600',     Icon: XCircle },
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit', month: 'short', year: 'numeric',
  })
}

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function formatDocumentoTomador(tipo: Tomador['tipo'], cnpjCpf: string): string {
  const digits = cnpjCpf.replace(/\D/g, '')
  if (tipo === 'PACIENTE_PF') return formatCpf(digits)
  return formatCnpj(digits)
}

function WhatsAppIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
    </svg>
  )
}

function InfoRow({ label, value }: { label: ReactNode; value?: string | null }) {
  return (
    <div className="flex flex-col gap-0.5">
      <p className="text-[11px] font-semibold text-ds-light uppercase tracking-wide flex items-center gap-1.5">{label}</p>
      <p className="text-sm text-ds-text font-medium">{value || '—'}</p>
    </div>
  )
}

// ─── Tabs ─────────────────────────────────────────────────────────────────────

type Tab = 'dados' | 'bancarios' | 'documentos' | 'onboarding' | 'historico' | 'taxa-pin'

// ─── Page ─────────────────────────────────────────────────────────────────────

export function MedicoPerfilPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()

  const isGestao   = user?.realm_access?.roles.includes('gestao') ?? false
  const isOperacao = user?.realm_access?.roles.includes('operacao') ?? false
  const canEdit    = isGestao || isOperacao

  const [medico,    setMedico]    = useState<Medico | null>(null)
  const [empresa,   setEmpresa]   = useState<Empresa | null>(null)
  const [historico,        setHistorico]        = useState<HistoricoMedico[]>([])
  const [historicoTaxaPin, setHistoricoTaxaPin] = useState<HistoricoTaxaPin[]>([])
  const taxaPinLoaded = useRef(false)
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState<string | null>(null)
  const [tab,       setTab]       = useState<Tab>('dados')

  // Multi-empresa (gestão)
  const [todasEmpresas,       setTodasEmpresas]       = useState<Empresa[]>([])
  const [addEmpresaId,        setAddEmpresaId]        = useState('')
  const [addingVinculo,       setAddingVinculo]       = useState(false)
  const [removingVinculoId,   setRemovingVinculoId]   = useState<string | null>(null)
  const [vinculoError,        setVinculoError]        = useState<string | null>(null)

  // Tomadores associados (EPIC-15.11)
  const [todosTomadores,      setTodosTomadores]      = useState<Tomador[]>([])
  const [tomadoresAlocados,   setTomadoresAlocados]   = useState<Tomador[]>([])
  const [addTomadorId,        setAddTomadorId]        = useState('')
  const [addingTomador,       setAddingTomador]       = useState(false)
  const [removingTomadorId,   setRemovingTomadorId]   = useState<string | null>(null)
  const [tomadorError,        setTomadorError]        = useState<string | null>(null)

  const [showEdit,         setShowEdit]         = useState(false)
  const [inativando,       setInativando]       = useState(false)
  const [showDocs,         setShowDocs]         = useState(false)
  const [activating,       setActivating]       = useState(false)
  const [enviandoConvite,  setEnviandoConvite]  = useState(false)
  const [enviandoContrato, setEnviandoContrato] = useState(false)
  const [novoStatusJunta,  setNovoStatusJunta]  = useState<StatusJuntaComercial | ''>('')
  const [obsJunta,         setObsJunta]         = useState('')
  const [atualizandoJunta, setAtualizandoJunta] = useState(false)

  useEffect(() => {
    if (!id) return
    Promise.all([
      medicosApi.buscarPorId(id),
      medicosApi.listarHistorico(id).catch(() => [] as HistoricoMedico[]),
    ])
      .then(([m, h]) => {
        setMedico(m)
        setHistorico(h)
        if (m.empresaId) {
          empresasApi.listar(0, 200)
            .then(p => {
              setEmpresa(p.content.find(e => e.id === m.empresaId) ?? null)
              if (isGestao) setTodasEmpresas(p.content)
            })
            .catch(() => {})
        } else if (isGestao) {
          empresasApi.listar(0, 200).then(p => setTodasEmpresas(p.content)).catch(() => {})
        }
        tomadoresApi.listar(undefined, m.id).then(setTomadoresAlocados).catch(() => {})
        if (canEdit) tomadoresApi.listar().then(setTodosTomadores).catch(() => {})
      })
      .catch(() => setError('Erro ao carregar dados do médico'))
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    if (tab === 'taxa-pin' && id && !taxaPinLoaded.current) {
      taxaPinLoaded.current = true
      medicosApi.listarHistoricoTaxaPin(id).then(setHistoricoTaxaPin).catch(() => {})
    }
  }, [tab, id])

  async function handleAtivar() {
    if (!medico) return
    setActivating(true)
    setError(null)
    try {
      const updated = await medicosApi.ativar(medico.id)
      setMedico(updated)
      const h = await medicosApi.listarHistorico(medico.id).catch(() => [] as HistoricoMedico[])
      setHistorico(h)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao ativar médico')
    } finally {
      setActivating(false)
    }
  }

  async function handleEnviarConvite() {
    if (!medico) return
    setEnviandoConvite(true)
    setError(null)
    try {
      const convite = await medicosApi.enviarConvite(medico.id)
      setMedico(prev => prev ? { ...prev, ultimoConvite: convite } : prev)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao enviar convite')
    } finally {
      setEnviandoConvite(false)
    }
  }

  async function handleEnviarContrato() {
    if (!medico) return
    setEnviandoContrato(true)
    setError(null)
    try {
      const contrato = await medicosApi.enviarContrato(medico.id)
      setMedico(prev => prev ? { ...prev, contratoAssinatura: contrato } : prev)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao enviar contrato ao Clicksign')
    } finally {
      setEnviandoContrato(false)
    }
  }

  async function handleAtualizarJunta() {
    if (!medico || !novoStatusJunta) return
    setAtualizandoJunta(true)
    setError(null)
    try {
      const updated = await medicosApi.atualizarJuntaComercial(medico.id, novoStatusJunta, obsJunta || undefined)
      setMedico(updated)
      setNovoStatusJunta('')
      setObsJunta('')
      const h = await medicosApi.listarHistorico(medico.id).catch(() => [] as HistoricoMedico[])
      setHistorico(h)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao atualizar status da Junta Comercial')
    } finally {
      setAtualizandoJunta(false)
    }
  }

  async function handleAdicionarVinculo() {
    if (!medico || !addEmpresaId) return
    setAddingVinculo(true)
    setVinculoError(null)
    try {
      const novoVinculo = await medicosApi.adicionarVinculo(medico.id, addEmpresaId)
      setMedico(m => m ? { ...m, empresas: [...(m.empresas ?? []), novoVinculo] } : m)
      setAddEmpresaId('')
    } catch (e) {
      setVinculoError(e instanceof Error ? e.message : 'Erro ao adicionar vínculo')
    } finally {
      setAddingVinculo(false)
    }
  }

  async function handleRemoverVinculo(empresaId: string) {
    if (!medico) return
    setRemovingVinculoId(empresaId)
    setVinculoError(null)
    try {
      await medicosApi.removerVinculo(medico.id, empresaId)
      setMedico(m => m ? { ...m, empresas: (m.empresas ?? []).filter(v => v.empresaId !== empresaId) } : m)
    } catch (e) {
      setVinculoError(e instanceof Error ? e.message : 'Erro ao remover vínculo')
    } finally {
      setRemovingVinculoId(null)
    }
  }

  async function handleAdicionarTomador() {
    if (!medico || !addTomadorId) return
    setAddingTomador(true)
    setTomadorError(null)
    try {
      await tomadoresApi.adicionarMedico(addTomadorId, medico.id)
      const tomador = todosTomadores.find(t => t.id === addTomadorId)
      if (tomador) setTomadoresAlocados(list => [...list, tomador])
      setAddTomadorId('')
    } catch (e) {
      setTomadorError(e instanceof Error ? e.message : 'Erro ao adicionar tomador')
    } finally {
      setAddingTomador(false)
    }
  }

  async function handleRemoverTomador(tomadorId: string) {
    if (!medico) return
    setRemovingTomadorId(tomadorId)
    setTomadorError(null)
    try {
      await tomadoresApi.removerMedico(tomadorId, medico.id)
      setTomadoresAlocados(list => list.filter(t => t.id !== tomadorId))
    } catch (e) {
      setTomadorError(e instanceof Error ? e.message : 'Erro ao remover tomador')
    } finally {
      setRemovingTomadorId(null)
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center py-32">
        <Spinner size="lg" />
      </div>
    )
  }

  if (error || !medico) {
    return (
      <div className="p-6">
        <Alert variant="error">{error ?? 'Médico não encontrado'}</Alert>
        <button onClick={() => navigate('/medicos')} className="mt-4 text-sm text-primary hover:underline flex items-center gap-1">
          <ArrowLeft size={14} /> Voltar para Médicos
        </button>
      </div>
    )
  }

  const db = medico.dadosBancarios

  return (
    <div className="flex flex-col gap-5">

      {/* Breadcrumb + Header */}
      <div className="flex flex-col gap-3">
        <button
          onClick={() => navigate('/medicos')}
          className="flex items-center gap-1.5 text-xs text-ds-light hover:text-primary transition-colors w-fit"
        >
          <ArrowLeft size={13} /> Médicos
        </button>

        <div className="bg-white rounded-xl border border-ds-border shadow-sm p-5">
          <div className="flex flex-col sm:flex-row sm:items-start gap-4">
            {/* Avatar */}
            <div className="w-14 h-14 rounded-2xl bg-primary-50 flex items-center justify-center shrink-0">
              <span className="text-xl font-black text-primary">
                {medico.nome.trim().split(/\s+/).filter(w => w.length > 1).slice(0, 2)
                  .map(w => w[0].toUpperCase()).join('') || medico.nome.slice(0, 2).toUpperCase()}
              </span>
            </div>

            {/* Info */}
            <div className="flex-1 min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-lg font-bold text-ds-text">{medico.nome}</h1>
                <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold ${STATUS_BADGE[medico.status]}`}>
                  {STATUS_LABELS[medico.status]}
                </span>
              </div>
              <div className="flex flex-wrap gap-3 mt-1">
                <span className="text-sm text-ds-mid">
                  <span className="font-semibold">CRM</span> {medico.crm}/{medico.crmUf.trim()}
                </span>
                {medico.especialidade && (
                  <span className="text-sm text-ds-mid">· {medico.especialidade}</span>
                )}
                {(medico.empresas && medico.empresas.length > 0) ? (
                  medico.empresas.map(v => (
                    <span key={v.empresaId} className="text-sm text-ds-mid flex items-center gap-1">
                      · <Building2 size={12} /> {v.razaoSocial}
                    </span>
                  ))
                ) : empresa ? (
                  <span className="text-sm text-ds-mid flex items-center gap-1">
                    · <Building2 size={12} /> {empresa.razaoSocial}
                  </span>
                ) : null}
              </div>
            </div>

            {/* Actions */}
            <div className="flex flex-wrap gap-2 shrink-0">
              {canEdit && (medico.status === 'RASCUNHO' || medico.status === 'INATIVO') && (
                <Button size="sm" variant="outline" onClick={handleAtivar} disabled={activating}>
                  <UserCheck size={14} /> {medico.status === 'INATIVO' ? 'Reativar' : 'Ativar'}
                </Button>
              )}
              {canEdit && (
                <Button size="sm" variant="outline" onClick={() => setShowEdit(true)}>
                  <Pencil size={14} /> Editar
                </Button>
              )}
              {isGestao && medico.status !== 'INATIVO' && (
                <Button size="sm" variant="outline" onClick={() => setInativando(true)}
                  className="!text-red-600 !border-red-200 hover:!bg-red-50">
                  <UserX size={14} /> Inativar
                </Button>
              )}
              <Button size="sm" variant="outline"
                onClick={() => navigate(`/notas?medicoId=${medico.id}`)}>
                <FileText size={14} /> Ver Notas
              </Button>
            </div>
          </div>
        </div>
      </div>

      {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

      {/* Tabs */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
        <div className="flex border-b border-ds-border overflow-x-auto">
          {([
            { key: 'dados',       label: 'Dados Pessoais',  Icon: User },
            { key: 'bancarios',   label: 'Dados Bancários', Icon: CreditCard },
            { key: 'documentos',  label: 'Documentos',      Icon: FileText },
            { key: 'onboarding',  label: 'Onboarding',      Icon: Send },
            { key: 'taxa-pin',    label: 'Taxa Pin',         Icon: Landmark },
            { key: 'historico',   label: 'Histórico',       Icon: Clock },
          ] as { key: Tab; label: string; Icon: React.ElementType }[]).map(({ key, label, Icon }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={[
                'flex items-center gap-2 px-5 py-3 text-sm font-semibold border-b-2 transition-colors whitespace-nowrap',
                tab === key
                  ? 'border-primary text-primary bg-primary-50/30'
                  : 'border-transparent text-ds-mid hover:text-primary hover:bg-primary-50/20',
              ].join(' ')}
            >
              <Icon size={15} />
              {label}
            </button>
          ))}
        </div>

        {/* ── Dados Pessoais ── */}
        {tab === 'dados' && (
          <div className="p-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
              <InfoRow label="Nome completo"      value={medico.nome} />
              <InfoRow label="CPF"                value={medico.cpf ? formatCpf(medico.cpf) : '—'} />
              <InfoRow label="CRM"                value={`${medico.crm} / ${medico.crmUf.trim()}`} />
              <InfoRow label="Especialidade"      value={medico.especialidade} />
              <InfoRow label="E-mail"             value={medico.email} />
              <InfoRow
                label={
                  <span className="flex items-center gap-1.5">
                    Telefone
                    <span className="flex items-center gap-1 text-[#25D366]">
                      <WhatsAppIcon className="w-3 h-3" />
                      WhatsApp
                    </span>
                  </span>
                }
                value={medico.telefone}
              />
              <InfoRow
                label="Taxa Pin negociada"
                value={medico.taxaPinPct != null
                  ? `${(medico.taxaPinPct * 100).toFixed(2).replace('.', ',')}%`
                  : '15,00%'}
              />
              <InfoRow label="Status"             value={STATUS_LABELS[medico.status]} />
              <InfoRow label="Cadastrado em"      value={formatDate(medico.createdAt)} />
              <InfoRow label="Última atualização" value={formatDate(medico.updatedAt)} />
            </div>

            {/* Empresas Associadas */}
            <div className="mt-6 pt-6 border-t border-ds-border">
              <div className="flex items-center justify-between mb-3">
                <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide">Empresas Associadas</p>
                <span className="text-xs text-ds-light">{(medico.empresas ?? []).length} empresa(s)</span>
              </div>
              <div className="flex flex-col gap-2">
                {(medico.empresas ?? [empresa ? { empresaId: medico.empresaId ?? '', cnpj: '', razaoSocial: empresa.razaoSocial, createdAt: '' } as VinculoEmpresa : null]).filter(Boolean).map(v => (
                  <div key={(v as VinculoEmpresa).empresaId}
                    className="flex items-center justify-between px-3 py-2.5 rounded-lg bg-ds-input border border-ds-border">
                    <div className="flex items-center gap-3 min-w-0">
                      <Building2 size={15} className="text-primary shrink-0" />
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-ds-text truncate">{(v as VinculoEmpresa).razaoSocial}</p>
                        {(v as VinculoEmpresa).cnpj && (
                          <p className="text-xs text-ds-light">{(v as VinculoEmpresa).cnpj}</p>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      {(v as VinculoEmpresa).statusSocietario && (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-green-50 text-green-700 border border-green-200">
                          {(v as VinculoEmpresa).statusSocietario}
                        </span>
                      )}
                      {isGestao && (medico.empresas ?? []).length > 1 && (
                        <button
                          onClick={() => handleRemoverVinculo((v as VinculoEmpresa).empresaId)}
                          disabled={removingVinculoId === (v as VinculoEmpresa).empresaId}
                          className="p-1 rounded text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors disabled:opacity-50"
                          title="Remover vínculo"
                        >
                          <XCircle size={15} />
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              {isGestao && (
                <div className="mt-3 flex gap-2">
                  <select
                    value={addEmpresaId}
                    onChange={e => setAddEmpresaId(e.target.value)}
                    className="flex-1 text-sm border border-ds-border rounded-lg px-3 py-1.5 bg-white text-ds-text focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
                  >
                    <option value="">Adicionar empresa...</option>
                    {todasEmpresas
                      .filter(e => !(medico.empresas ?? []).some(v => v.empresaId === e.id))
                      .map(e => (
                        <option key={e.id} value={e.id}>{e.razaoSocial}</option>
                      ))
                    }
                  </select>
                  <Button
                    size="sm"
                    onClick={handleAdicionarVinculo}
                    disabled={!addEmpresaId || addingVinculo}
                  >
                    {addingVinculo ? 'Adicionando...' : 'Adicionar'}
                  </Button>
                </div>
              )}
              {vinculoError && <p className="mt-2 text-xs text-red-600">{vinculoError}</p>}
            </div>

            {/* Tomadores Associados */}
            <div className="mt-6 pt-6 border-t border-ds-border">
              <div className="flex items-center justify-between mb-3">
                <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide">Tomadores Associados</p>
                <span className="text-xs text-ds-light">{tomadoresAlocados.length} tomador(es)</span>
              </div>
              <div className="flex flex-col gap-2">
                {tomadoresAlocados.map(t => (
                  <div key={t.id}
                    className="flex items-center justify-between px-3 py-2.5 rounded-lg bg-ds-input border border-ds-border">
                    <div className="flex items-center gap-3 min-w-0">
                      <Hospital size={15} className="text-primary shrink-0" />
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-ds-text truncate">{t.razaoSocialNome}</p>
                        <p className="text-xs text-ds-light">{formatDocumentoTomador(t.tipo, t.cnpjCpf)}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      {canEdit && (
                        <button
                          onClick={() => handleRemoverTomador(t.id)}
                          disabled={removingTomadorId === t.id}
                          className="p-1 rounded text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors disabled:opacity-50"
                          title="Remover tomador"
                        >
                          <XCircle size={15} />
                        </button>
                      )}
                    </div>
                  </div>
                ))}
                {tomadoresAlocados.length === 0 && (
                  <p className="text-sm text-ds-light">Nenhum tomador associado.</p>
                )}
              </div>

              {canEdit && (
                <div className="mt-3 flex gap-2">
                  <select
                    value={addTomadorId}
                    onChange={e => setAddTomadorId(e.target.value)}
                    className="flex-1 text-sm border border-ds-border rounded-lg px-3 py-1.5 bg-white text-ds-text focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
                  >
                    <option value="">Adicionar tomador...</option>
                    {todosTomadores
                      .filter(t => !tomadoresAlocados.some(a => a.id === t.id))
                      .map(t => (
                        <option key={t.id} value={t.id}>{t.razaoSocialNome}</option>
                      ))
                    }
                  </select>
                  <Button
                    size="sm"
                    onClick={handleAdicionarTomador}
                    disabled={!addTomadorId || addingTomador}
                  >
                    {addingTomador ? 'Adicionando...' : 'Adicionar'}
                  </Button>
                </div>
              )}
              {tomadorError && <p className="mt-2 text-xs text-red-600">{tomadorError}</p>}
            </div>

            {/* Checklist */}
            {medico.checklist && (
              <div className="mt-6 pt-6 border-t border-ds-border">
                <ChecklistEditor
                  checklist={medico.checklist}
                  medicoId={medico.id}
                  canEdit={canEdit}
                  verificadoPor={user?.name ?? user?.email ?? ''}
                  onSaved={updated => setMedico(m => m ? { ...m, checklist: updated } : m)}
                />
              </div>
            )}
          </div>
        )}

        {/* ── Dados Bancários ── */}
        {tab === 'bancarios' && (
          <div className="p-6">
            {!db ? (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <AlertCircle size={28} className="text-ds-light mb-3" />
                <p className="text-sm font-medium text-ds-mid">Dados bancários não cadastrados</p>
                <p className="text-xs text-ds-light mt-1">Configure os dados bancários para recebimento de repasses</p>
              </div>
            ) : db.tipoRecebimento === 'TED' ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                <InfoRow label="Tipo de recebimento" value="TED — Transferência Eletrônica" />
                <InfoRow label="Banco"
                  value={bancos.find(b => b.compe === db.bancoCodigo)
                    ? `${db.bancoCodigo} — ${db.bancoNome}`
                    : db.bancoNome ?? db.bancoCodigo} />
                <InfoRow label="Agência"       value={db.agencia} />
                <InfoRow label="Conta"         value={db.conta} />
                <InfoRow label="Tipo de conta" value={db.tipoConta === 'POUPANCA' ? 'Poupança' : 'Conta Corrente'} />
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                <InfoRow label="Tipo de recebimento" value="PIX" />
                <InfoRow label="Tipo de chave" value={db.tipoPix ?? '—'} />
                <InfoRow label="Chave PIX (mascarada)"
                  value={db.tipoPix && db.chavePix ? maskPixKey(db.tipoPix, db.chavePix) : '—'} />
                {db.cpfsAdicionaisSplit && (
                  <div className="col-span-2">
                    <InfoRow label="CPFs para split" value={db.cpfsAdicionaisSplit} />
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* ── Documentos ── */}
        {tab === 'documentos' && (
          <div className="p-6">
            {(!medico.documentos || medico.documentos.length === 0) ? (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <FileText size={28} className="text-ds-light mb-3" />
                <p className="text-sm font-medium text-ds-mid">Nenhum documento enviado</p>
                <button
                  onClick={() => setShowDocs(true)}
                  className="mt-3 text-xs text-primary hover:underline"
                >
                  Gerenciar documentos
                </button>
              </div>
            ) : (
              <>
                <div className="divide-y divide-ds-border border border-ds-border rounded-xl overflow-hidden">
                  {medico.documentos.map(doc => {
                    const { label, Icon } = DOC_TIPO_INFO[doc.tipo] ?? { label: doc.tipo, Icon: FileText }
                    const statusCfg = DOC_STATUS_CONFIG[doc.statusValidacao]
                    return (
                      <div key={doc.id} className="flex items-center gap-3 px-4 py-3">
                        <div className="w-7 h-7 rounded-lg bg-primary-50 flex items-center justify-center shrink-0">
                          <Icon size={14} className="text-primary" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-semibold text-ds-text">{label}</p>
                          <p className="text-[11px] text-ds-light truncate">{doc.nomeArquivo}</p>
                        </div>
                        <span className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold shrink-0 ${statusCfg.cls}`}>
                          <statusCfg.Icon size={11} />
                          {statusCfg.label}
                        </span>
                        <p className="text-[11px] text-ds-light shrink-0 hidden sm:block">
                          {formatDate(doc.createdAt)}
                        </p>
                      </div>
                    )
                  })}
                </div>
                <div className="mt-4 flex justify-end">
                  <Button size="sm" variant="outline" onClick={() => setShowDocs(true)}>
                    Gerenciar documentos
                  </Button>
                </div>
              </>
            )}
          </div>
        )}

        {/* ── Onboarding ── */}
        {tab === 'onboarding' && (
          <div className="p-6 flex flex-col gap-6">

            {/* Convite por e-mail */}
            <div className="border border-ds-border rounded-xl p-5">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-8 h-8 rounded-lg bg-blue-50 flex items-center justify-center">
                  <Mail size={16} className="text-blue-600" />
                </div>
                <div>
                  <p className="text-sm font-bold text-ds-text">Convite por E-mail</p>
                  <p className="text-xs text-ds-light">Link de autoatendimento enviado ao médico</p>
                </div>
              </div>
              {medico.ultimoConvite ? (
                <div className="flex flex-wrap items-center gap-3">
                  <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                    medico.ultimoConvite.status === 'ENVIADO' ? 'bg-amber-50 text-amber-700 border border-amber-200'
                    : medico.ultimoConvite.status === 'UTILIZADO' ? 'bg-green-50 text-green-700 border border-green-200'
                    : 'bg-gray-100 text-gray-500 border border-gray-200'
                  }`}>
                    {medico.ultimoConvite.status}
                  </span>
                  {medico.ultimoConvite.emailDestino && (
                    <span className="text-xs text-ds-mid">{medico.ultimoConvite.emailDestino}</span>
                  )}
                  {medico.ultimoConvite.enviadoEm && (
                    <span className="text-xs text-ds-light">Enviado {formatDateTime(medico.ultimoConvite.enviadoEm)}</span>
                  )}
                  {medico.ultimoConvite.expiraEm && (
                    <span className="text-xs text-ds-light">· Expira {formatDateTime(medico.ultimoConvite.expiraEm)}</span>
                  )}
                </div>
              ) : (
                <p className="text-xs text-ds-light mb-3">Nenhum convite enviado ainda.</p>
              )}
              {canEdit && (
                <div className="mt-3">
                  <Button
                    size="sm"
                    onClick={handleEnviarConvite}
                    disabled={enviandoConvite || !medico.email}
                  >
                    <Mail size={13} />
                    {enviandoConvite ? 'Enviando...' : medico.ultimoConvite ? 'Reenviar Convite' : 'Enviar Convite'}
                  </Button>
                  {!medico.email && (
                    <p className="text-xs text-amber-600 mt-1.5">Cadastre um e-mail no médico para enviar o convite.</p>
                  )}
                </div>
              )}
            </div>

            {/* Contrato Clicksign */}
            <div className="border border-ds-border rounded-xl p-5">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-8 h-8 rounded-lg bg-violet-50 flex items-center justify-center">
                  <Send size={16} className="text-violet-600" />
                </div>
                <div>
                  <p className="text-sm font-bold text-ds-text">Contrato de Adesão (Clicksign)</p>
                  <p className="text-xs text-ds-light">Assinatura eletrônica do contrato médico</p>
                </div>
              </div>
              {medico.contratoAssinatura ? (
                <div className="flex flex-col gap-2">
                  <div className="flex flex-wrap items-center gap-3">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${CONTRATO_CONFIG[medico.contratoAssinatura.status].cls}`}>
                      {CONTRATO_CONFIG[medico.contratoAssinatura.status].label}
                    </span>
                    {medico.contratoAssinatura.enviadoEm && (
                      <span className="text-xs text-ds-light">Enviado {formatDateTime(medico.contratoAssinatura.enviadoEm)}</span>
                    )}
                    {medico.contratoAssinatura.assinadoEm && (
                      <span className="text-xs text-ds-light">· Assinado {formatDateTime(medico.contratoAssinatura.assinadoEm)}</span>
                    )}
                  </div>
                  {medico.contratoAssinatura.linkAssinatura && medico.contratoAssinatura.status !== 'ASSINADO' && (
                    <a
                      href={medico.contratoAssinatura.linkAssinatura}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-xs text-primary hover:underline flex items-center gap-1 w-fit"
                    >
                      <Send size={11} /> Abrir link de assinatura
                    </a>
                  )}
                </div>
              ) : (
                <p className="text-xs text-ds-light mb-3">Contrato ainda não enviado ao Clicksign.</p>
              )}
              {canEdit && medico.contratoAssinatura?.status !== 'ASSINADO' && (
                <div className="mt-3">
                  <Button
                    size="sm"
                    onClick={handleEnviarContrato}
                    disabled={enviandoContrato}
                  >
                    <Send size={13} />
                    {enviandoContrato ? 'Enviando...' : medico.contratoAssinatura ? 'Reenviar ao Clicksign' : 'Enviar ao Clicksign'}
                  </Button>
                </div>
              )}
            </div>

            {/* Junta Comercial */}
            <div className="border border-ds-border rounded-xl p-5">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-8 h-8 rounded-lg bg-amber-50 flex items-center justify-center">
                  <Landmark size={16} className="text-amber-700" />
                </div>
                <div>
                  <p className="text-sm font-bold text-ds-text">Junta Comercial</p>
                  <p className="text-xs text-ds-light">Status do registro societário</p>
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-3 mb-4">
                <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${JUNTA_CONFIG[medico.statusJuntaComercial ?? 'AGUARDANDO'].cls}`}>
                  {JUNTA_CONFIG[medico.statusJuntaComercial ?? 'AGUARDANDO'].label}
                </span>
              </div>
              {canEdit && (
                <div className="flex flex-col gap-2">
                  <p className="text-xs font-semibold text-ds-mid">Atualizar status:</p>
                  <div className="flex flex-wrap gap-2">
                    {(['AGUARDANDO', 'EM_ANALISE', 'APROVADO', 'RECUSADO'] as StatusJuntaComercial[]).map(s => (
                      <button
                        key={s}
                        onClick={() => setNovoStatusJunta(novoStatusJunta === s ? '' : s)}
                        className={[
                          'px-3 py-1 rounded-full text-xs font-semibold border transition-colors',
                          novoStatusJunta === s
                            ? JUNTA_CONFIG[s].cls + ' ring-2 ring-offset-1 ring-primary'
                            : 'border-ds-border text-ds-mid hover:border-primary hover:text-primary'
                        ].join(' ')}
                      >
                        {JUNTA_CONFIG[s].label}
                      </button>
                    ))}
                  </div>
                  {novoStatusJunta && (
                    <div className="flex flex-col gap-2 mt-1">
                      <input
                        type="text"
                        placeholder="Observação (opcional)"
                        value={obsJunta}
                        onChange={e => setObsJunta(e.target.value)}
                        className="text-sm border border-ds-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary"
                      />
                      <div className="flex gap-2">
                        <Button size="sm" onClick={handleAtualizarJunta} disabled={atualizandoJunta}>
                          {atualizandoJunta ? 'Salvando...' : 'Confirmar'}
                        </Button>
                        <Button size="sm" variant="outline" onClick={() => { setNovoStatusJunta(''); setObsJunta('') }}>
                          Cancelar
                        </Button>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Resumo de requisitos */}
            <div className="border border-ds-border rounded-xl p-5 bg-ds-surface">
              <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide mb-3">Requisitos para Ativação</p>
              {[
                { label: 'Checklist de conduta completo',   ok: medico.checklist?.completo ?? false },
                { label: 'Documentos enviados aprovados',
                  ok: (medico.documentos ?? []).length > 0 &&
                      (medico.documentos ?? []).every(d => d.statusValidacao === 'APROVADO')
                },
                { label: 'Contrato Clicksign assinado',     ok: medico.contratoAssinatura?.status === 'ASSINADO' },
                { label: 'Junta Comercial aprovada',         ok: medico.statusJuntaComercial === 'APROVADO' },
              ].map(({ label, ok }) => (
                <div key={label} className="flex items-center gap-2 py-1">
                  {ok
                    ? <CheckCircle2 size={14} className="text-green-500 shrink-0" />
                    : <XCircle size={14} className="text-gray-300 shrink-0" />}
                  <span className={`text-sm ${ok ? 'text-ds-text font-medium' : 'text-ds-light'}`}>{label}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── Taxa Pin ── */}
        {tab === 'taxa-pin' && (
          <div className="p-6">
            <div className="mb-6 p-4 bg-primary-50 rounded-xl border border-primary-100">
              <p className="text-xs font-semibold text-ds-light uppercase tracking-wide mb-1">Taxa Pin atual</p>
              <p className="text-3xl font-black text-primary">
                {medico?.taxaPinPct != null
                  ? `${(medico.taxaPinPct * 100).toFixed(2).replace('.', ',')}%`
                  : '15,00%'}
              </p>
              <p className="text-xs text-ds-light mt-1">
                O médico recebe {medico?.taxaPinPct != null
                  ? `${((1 - medico.taxaPinPct) * 100).toFixed(2).replace('.', ',')}%`
                  : '85,00%'} do valor bruto de cada produção
              </p>
            </div>

            {historicoTaxaPin.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <Landmark size={28} className="text-ds-light mb-3" />
                <p className="text-sm font-medium text-ds-mid">Nenhuma renegociação registrada</p>
                <p className="text-xs text-ds-light mt-1">O histórico aparecerá aqui sempre que a taxa for alterada</p>
              </div>
            ) : (
              <div>
                <p className="text-xs font-semibold text-ds-light uppercase tracking-wide mb-4">
                  Histórico de renegociações
                </p>
                <div className="flex flex-col gap-3">
                  {historicoTaxaPin.map(h => (
                    <div key={h.id} className="flex items-center gap-4 p-4 rounded-xl border border-ds-border bg-white">
                      <div className="flex items-center gap-2 shrink-0">
                        <span className="text-sm font-semibold text-ds-mid line-through">
                          {(h.taxaAnterior * 100).toFixed(2).replace('.', ',')}%
                        </span>
                        <span className="text-ds-light">→</span>
                        <span className="text-sm font-black text-primary">
                          {(h.taxaNova * 100).toFixed(2).replace('.', ',')}%
                        </span>
                      </div>
                      <div className="flex-1 min-w-0">
                        {h.alteradoPor && (
                          <p className="text-xs text-ds-mid truncate flex items-center gap-1">
                            <User size={10} /> {h.alteradoPor}
                          </p>
                        )}
                        <p className="text-[11px] text-ds-light">{formatDateTime(h.alteradoEm)}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* ── Histórico ── */}
        {tab === 'historico' && (
          <div className="p-6">
            {historico.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <Clock size={28} className="text-ds-light mb-3" />
                <p className="text-sm font-medium text-ds-mid">Nenhum registro no histórico</p>
              </div>
            ) : (
              <div className="relative pl-6">
                {/* Linha vertical */}
                <div className="absolute left-[11px] top-1 bottom-1 w-px bg-ds-border" />

                <div className="flex flex-col gap-4">
                  {historico.map((h, i) => {
                    const cfg = ACAO_CONFIG[h.tipoAcao] ?? { label: h.tipoAcao, cls: 'bg-gray-100 text-gray-500', Icon: Clock }
                    const { Icon } = cfg
                    return (
                      <div key={h.id} className="relative flex gap-4">
                        {/* Dot */}
                        <div className={`absolute -left-6 w-5 h-5 rounded-full flex items-center justify-center shrink-0 ${cfg.cls}`}>
                          <Icon size={11} />
                        </div>
                        {/* Content */}
                        <div className={`flex-1 rounded-xl border border-ds-border p-3 ${i === 0 ? 'bg-primary-50/20' : 'bg-white'}`}>
                          <div className="flex items-start justify-between gap-2">
                            <div>
                              <p className="text-sm font-semibold text-ds-text">{cfg.label}</p>
                              <p className="text-xs text-ds-mid mt-0.5">{h.descricao}</p>
                            </div>
                            <p className="text-[11px] text-ds-light shrink-0 text-right">
                              {formatDateTime(h.createdAt)}
                            </p>
                          </div>
                          {h.usuario && (
                            <p className="text-[11px] text-ds-light mt-1.5 flex items-center gap-1">
                              <User size={10} /> {h.usuario}
                            </p>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Modals */}
      {showEdit && (
        <MedicoWizardModal
          medico={medico}
          onClose={() => setShowEdit(false)}
          onSaved={updated => { setMedico(updated); setShowEdit(false) }}
        />
      )}
      {inativando && (
        <MedicoInativarModal
          medico={medico}
          onClose={() => setInativando(false)}
          onInativado={updated => { setMedico(updated); setInativando(false) }}
        />
      )}
      {showDocs && (
        <DocumentosModal
          medico={medico}
          onClose={() => setShowDocs(false)}
          onDocumentosChange={async () => {
            const updated = await medicosApi.buscarPorId(medico.id).catch(() => medico)
            setMedico(updated)
          }}
        />
      )}
    </div>
  )
}
