import { useEffect, useState, useRef } from 'react'
import {
  CheckCircle2, XCircle, FileText, ChevronRight,
  RefreshCw, User, Award, GraduationCap, Home, CreditCard,
  Mail, Send, Landmark, UserCheck, AlertCircle, ExternalLink,
  BookOpen,
} from 'lucide-react'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import {
  Medico, StatusJuntaComercial, StatusContrato,
  TipoDocumentoMedico, DocumentoMedico,
  medicosApi,
} from '../api/medicosApi'
import { useAuth } from '../auth/useAuth'

// ─── Tipos de estágio ────────────────────────────────────────────────────────

type Stage = 'docs' | 'contrato' | 'junta' | 'pronto'
type FilterKey = 'all' | Stage

function computeStage(m: Medico): Stage {
  const checklistOk = m.checklist?.completo ?? false
  const docsOk = (m.documentos?.length ?? 0) > 0 &&
    (m.documentos ?? []).every(d => d.statusValidacao === 'APROVADO')
  if (!checklistOk || !docsOk) return 'docs'
  if (m.contratoAssinatura?.status !== 'ASSINADO') return 'contrato'
  if (m.statusJuntaComercial !== 'APROVADO') return 'junta'
  return 'pronto'
}

// ─── Configs de estágio ───────────────────────────────────────────────────────

const STAGE_CONFIG: Record<Stage, { label: string; cls: string; dot: string }> = {
  docs:     { label: 'Documentação', cls: 'bg-amber-50 text-amber-700 border border-amber-200',  dot: 'bg-amber-400' },
  contrato: { label: 'Contrato',     cls: 'bg-violet-50 text-violet-700 border border-violet-200', dot: 'bg-violet-400' },
  junta:    { label: 'Junta',        cls: 'bg-blue-50 text-blue-700 border border-blue-200',     dot: 'bg-blue-400' },
  pronto:   { label: 'Pronto ✓',    cls: 'bg-green-50 text-green-700 border border-green-200',  dot: 'bg-green-400' },
}

const DOC_TIPO_INFO: Record<TipoDocumentoMedico, { label: string; Icon: React.ElementType }> = {
  CRM:        { label: 'Registro CRM',             Icon: Award },
  DIPLOMA:    { label: 'Diploma Médico',            Icon: GraduationCap },
  IDENTIDADE: { label: 'Identidade (CNH ou RG)',    Icon: CreditCard },
  RESIDENCIA: { label: 'Comprovante de Residência', Icon: Home },
  CONTRATO:   { label: 'Contrato',                  Icon: FileText },
}

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

// ─── Helpers ──────────────────────────────────────────────────────────────────

function daysSince(iso: string) {
  const diff = Date.now() - new Date(iso).getTime()
  return Math.floor(diff / 86400000)
}

// ─── Componente de documento (com aprovar/reprovar inline) ────────────────────

