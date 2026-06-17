import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft, Pencil, UserCheck, UserX, FileText,
  User, Building2, CreditCard, Clock, CheckCircle2,
  XCircle, AlertCircle,
  Award, GraduationCap, Home, BookOpen,
} from 'lucide-react'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import {
  Medico, StatusMedico, TipoDocumentoMedico,
  StatusValidacaoDocumento, HistoricoMedico,
  medicosApi,
} from '../api/medicosApi'
import { empresasApi, Empresa } from '../api/empresasApi'
import { MedicoWizardModal, maskPixKey } from '../components/MedicoWizardModal'
import { MedicoInativarModal } from '../components/MedicoInativarModal'
import { DocumentosModal } from '../components/DocumentosModal'
import { formatCpf } from '../utils/cpf'
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
  CADASTRO:                   { label: 'Cadastro',             cls: 'bg-primary-50 text-primary',     Icon: User },
  ATIVACAO:                   { label: 'Ativação',             cls: 'bg-green-50 text-green-600',     Icon: CheckCircle2 },
  INATIVACAO:                 { label: 'Inativação',           cls: 'bg-gray-100 text-gray-500',      Icon: XCircle },
  ATUALIZACAO_DADOS:          { label: 'Dados atualizados',    cls: 'bg-blue-50 text-blue-600',       Icon: Pencil },
  ATUALIZACAO_DADOS_BANCARIOS:{ label: 'Dados bancários',      cls: 'bg-purple-50 text-purple-600',   Icon: CreditCard },
  UPLOAD_DOCUMENTO:           { label: 'Documento enviado',    cls: 'bg-amber-50 text-amber-600',     Icon: FileText },
  VALIDACAO_DOCUMENTO:        { label: 'Documento validado',   cls: 'bg-teal-50 text-teal-600',       Icon: CheckCircle2 },
  EXCLUSAO_DOCUMENTO:         { label: 'Documento removido',   cls: 'bg-red-50 text-red-500',         Icon: XCircle },
  ATUALIZACAO_CHECKLIST:      { label: 'Checklist',            cls: 'bg-indigo-50 text-indigo-600',   Icon: BookOpen },
}

// ─── Documento row (inline no perfil) ────────────────────────────────────────

const DOC_TIPO_INFO: Record<TipoDocumentoMedico, { label: string; Icon: React.ElementType }> = {
  CRM:        { label: 'Registro CRM',             Icon: Award },
  DIPLOMA:    { label: 'Diploma Médico',            Icon: GraduationCap },
  IDENTIDADE: { label: 'Identidade (CNH ou RG)',    Icon: CreditCard },
  RESIDENCIA: { label: 'Comprovante de Residência', Icon: Home },
  CONTRATO:   { label: 'Contrato',                  Icon: FileText },
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

function InfoRow({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex flex-col gap-0.5">
      <p className="text-[11px] font-semibold text-ds-light uppercase tracking-wide">{label}</p>
      <p className="text-sm text-ds-text font-medium">{value || '—'}</p>
    </div>
  )
}

// ─── Tabs ─────────────────────────────────────────────────────────────────────

type Tab = 'dados' | 'bancarios' | 'documentos' | 'historico'

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
  const [historico, setHistorico] = useState<HistoricoMedico[]>([])
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState<string | null>(null)
  const [tab,       setTab]       = useState<Tab>('dados')

  const [showEdit,    setShowEdit]    = useState(false)
  const [inativando,  setInativando]  = useState(false)
  const [showDocs,    setShowDocs]    = useState(false)
  const [activating,  setActivating]  = useState(false)

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
            .then(p => setEmpresa(p.content.find(e => e.id === m.empresaId) ?? null))
            .catch(() => {})
        }
      })
      .catch(() => setError('Erro ao carregar dados do médico'))
      .finally(() => setLoading(false))
  }, [id])

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
                {empresa && (
                  <span className="text-sm text-ds-mid flex items-center gap-1">
                    · <Building2 size={12} /> {empresa.razaoSocial}
                  </span>
                )}
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
            { key: 'dados',      label: 'Dados Pessoais',   Icon: User },
            { key: 'bancarios',  label: 'Dados Bancários',  Icon: CreditCard },
            { key: 'documentos', label: 'Documentos',        Icon: FileText },
            { key: 'historico',  label: 'Histórico',         Icon: Clock },
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
              <InfoRow label="Nome completo"   value={medico.nome} />
              <InfoRow label="CPF"             value={medico.cpf ? formatCpf(medico.cpf) : '—'} />
              <InfoRow label="CRM"             value={`${medico.crm} / ${medico.crmUf.trim()}`} />
              <InfoRow label="Especialidade"   value={medico.especialidade} />
              <InfoRow label="E-mail"          value={medico.email} />
              <InfoRow label="Telefone"        value={medico.telefone} />
              <InfoRow label="Empresa vinculada" value={empresa?.razaoSocial} />
              <InfoRow label="Status"          value={STATUS_LABELS[medico.status]} />
              <InfoRow label="Cadastrado em"   value={formatDate(medico.createdAt)} />
              <InfoRow label="Última atualização" value={formatDate(medico.updatedAt)} />
            </div>

            {/* Checklist */}
            {medico.checklist && (
              <div className="mt-6 pt-6 border-t border-ds-border">
                <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide mb-3">Checklist de Conduta</p>
                <div className="flex flex-col gap-2">
                  {[
                    { label: 'Número do conselho verificado', ok: medico.checklist.numeroConselhoVerificado },
                    { label: 'Registros disciplinares verificados', ok: medico.checklist.registrosDisciplinares },
                    { label: 'Processos médicos verificados', ok: medico.checklist.processosMedicos },
                  ].map(({ label, ok }) => (
                    <div key={label} className="flex items-center gap-2">
                      {ok
                        ? <CheckCircle2 size={15} className="text-green-500 shrink-0" />
                        : <XCircle size={15} className="text-gray-300 shrink-0" />}
                      <span className={`text-sm ${ok ? 'text-ds-text' : 'text-ds-light'}`}>{label}</span>
                    </div>
                  ))}
                  {medico.checklist.verificadoPor && (
                    <p className="text-xs text-ds-light mt-1">
                      Verificado por {medico.checklist.verificadoPor}
                      {medico.checklist.verificadoEm ? ` em ${formatDateTime(medico.checklist.verificadoEm)}` : ''}
                    </p>
                  )}
                </div>
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
