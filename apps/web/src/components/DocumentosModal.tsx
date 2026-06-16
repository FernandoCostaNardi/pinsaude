import { useEffect, useRef, useState } from 'react'
import {
  Award, GraduationCap, CreditCard, Home, FileText,
  Upload, CheckCircle2, XCircle, Clock, Eye, RotateCcw,
} from 'lucide-react'
import { Modal, Button, Alert, Spinner } from '@pinsaude/ui'
import {
  Medico, TipoDocumentoMedico, StatusValidacaoDocumento,
  DocumentoMedico, medicosApi,
} from '../api/medicosApi'
import { useAuth } from '../auth/useAuth'

// ─── Constantes ───────────────────────────────────────────────────────────────

const TODOS_TIPOS: TipoDocumentoMedico[] = ['CRM', 'DIPLOMA', 'IDENTIDADE', 'RESIDENCIA', 'CONTRATO']
const OBRIGATORIOS: TipoDocumentoMedico[] = ['CRM', 'DIPLOMA', 'IDENTIDADE', 'RESIDENCIA']

const TIPO_INFO: Record<TipoDocumentoMedico, { label: string; Icon: React.ElementType }> = {
  CRM:        { label: 'Registro CRM',               Icon: Award },
  DIPLOMA:    { label: 'Diploma Médico',              Icon: GraduationCap },
  IDENTIDADE: { label: 'Identidade (CNH ou RG)',      Icon: CreditCard },
  RESIDENCIA: { label: 'Comprovante de Residência',   Icon: Home },
  CONTRATO:   { label: 'Contrato',                    Icon: FileText },
}

const STATUS_CONFIG: Record<StatusValidacaoDocumento, { label: string; cls: string; Icon: React.ElementType }> = {
  PENDENTE:  { label: 'Aguardando', cls: 'bg-gray-100 text-gray-500',  Icon: Clock },
  APROVADO:  { label: 'Aprovado',   cls: 'bg-green-50 text-green-600', Icon: CheckCircle2 },
  REPROVADO: { label: 'Reprovado',  cls: 'bg-red-50 text-red-600',     Icon: XCircle },
}

const ACCEPT = '.pdf,.jpg,.jpeg,.png'
const MAX_MB = 10

function isImagem(nome: string) { return /\.(jpg|jpeg|png)$/i.test(nome) }
function validarArquivo(f: File) {
  if (f.size > MAX_MB * 1024 * 1024) return `Arquivo muito grande (máx. ${MAX_MB} MB)`
  if (!/\.(pdf|jpg|jpeg|png)$/i.test(f.name)) return 'Apenas PDF, JPG e PNG são aceitos'
  return null
}

// ─── Linha de documento ───────────────────────────────────────────────────────

interface RowProps {
  tipo: TipoDocumentoMedico
  doc: DocumentoMedico | null
  preview: string | null
  uploading: boolean
  validating: boolean
  canValidate: boolean
  rejectingDocId: string | null
  rejectReason: string
  onAprovar: (doc: DocumentoMedico) => void
  onIniciarReprovacao: (doc: DocumentoMedico) => void
  onConfirmarReprovacao: () => void
  onCancelarReprovacao: () => void
  onRejectReasonChange: (v: string) => void
  onVerArquivo: (doc: DocumentoMedico) => void
  onReenviar: (tipo: TipoDocumentoMedico) => void
}