function DocRow({
  doc,
  medicoId,
  onValidado,
}: {
  doc: DocumentoMedico
  medicoId: string
  onValidado: (updated: DocumentoMedico) => void
}) {
  const [rejecting, setRejecting]   = useState(false)
  const [motivo,    setMotivo]      = useState('')
  const [loading,   setLoading]     = useState(false)
  const [docUrl,    setDocUrl]      = useState<string | null>(null)
  const [error,     setError]       = useState<string | null>(null)

  const { label, Icon } = DOC_TIPO_INFO[doc.tipo] ?? { label: doc.tipo, Icon: FileText }

  async function handleAprovar() {
    setLoading(true)
    setError(null)
    try {
      const updated = await medicosApi.validarDocumento(medicoId, doc.id, {
        statusValidacao: 'APROVADO',
      })
      onValidado(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao aprovar')
    } finally {
      setLoading(false)
    }
  }

  async function handleReprovar() {
    if (!motivo.trim()) return
    setLoading(true)
    setError(null)
    try {
      const updated = await medicosApi.validarDocumento(medicoId, doc.id, {
        statusValidacao: 'REPROVADO',
        motivoReprovacao: motivo.trim(),
      })
      onValidado(updated)
      setRejecting(false)
      setMotivo('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao reprovar')
    } finally {
      setLoading(false)
    }
  }

  async function handleOpenDoc() {
    if (docUrl) { window.open(docUrl, '_blank'); return }
    try {
      const url = await medicosApi.getDocumentoUrl(medicoId, doc.id)
      setDocUrl(url)
      window.open(url, '_blank')
    } catch {
      setError('Não foi possível obter o link do documento')
    }
  }

  const statusColor =
    doc.statusValidacao === 'APROVADO'  ? 'bg-green-50 text-green-600' :
    doc.statusValidacao === 'REPROVADO' ? 'bg-red-50 text-red-600' :
    'bg-gray-100 text-gray-500'

  return (
    <div className="border border-ds-border rounded-xl overflow-hidden">
      <div className="flex items-center gap-3 px-4 py-3">
        <div className="w-7 h-7 rounded-lg bg-primary-50 flex items-center justify-center shrink-0">
          <Icon size={14} className="text-primary" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-ds-text">{label}</p>
          <button
            onClick={handleOpenDoc}
            className="text-[11px] text-primary hover:underline flex items-center gap-0.5 truncate"
          >
            <ExternalLink size={10} /> {doc.nomeArquivo}
          </button>
        </div>
        <span className={`px-2 py-0.5 rounded-full text-[11px] font-semibold shrink-0 ${statusColor}`}>
          {doc.statusValidacao === 'APROVADO'  ? 'Aprovado' :
           doc.statusValidacao === 'REPROVADO' ? 'Reprovado' : 'Aguardando'}
        </span>
        {doc.statusValidacao !== 'APROVADO' && (
          <button
            onClick={handleAprovar}
            disabled={loading}
            className="ml-1 px-2 py-1 rounded-lg text-[11px] font-semibold bg-green-50 text-green-700 hover:bg-green-100 border border-green-200 disabled:opacity-50 shrink-0"
          >
            {loading ? '...' : 'Aprovar'}
          </button>
        )}
        {doc.statusValidacao !== 'REPROVADO' && (
          <button
            onClick={() => setRejecting(r => !r)}
            disabled={loading}
            className="ml-1 px-2 py-1 rounded-lg text-[11px] font-semibold bg-red-50 text-red-600 hover:bg-red-100 border border-red-200 disabled:opacity-50 shrink-0"
          >
            Reprovar
          </button>
        )}
      </div>
      {doc.motivoReprovacao && (
        <p className="px-4 pb-2 text-[11px] text-red-600">Motivo: {doc.motivoReprovacao}</p>
      )}
      {rejecting && (
        <div className="px-4 pb-3 flex gap-2">
          <input
            autoFocus
            type="text"
            placeholder="Motivo da reprovação (obrigatório)"
            value={motivo}
            onChange={e => setMotivo(e.target.value)}
            className="flex-1 text-sm border border-ds-border rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-red-300 focus:border-red-400"
          />
          <button
            onClick={handleReprovar}
            disabled={!motivo.trim() || loading}
            className="px-3 py-1.5 rounded-lg text-sm font-semibold bg-red-500 text-white hover:bg-red-600 disabled:opacity-40"
          >
            {loading ? '...' : 'Confirmar'}
          </button>
          <button
            onClick={() => { setRejecting(false); setMotivo('') }}
            className="px-3 py-1.5 rounded-lg text-sm font-semibold border border-ds-border text-ds-mid hover:bg-ds-input"
          >
            Cancelar
          </button>
        </div>
      )}
      {error && <p className="px-4 pb-2 text-[11px] text-red-600">{error}</p>}
    </div>
  )
}

// ─── Painel de detalhe ────────────────────────────────────────────────────────

function DetalhePanel({
  medico,
  onRefresh,
}: {
  medico: Medico
  onRefresh: (updated: Medico) => void
}) {
  const [activating,       setActivating]       = useState(false)
  const [signingContract,  setSigningContract]   = useState(false)
  const [enviandoConvite,  setEnviandoConvite]   = useState(false)
  const [enviandoClicksign, setEnviandoClicksign] = useState(false)
  const [novoStatusJunta, setNovoStatusJunta]  = useState<StatusJuntaComercial | ''>('')
  const [obsJunta,        setObsJunta]         = useState('')
  const [updatingJunta,   setUpdatingJunta]    = useState(false)
  const [error,           setError]            = useState<string | null>(null)
  const [docs,            setDocs]             = useState<DocumentoMedico[]>(medico.documentos ?? [])

  // Sync docs when medico changes (different doctor selected)
  const medicoIdRef = useRef(medico.id)
  if (medicoIdRef.current !== medico.id) {
    medicoIdRef.current = medico.id
    setDocs(medico.documentos ?? [])
    setNovoStatusJunta('')
    setObsJunta('')
    setError(null)
  }

  const checklistOk  = medico.checklist?.completo ?? false
  const docsOk       = docs.length > 0 && docs.every(d => d.statusValidacao === 'APROVADO')
  const contratoOk   = medico.contratoAssinatura?.status === 'ASSINADO'
  const juntaOk      = medico.statusJuntaComercial === 'APROVADO'
  const prontoPraAtivar = checklistOk && docsOk && contratoOk && juntaOk

  async function handleAtivar() {
    setActivating(true)
    setError(null)
    try {
      const updated = await medicosApi.ativar(medico.id)
      onRefresh(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao ativar médico')
    } finally {
      setActivating(false)
    }
  }

  async function handleAssinarContrato() {
    setSigningContract(true)
    setError(null)
    try {
      await medicosApi.assinarContratoManual(medico.id)
      const updated = await medicosApi.buscarPorId(medico.id)
      onRefresh(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao marcar contrato')
    } finally {
      setSigningContract(false)
    }
  }

  async function handleEnviarConvite() {
    setEnviandoConvite(true)
    setError(null)
    try {
      await medicosApi.enviarConvite(medico.id)
      const updated = await medicosApi.buscarPorId(medico.id)
      onRefresh(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao enviar convite')
    } finally {
      setEnviandoConvite(false)
    }
  }

  async function handleAtualizarJunta() {
    if (!novoStatusJunta) return
    setUpdatingJunta(true)
    setError(null)
    try {
      const updated = await medicosApi.atualizarJuntaComercial(medico.id, novoStatusJunta, obsJunta || undefined)
      setNovoStatusJunta('')
      setObsJunta('')
      onRefresh(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao atualizar Junta')
    } finally {
      setUpdatingJunta(false)
    }
  }

  function handleDocValidado(updated: DocumentoMedico) {
    setDocs(prev => prev.map(d => d.id === updated.id ? updated : d))
  }

  return (
    <div className="flex flex-col gap-5 overflow-y-auto pr-1">

      {/* Cabeçalho do médico */}
      <div className="flex items-start gap-3">
        <div className="w-10 h-10 rounded-xl bg-primary-50 flex items-center justify-center shrink-0">
          <span className="text-base font-black text-primary">
            {medico.nome.trim().split(/\s+/).filter(w => w.length > 1).slice(0, 2)
              .map(w => w[0].toUpperCase()).join('') || medico.nome.slice(0, 2).toUpperCase()}
          </span>
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-base font-bold text-ds-text truncate">{medico.nome}</p>
          <p className="text-xs text-ds-mid">CRM {medico.crm}/{medico.crmUf.trim()} · {medico.especialidade ?? '—'}</p>
          {medico.email && <p className="text-[11px] text-ds-light mt-0.5">{medico.email}</p>}
        </div>
      </div>

      {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

      {/* Requisitos */}
      <div className="border border-ds-border rounded-xl p-4 bg-ds-surface">
        <p className="text-[11px] font-semibold text-ds-mid uppercase tracking-wide mb-2">Requisitos para Ativação</p>
        {[
          { label: 'Checklist de conduta completo', ok: checklistOk },
          { label: 'Documentos enviados aprovados', ok: docsOk },
          { label: 'Contrato Clicksign assinado',   ok: contratoOk },
          { label: 'Junta Comercial aprovada',      ok: juntaOk },
        ].map(({ label, ok }) => (
          <div key={label} className="flex items-center gap-2 py-0.5">
            {ok
              ? <CheckCircle2 size={13} className="text-green-500 shrink-0" />
              : <XCircle size={13} className="text-gray-300 shrink-0" />}
            <span className={`text-xs ${ok ? 'text-ds-text font-medium' : 'text-ds-light'}`}>{label}</span>
          </div>
        ))}
      </div>

      {/* Documentos */}
      <div>
        <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide mb-2">
          Documentos ({docs.length})
        </p>
        {docs.length === 0 ? (
          <div className="flex items-center gap-2 py-4 text-ds-light">
            <AlertCircle size={14} />
            <span className="text-xs">Nenhum documento enviado</span>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {docs.map(doc => (
              <DocRow
                key={doc.id}
                doc={doc}
                medicoId={medico.id}
                onValidado={handleDocValidado}
              />
            ))}
          </div>
        )}
      </div>

      {/* Checklist */}
      {medico.checklist && (
        <div className="border border-ds-border rounded-xl p-4">
          <div className="flex items-center gap-2 mb-2">
            <BookOpen size={14} className="text-indigo-500" />
            <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide">Checklist de Conduta</p>
          </div>
          <div className="flex flex-col gap-1.5">
            {[
              { label: 'Número do conselho', ok: medico.checklist.numeroConselhoVerificado },
              { label: 'Registros disciplinares', ok: medico.checklist.registrosDisciplinares },
              { label: 'Processos médicos', ok: medico.checklist.processosMedicos },
            ].map(({ label, ok }) => (
              <div key={label} className="flex items-center gap-2">
                {ok
                  ? <CheckCircle2 size={12} className="text-green-500 shrink-0" />
                  : <XCircle size={12} className="text-gray-300 shrink-0" />}
                <span className={`text-xs ${ok ? 'text-ds-text' : 'text-ds-light'}`}>{label}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Convite */}
      <div className="border border-ds-border rounded-xl p-4">
        <div className="flex items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-2">
            <Mail size={14} className="text-blue-500" />
            <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide">Convite por E-mail</p>
          </div>
          {medico.ultimoConvite && (
            <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
              medico.ultimoConvite.status === 'ENVIADO'   ? 'bg-amber-50 text-amber-700 border border-amber-200' :
              medico.ultimoConvite.status === 'UTILIZADO' ? 'bg-green-50 text-green-700 border border-green-200' :
              'bg-gray-100 text-gray-500 border border-gray-200'
            }`}>
              {medico.ultimoConvite.status}
            </span>
          )}
        </div>
        <Button
          size="sm"
          variant="outline"
          onClick={handleEnviarConvite}
          disabled={enviandoConvite || !medico.email}
        >
          <Mail size={12} />
          {enviandoConvite ? 'Enviando...' : medico.ultimoConvite ? 'Reenviar' : 'Enviar Convite'}
        </Button>
      </div>

      {/* Contrato */}
      <div className="border border-ds-border rounded-xl p-4">
        <div className="flex items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-2">
            <Send size={14} className="text-violet-500" />
            <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide">Contrato Clicksign</p>
          </div>
          {medico.contratoAssinatura && (
            <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${CONTRATO_CONFIG[medico.contratoAssinatura.status].cls}`}>
              {CONTRATO_CONFIG[medico.contratoAssinatura.status].label}
            </span>
          )}
        </div>
        <div className="flex gap-2 flex-wrap">
          {medico.contratoAssinatura?.status !== 'ASSINADO' && (
            <Button
              size="sm"
              variant="outline"
              disabled={enviandoClicksign}
              onClick={async () => {
                setEnviandoClicksign(true)
                setError(null)
                try {
                  await medicosApi.enviarContrato(medico.id)
                  const updated = await medicosApi.buscarPorId(medico.id)
                  onRefresh(updated)
                } catch (e) {
                  setError(e instanceof Error ? e.message : 'Erro ao enviar para o Clicksign')
                } finally {
                  setEnviandoClicksign(false)
                }
              }}
            >
              <Send size={12} /> {enviandoClicksign ? 'Enviando...' : 'Enviar Clicksign'}
            </Button>
          )}
          {medico.contratoAssinatura && medico.contratoAssinatura.status !== 'ASSINADO' && (
            <Button
              size="sm"
              variant="outline"
              onClick={handleAssinarContrato}
              disabled={signingContract}
            >
              <CheckCircle2 size={12} />
              {signingContract ? 'Salvando...' : 'Marcar como assinado'}
            </Button>
          )}
          {!medico.contratoAssinatura && (
            <p className="text-xs text-ds-light">Nenhum contrato enviado ainda.</p>
          )}
        </div>
        {medico.contratoAssinatura?.linkAssinatura && medico.contratoAssinatura.status !== 'ASSINADO' && (
          <a
            href={medico.contratoAssinatura.linkAssinatura}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-2 text-[11px] text-primary hover:underline flex items-center gap-1 w-fit"
          >
            <ExternalLink size={10} /> Abrir link de assinatura
          </a>
        )}
      </div>

      {/* Junta Comercial */}
      <div className="border border-ds-border rounded-xl p-4">
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Landmark size={14} className="text-amber-600" />
            <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide">Junta Comercial</p>
          </div>
          <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${JUNTA_CONFIG[medico.statusJuntaComercial ?? 'AGUARDANDO'].cls}`}>
            {JUNTA_CONFIG[medico.statusJuntaComercial ?? 'AGUARDANDO'].label}
          </span>
        </div>
        <div className="flex flex-wrap gap-1.5 mb-2">
          {(['AGUARDANDO', 'EM_ANALISE', 'APROVADO', 'RECUSADO'] as StatusJuntaComercial[]).map(s => (
            <button
              key={s}
              onClick={() => setNovoStatusJunta(novoStatusJunta === s ? '' : s)}
              className={[
                'px-2.5 py-1 rounded-full text-[11px] font-semibold border transition-colors',
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
          <div className="flex flex-col gap-2">
            <input
              type="text"
              placeholder="Observação (opcional)"
              value={obsJunta}
              onChange={e => setObsJunta(e.target.value)}
              className="text-sm border border-ds-border rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            <div className="flex gap-2">
              <Button size="sm" onClick={handleAtualizarJunta} disabled={updatingJunta}>
                {updatingJunta ? 'Salvando...' : 'Confirmar'}
              </Button>
              <Button size="sm" variant="outline" onClick={() => { setNovoStatusJunta(''); setObsJunta('') }}>
                Cancelar
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* Ativar médico */}
      <button
        onClick={handleAtivar}
        disabled={!prontoPraAtivar || activating || medico.status === 'ATIVO'}
        className={[
          'w-full py-3 rounded-xl text-sm font-bold flex items-center justify-center gap-2 transition-all',
          medico.status === 'ATIVO'
            ? 'bg-green-50 text-green-700 border border-green-200 cursor-default'
            : prontoPraAtivar
              ? 'bg-green-500 text-white hover:bg-green-600 shadow-md shadow-green-200'
              : 'bg-gray-100 text-gray-400 cursor-not-allowed border border-gray-200',
        ].join(' ')}
      >
        <UserCheck size={16} />
        {medico.status === 'ATIVO'
          ? 'Médico já está ativo'
          : activating
            ? 'Ativando...'
            : prontoPraAtivar
              ? 'Ativar Médico'
              : 'Ativar Médico (requisitos pendentes)'}
      </button>
    </div>
  )
}

// ─── Página principal ─────────────────────────────────────────────────────────

export function AprovacaoOnboardingPage() {
  const { user } = useAuth()
  const userRoles = user?.realm_access?.roles ?? []
  const canAccess = userRoles.includes('operacao') || userRoles.includes('gestao')

  const [medicos,    setMedicos]    = useState<Medico[]>([])
  const [loading,    setLoading]    = useState(true)
  const [error,      setError]      = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [filter,     setFilter]     = useState<FilterKey>('all')

  useEffect(() => { if (canAccess) loadQueue() }, [canAccess])

  function loadQueue() {
    setLoading(true)
    setError(null)
    medicosApi.listarFilaAprovacao()
      .then(data => {
        setMedicos(data)
        if (data.length > 0 && !selectedId) setSelectedId(data[0].id)
      })
      .catch(() => setError('Erro ao carregar fila de onboarding'))
      .finally(() => setLoading(false))
  }

  function handleRefresh(updated: Medico) {
    setMedicos(prev => {
      // se foi ativado (status ATIVO), remove da fila
      if (updated.status === 'ATIVO') {
        const remaining = prev.filter(m => m.id !== updated.id)
        setSelectedId(remaining[0]?.id ?? null)
        return remaining
      }
      return prev.map(m => m.id === updated.id ? updated : m)
    })
  }

  if (!canAccess) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center">
        <AlertCircle size={32} className="text-red-400 mb-3" />
        <p className="text-base font-semibold text-ds-text">Acesso restrito</p>
        <p className="text-sm text-ds-light mt-1">Esta tela é exclusiva para Operação e Gestão.</p>
      </div>
    )
  }

  // Filtragem
  const filtered = filter === 'all'
    ? medicos
    : medicos.filter(m => computeStage(m) === filter)

  const counts: Record<FilterKey, number> = {
    all:      medicos.length,
    docs:     medicos.filter(m => computeStage(m) === 'docs').length,
    contrato: medicos.filter(m => computeStage(m) === 'contrato').length,
    junta:    medicos.filter(m => computeStage(m) === 'junta').length,
    pronto:   medicos.filter(m => computeStage(m) === 'pronto').length,
  }

  const selectedMedico = medicos.find(m => m.id === selectedId) ?? null

  const FILTER_ITEMS: { key: FilterKey; label: string; cls: string }[] = [
    { key: 'all',      label: 'Todos',        cls: 'bg-ds-surface text-ds-mid border border-ds-border' },
    { key: 'docs',     label: 'Documentação', cls: STAGE_CONFIG.docs.cls },
    { key: 'contrato', label: 'Contrato',     cls: STAGE_CONFIG.contrato.cls },
    { key: 'junta',    label: 'Junta',        cls: STAGE_CONFIG.junta.cls },
    { key: 'pronto',   label: 'Pronto ✓',    cls: STAGE_CONFIG.pronto.cls },
  ]

  return (
    <div className="flex flex-col gap-4 h-full">

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-ds-text">Aprovação de Onboarding</h1>
          <p className="text-xs text-ds-light mt-0.5">Fila de médicos aguardando aprovação</p>
        </div>
        <button
          onClick={loadQueue}
          disabled={loading}
          className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium text-ds-mid border border-ds-border hover:bg-ds-input transition-colors disabled:opacity-50"
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          Atualizar
        </button>
      </div>

      {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

      {/* Filtros */}
      <div className="flex flex-wrap gap-2">
        {FILTER_ITEMS.map(({ key, label, cls }) => (
          <button
            key={key}
            onClick={() => setFilter(key)}
            className={[
              'flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold transition-all border',
              filter === key ? cls + ' ring-2 ring-offset-1 ring-primary' : cls,
            ].join(' ')}
          >
            {label}
            <span className="min-w-[18px] h-[18px] bg-white/60 rounded-full text-[10px] font-bold flex items-center justify-center px-1">
              {counts[key]}
            </span>
          </button>
        ))}
      </div>

      {/* Split panel */}
      {loading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : medicos.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <CheckCircle2 size={36} className="text-green-400 mb-3" />
          <p className="text-base font-semibold text-ds-text">Fila vazia!</p>
          <p className="text-sm text-ds-light mt-1">Nenhum médico aguardando aprovação no momento.</p>
        </div>
      ) : (
        <div className="flex gap-4 flex-1 min-h-0">

          {/* Lista esquerda */}
          <div className="w-72 shrink-0 flex flex-col gap-1 overflow-y-auto">
            {filtered.length === 0 ? (
              <p className="text-xs text-ds-light text-center py-8">Nenhum médico neste estágio.</p>
            ) : filtered.map(m => {
              const stage = computeStage(m)
              const cfg   = STAGE_CONFIG[stage]
              const dias  = daysSince(m.createdAt)
              const isSelected = m.id === selectedId
              return (
                <button
                  key={m.id}
                  onClick={() => setSelectedId(m.id)}
                  className={[
                    'w-full text-left rounded-xl border p-3 transition-all',
                    isSelected
                      ? 'border-primary bg-primary-50/40 shadow-sm'
                      : 'border-ds-border bg-white hover:border-primary/40 hover:bg-primary-50/10',
                  ].join(' ')}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-ds-text truncate">{m.nome}</p>
                      <p className="text-[11px] text-ds-mid">CRM {m.crm}/{m.crmUf.trim()}</p>
                    </div>
                    <ChevronRight size={14} className={`shrink-0 mt-0.5 ${isSelected ? 'text-primary' : 'text-ds-light'}`} />
                  </div>
                  <div className="flex items-center justify-between mt-2">
                    <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${cfg.cls}`}>
                      {cfg.label}
                    </span>
                    <span className="text-[10px] text-ds-light">
                      {dias === 0 ? 'hoje' : `${dias}d atrás`}
                    </span>
                  </div>
                </button>
              )
            })}
          </div>

          {/* Detalhe direita */}
          <div className="flex-1 bg-white border border-ds-border rounded-xl p-5 overflow-y-auto">
            {!selectedMedico ? (
              <div className="flex flex-col items-center justify-center h-full text-center">
                <User size={28} className="text-ds-light mb-3" />
                <p className="text-sm text-ds-mid">Selecione um médico para ver o detalhe</p>
              </div>
            ) : (
              <DetalhePanel
                key={selectedMedico.id}
                medico={selectedMedico}
                onRefresh={handleRefresh}
              />
            )}
          </div>
        </div>
      )}
    </div>
  )
}