function DocumentoRow({
  tipo, doc, preview, uploading, validating, canValidate,
  rejectingDocId, rejectReason,
  onAprovar, onIniciarReprovacao, onConfirmarReprovacao,
  onCancelarReprovacao, onRejectReasonChange, onVerArquivo, onReenviar,
}: RowProps) {
  const { Icon, label } = TIPO_INFO[tipo]
  const statusCfg = doc ? STATUS_CONFIG[doc.statusValidacao] : null
  const isRejecting = !!(doc && rejectingDocId === doc.id)
  const obrigatorio = OBRIGATORIOS.includes(tipo)

  return (
    <div className="flex flex-col gap-0">
      <div className="flex items-center gap-3 py-3 px-4">
        {/* Ícone + label */}
        <div className="w-8 h-8 rounded-lg bg-primary-50 flex items-center justify-center shrink-0">
          <Icon size={15} className="text-primary" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="text-sm font-semibold text-ds-text">{label}</span>
            {obrigatorio && (
              <span className="text-[10px] text-ds-light border border-ds-border rounded px-1">obrigatório</span>
            )}
          </div>
          {doc ? (
            <p className="text-[11px] text-ds-light truncate">{doc.nomeArquivo}</p>
          ) : uploading ? (
            <p className="text-[11px] text-primary">Enviando...</p>
          ) : (
            <p className="text-[11px] text-ds-light">Nenhum arquivo enviado</p>
          )}
        </div>

        {/* Status badge */}
        {uploading ? (
          <Spinner size="sm" />
        ) : statusCfg ? (
          <span className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold shrink-0 ${statusCfg.cls}`}>
            <statusCfg.Icon size={11} />
            {statusCfg.label}
          </span>
        ) : (
          <span className="text-[11px] text-ds-light">—</span>
        )}

        {/* Ações rápidas */}
        {doc && !isRejecting && (
          <div className="flex items-center gap-1 shrink-0">
            <button
              onClick={() => onVerArquivo(doc)}
              className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
              title="Ver arquivo"
            >
              <Eye size={14} />
            </button>
            <button
              onClick={() => onReenviar(tipo)}
              className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
              title="Substituir arquivo"
            >
              <RotateCcw size={14} />
            </button>
            {canValidate && doc.statusValidacao !== 'APROVADO' && (
              <button
                onClick={() => onAprovar(doc)}
                disabled={validating}
                className="px-2 py-0.5 rounded text-[11px] font-semibold bg-green-50 text-green-600 hover:bg-green-100 disabled:opacity-50 transition-colors"
              >
                Aprovar
              </button>
            )}
            {canValidate && doc.statusValidacao !== 'REPROVADO' && (
              <button
                onClick={() => onIniciarReprovacao(doc)}
                disabled={validating}
                className="px-2 py-0.5 rounded text-[11px] font-semibold bg-red-50 text-red-600 hover:bg-red-100 disabled:opacity-50 transition-colors"
              >
                Reprovar
              </button>
            )}
          </div>
        )}
      </div>

      {/* Motivo de reprovação */}
      {doc?.motivoReprovacao && !isRejecting && (
        <p className="text-[11px] text-red-500 bg-red-50 px-4 pb-2 -mt-1">
          Motivo: {doc.motivoReprovacao}
        </p>
      )}

      {/* Inline reject form */}
      {isRejecting && (
        <div className="flex flex-col gap-2 px-4 pb-3 -mt-1 bg-red-50">
          <textarea
            value={rejectReason}
            onChange={e => onRejectReasonChange(e.target.value)}
            placeholder="Informe o motivo da reprovação..."
            rows={2}
            autoFocus
            className="w-full text-xs border border-red-200 rounded-lg px-2 py-1.5 resize-none focus:outline-none focus:ring-2 focus:ring-red-200 focus:border-red-400 bg-white"
          />
          <div className="flex gap-1.5 justify-end">
            <button
              onClick={onCancelarReprovacao}
              className="px-2.5 py-1 rounded-lg text-[11px] border border-ds-border text-ds-mid hover:bg-white transition-colors"
            >
              Cancelar
            </button>
            <button
              onClick={onConfirmarReprovacao}
              disabled={!rejectReason.trim() || validating}
              className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-red-600 text-white hover:bg-red-700 disabled:opacity-50 transition-colors"
            >
              {validating ? 'Salvando...' : 'Confirmar reprovação'}
            </button>
          </div>
        </div>
      )}

      {/* Preview thumbnail para imagens */}
      {doc && preview && isImagem(doc.nomeArquivo) && !isRejecting && (
        <div className="px-4 pb-3 -mt-1">
          <img
            src={preview}
            alt={label}
            className="h-16 rounded-lg border border-ds-border object-cover"
          />
        </div>
      )}
    </div>
  )
}

// ─── Modal principal ──────────────────────────────────────────────────────────

interface Props {
  medico: Medico
  onClose: () => void
  onDocumentosChange?: () => void
}

export function DocumentosModal({ medico, onClose, onDocumentosChange }: Props) {
  const { user } = useAuth()
  const canValidate = user?.realm_access?.roles.some(r => r === 'gestao' || r === 'operacao') ?? false

  const [selectedTipo, setSelectedTipo] = useState<TipoDocumentoMedico>('CRM')
  const [docs, setDocs]     = useState<Partial<Record<TipoDocumentoMedico, DocumentoMedico>>>({})
  const [previews, setPreviews] = useState<Partial<Record<TipoDocumentoMedico, string>>>({})
  const [dragging, setDragging]     = useState(false)
  const [uploading, setUploading]   = useState<TipoDocumentoMedico | null>(null)
  const [validating, setValidating] = useState<string | null>(null) // docId
  const [rejectingDocId, setRejectingDocId] = useState<string | null>(null)
  const [rejectReason, setRejectReason]     = useState('')
  const [error, setError]   = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const fileInputRef = useRef<HTMLInputElement>(null)
  const previewsRef  = useRef(previews)
  previewsRef.current = previews

  useEffect(() => () => {
    Object.values(previewsRef.current).forEach(url => {
      if (url?.startsWith('blob:')) URL.revokeObjectURL(url)
    })
  }, [])

  useEffect(() => {
    medicosApi.listarDocumentos(medico.id)
      .then(list => {
        const byTipo: Partial<Record<TipoDocumentoMedico, DocumentoMedico>> = {}
        for (const doc of list) byTipo[doc.tipo] = doc
        setDocs(byTipo)
        for (const doc of list) {
          if (isImagem(doc.nomeArquivo)) {
            medicosApi.getDocumentoUrl(medico.id, doc.id)
              .then(url => setPreviews(p => ({ ...p, [doc.tipo]: url })))
              .catch(() => {})
          }
        }
      })
      .catch(() => setError('Erro ao carregar documentos'))
      .finally(() => setLoading(false))
  }, [medico.id])

  async function handleUpload(tipo: TipoDocumentoMedico, file: File) {
    const err = validarArquivo(file)
    if (err) { setError(err); return }
    setError(null)

    if (isImagem(file.name)) {
      const url = URL.createObjectURL(file)
      setPreviews(p => ({ ...p, [tipo]: url }))
    } else {
      setPreviews(p => { const n = { ...p }; delete n[tipo]; return n })
    }

    setUploading(tipo)
    try {
      const doc = await medicosApi.uploadDocumento(medico.id, tipo, file)
      setDocs(d => ({ ...d, [tipo]: doc }))
      onDocumentosChange?.()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao enviar arquivo')
      setPreviews(p => { const n = { ...p }; delete n[tipo]; return n })
    } finally {
      setUploading(null)
    }
  }

  function handleFileInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) handleUpload(selectedTipo, file)
    e.target.value = ''
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) handleUpload(selectedTipo, file)
  }

  function handleReenviar(tipo: TipoDocumentoMedico) {
    setSelectedTipo(tipo)
    setTimeout(() => fileInputRef.current?.click(), 0)
  }

  async function handleAprovar(doc: DocumentoMedico) {
    setValidating(doc.id)
    setError(null)
    try {
      const updated = await medicosApi.validarDocumento(medico.id, doc.id, { statusValidacao: 'APROVADO' })
      setDocs(d => ({ ...d, [doc.tipo]: updated }))
      onDocumentosChange?.()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao aprovar documento')
    } finally {
      setValidating(null)
    }
  }

  async function handleConfirmarReprovacao() {
    if (!rejectingDocId) return
    const tipo = Object.keys(docs).find(k => docs[k as TipoDocumentoMedico]?.id === rejectingDocId) as TipoDocumentoMedico | undefined
    if (!tipo) return
    setValidating(rejectingDocId)
    setError(null)
    try {
      const updated = await medicosApi.validarDocumento(medico.id, rejectingDocId, {
        statusValidacao: 'REPROVADO',
        motivoReprovacao: rejectReason.trim(),
      })
      setDocs(d => ({ ...d, [tipo]: updated }))
      setRejectingDocId(null)
      setRejectReason('')
      onDocumentosChange?.()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao reprovar documento')
    } finally {
      setValidating(null)
    }
  }

  async function handleVerArquivo(doc: DocumentoMedico) {
    try {
      const url = await medicosApi.getDocumentoUrl(medico.id, doc.id)
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch {
      setError('Não foi possível obter o link do arquivo')
    }
  }

  const aprovados = OBRIGATORIOS.filter(t => docs[t]?.statusValidacao === 'APROVADO').length

  return (
    <Modal open onClose={onClose} title="" size="lg">
      <div className="flex flex-col gap-4">

        {/* Header */}
        <div className="flex items-start justify-between gap-4 -mt-1">
          <div>
            <h2 className="text-base font-bold text-ds-text">Documentos do Médico</h2>
            <p className="text-xs text-ds-light mt-0.5">{medico.nome} · CRM {medico.crm}/{medico.crmUf.trim()}</p>
          </div>
          <div className="shrink-0 flex flex-col items-end gap-1">
            <span className={`px-3 py-1 rounded-full text-sm font-bold ${aprovados === 4 ? 'bg-green-50 text-green-600' : 'bg-amber-50 text-amber-700'}`}>
              {aprovados}/4 obrigatórios aprovados
            </span>
            <div className="w-28 h-1.5 rounded-full bg-ds-border overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${aprovados === 4 ? 'bg-green-500' : 'bg-amber-400'}`}
                style={{ width: `${(aprovados / 4) * 100}%` }}
              />
            </div>
          </div>
        </div>

        {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

        {/* Upload zone */}
        <div className="flex flex-col gap-2 p-4 border border-ds-border rounded-xl bg-ds-input">
          <p className="text-xs font-semibold text-ds-mid">Enviar documento</p>

          <div className="flex items-center gap-2">
            <select
              value={selectedTipo}
              onChange={e => setSelectedTipo(e.target.value as TipoDocumentoMedico)}
              className="flex-1 py-1.5 px-3 text-sm border border-ds-border rounded-lg bg-white text-ds-text focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
            >
              {TODOS_TIPOS.map(t => (
                <option key={t} value={t}>{TIPO_INFO[t].label}</option>
              ))}
            </select>
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading !== null}
              className="px-3 py-1.5 text-sm font-medium border border-ds-border rounded-lg bg-white text-ds-mid hover:bg-ds-hover disabled:opacity-50 transition-colors flex items-center gap-1.5 shrink-0"
            >
              <Upload size={14} /> Escolher arquivo
            </button>
          </div>

          <div
            onDragOver={e => { e.preventDefault(); setDragging(true) }}
            onDragLeave={() => setDragging(false)}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            className={[
              'flex flex-col items-center justify-center gap-1.5 py-5 rounded-xl border-2 border-dashed cursor-pointer transition-colors',
              dragging ? 'border-primary bg-primary-50' : 'border-ds-border hover:border-primary hover:bg-primary-50',
              uploading !== null ? 'pointer-events-none opacity-50' : '',
            ].join(' ')}
          >
            {uploading !== null ? (
              <>
                <Spinner size="sm" />
                <p className="text-xs text-primary">Enviando {TIPO_INFO[uploading].label}...</p>
              </>
            ) : (
              <>
                <Upload size={20} className={dragging ? 'text-primary' : 'text-ds-light'} />
                <p className="text-xs font-medium text-ds-mid">Arraste o arquivo aqui</p>
                <p className="text-[11px] text-ds-light">PDF, JPG ou PNG · máx. {MAX_MB} MB</p>
              </>
            )}
          </div>
          <input ref={fileInputRef} type="file" accept={ACCEPT} className="hidden" onChange={handleFileInputChange} />
        </div>

        {/* Lista de documentos */}
        <div className="border border-ds-border rounded-xl overflow-hidden">
          <div className="px-4 py-2 border-b border-ds-border bg-ds-input">
            <p className="text-xs font-semibold text-ds-mid">Documentos enviados</p>
          </div>
          {loading ? (
            <div className="flex justify-center py-8"><Spinner size="md" /></div>
          ) : (
            <div className="divide-y divide-ds-border">
              {TODOS_TIPOS.map(tipo => (
                <DocumentoRow
                  key={tipo}
                  tipo={tipo}
                  doc={docs[tipo] ?? null}
                  preview={previews[tipo] ?? null}
                  uploading={uploading === tipo}
                  validating={validating === docs[tipo]?.id}
                  canValidate={canValidate}
                  rejectingDocId={rejectingDocId}
                  rejectReason={rejectReason}
                  onAprovar={handleAprovar}
                  onIniciarReprovacao={doc => { setRejectingDocId(doc.id); setRejectReason('') }}
                  onConfirmarReprovacao={handleConfirmarReprovacao}
                  onCancelarReprovacao={() => { setRejectingDocId(null); setRejectReason('') }}
                  onRejectReasonChange={setRejectReason}
                  onVerArquivo={handleVerArquivo}
                  onReenviar={handleReenviar}
                />
              ))}
            </div>
          )}
        </div>

        <div className="flex justify-end pt-1 border-t border-ds-border">
          <Button variant="outline" onClick={onClose}>Fechar</Button>
        </div>
      </div>
    </Modal>
  )
}
