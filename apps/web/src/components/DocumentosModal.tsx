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

// ─── Metadados dos tipos de documento ────────────────────────────────────────

const TIPOS_OBRIGATORIOS: TipoDocumentoMedico[] = ['CRM', 'DIPLOMA', 'IDENTIDADE', 'RESIDENCIA']

const TIPO_INFO: Record<TipoDocumentoMedico, { label: string; descricao: string; Icon: React.ElementType }> = {
  CRM:       { label: 'Registro CRM',               descricao: 'Registro no Conselho Regional de Medicina', Icon: Award },
  DIPLOMA:   { label: 'Diploma Médico',              descricao: 'Diploma de graduação em medicina',           Icon: GraduationCap },
  IDENTIDADE:{ label: 'Identidade (CNH ou RG)',      descricao: 'Documento de identificação com foto',        Icon: CreditCard },
  RESIDENCIA:{ label: 'Comprovante de Residência',   descricao: 'Documento emitido há menos de 90 dias',     Icon: Home },
  CONTRATO:  { label: 'Contrato',                    descricao: 'Contrato de prestação de serviços',          Icon: FileText },
}

const STATUS_CONFIG: Record<StatusValidacaoDocumento, { label: string; className: string; Icon: React.ElementType }> = {
  PENDENTE:  { label: 'Aguardando',  className: 'bg-gray-100 text-gray-500',   Icon: Clock },
  APROVADO:  { label: 'Aprovado',    className: 'bg-green-50 text-green-600',  Icon: CheckCircle2 },
  REPROVADO: { label: 'Reprovado',   className: 'bg-red-50 text-red-600',      Icon: XCircle },
}

const ACCEPT_TYPES = '.pdf,.jpg,.jpeg,.png'
const MAX_SIZE_MB  = 10

function isImageFile(name: string) {
  return /\.(jpg|jpeg|png)$/i.test(name)
}

function validarArquivo(file: File): string | null {
  if (file.size > MAX_SIZE_MB * 1024 * 1024) return `Arquivo muito grande (máx. ${MAX_SIZE_MB} MB)`
  if (!/\.(pdf|jpg|jpeg|png)$/i.test(file.name)) return 'Apenas PDF, JPG e PNG são aceitos'
  return null
}

// ─── Card de documento ────────────────────────────────────────────────────────

interface CardProps {
  tipo: TipoDocumentoMedico
  doc: DocumentoMedico | null
  preview: string | null
  uploading: boolean
  validating: boolean
  canValidate: boolean
  rejecting: boolean
  rejectReason: string
  onUpload: (tipo: TipoDocumentoMedico, file: File) => void
  onAprovar: (doc: DocumentoMedico) => void
  onIniciarReprovacao: (doc: DocumentoMedico) => void
  onConfirmarReprovacao: () => void
  onCancelarReprovacao: () => void
  onRejectReasonChange: (v: string) => void
  onVerArquivo: (doc: DocumentoMedico) => void
  error: string | null
}

function DocumentoCard({
  tipo, doc, preview, uploading, validating, canValidate,
  rejecting, rejectReason, onUpload, onAprovar, onIniciarReprovacao,
  onConfirmarReprovacao, onCancelarReprovacao, onRejectReasonChange, onVerArquivo,
}: CardProps) {
  const inputRef  = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const { Icon, label, descricao } = TIPO_INFO[tipo]

  function handleDrop(e: React.DragEvent) {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) onUpload(tipo, file)
  }

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) onUpload(tipo, file)
    e.target.value = ''
  }

  const statusCfg = doc ? STATUS_CONFIG[doc.statusValidacao] : null

  return (
    <div className="flex flex-col border border-ds-border rounded-xl overflow-hidden bg-white">
      {/* Header do card */}
      <div className="flex items-center gap-3 px-4 py-3 border-b border-ds-border bg-ds-input">
        <div className="w-8 h-8 rounded-lg bg-primary-50 flex items-center justify-center shrink-0">
          <Icon size={16} className="text-primary" />
        </div>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-ds-text leading-tight truncate">{label}</p>
          <p className="text-[11px] text-ds-light leading-tight">{descricao}</p>
        </div>
        {statusCfg && (
          <span className={`ml-auto flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold shrink-0 ${statusCfg.className}`}>
            <statusCfg.Icon size={11} />
            {statusCfg.label}
          </span>
        )}
      </div>

      {/* Área de upload / preview */}
      <div className="flex-1 flex flex-col items-center justify-center p-4 min-h-[140px]">
        {uploading ? (
          <div className="flex flex-col items-center gap-2">
            <Spinner size="md" />
            <p className="text-xs text-ds-light">Enviando...</p>
          </div>
        ) : doc && (preview || !isImageFile(doc.nomeArquivo)) ? (
          <div className="w-full flex flex-col items-center gap-3">
            {preview && isImageFile(doc.nomeArquivo) ? (
              <img
                src={preview}
                alt={label}
                className="max-h-28 max-w-full object-contain rounded-lg border border-ds-border"
              />
            ) : (
              <div className="flex flex-col items-center gap-1">
                <div className="w-12 h-12 rounded-xl bg-red-50 flex items-center justify-center">
                  <FileText size={24} className="text-red-400" />
                </div>
                <p className="text-[11px] text-ds-light text-center break-all px-2">{doc.nomeArquivo}</p>
              </div>
            )}
            {doc.motivoReprovacao && (
              <p className="text-[11px] text-red-500 bg-red-50 rounded-lg px-2 py-1 w-full text-center">
                {doc.motivoReprovacao}
              </p>
            )}
          </div>
        ) : doc ? (
          // Arquivo existe mas sem preview (ex: imagem de sessão anterior)
          <div className="flex flex-col items-center gap-1">
            <div className="w-12 h-12 rounded-xl bg-primary-50 flex items-center justify-center">
              <Icon size={24} className="text-primary" />
            </div>
            <p className="text-[11px] text-ds-light text-center break-all px-2">{doc.nomeArquivo}</p>
            {doc.motivoReprovacao && (
              <p className="text-[11px] text-red-500 bg-red-50 rounded-lg px-2 py-1 mt-1 w-full text-center">
                {doc.motivoReprovacao}
              </p>
            )}
          </div>
        ) : (
          // Drop zone vazia
          <div
            onDragOver={e => { e.preventDefault(); setDragging(true) }}
            onDragLeave={() => setDragging(false)}
            onDrop={handleDrop}
            onClick={() => inputRef.current?.click()}
            className={[
              'w-full flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed cursor-pointer py-6 transition-colors',
              dragging ? 'border-primary bg-primary-50' : 'border-ds-border hover:border-primary hover:bg-primary-50',
            ].join(' ')}
          >
            <Upload size={20} className="text-ds-light" />
            <p className="text-xs font-medium text-ds-mid">Arraste ou clique para enviar</p>
            <p className="text-[11px] text-ds-light">PDF, JPG ou PNG • máx. {MAX_SIZE_MB} MB</p>
          </div>
        )}
        <input ref={inputRef} type="file" accept={ACCEPT_TYPES} className="hidden" onChange={handleChange} />
      </div>

      {/* Ações */}
      <div className="flex flex-wrap items-center gap-2 px-4 py-3 border-t border-ds-border bg-ds-input">
        {doc && (
          <button
            onClick={() => onVerArquivo(doc)}
            className="flex items-center gap-1 text-[11px] text-primary hover:underline"
          >
            <Eye size={12} /> Ver arquivo
          </button>
        )}

        {doc && (doc.statusValidacao === 'REPROVADO' || doc.statusValidacao === 'PENDENTE') && (
          <button
            onClick={() => inputRef.current?.click()}
            disabled={uploading}
            className="flex items-center gap-1 text-[11px] text-ds-mid hover:text-primary disabled:opacity-50"
          >
            <RotateCcw size={12} /> {doc.statusValidacao === 'REPROVADO' ? 'Re-enviar' : 'Substituir'}
          </button>
        )}

        {doc && canValidate && !rejecting && (
          <div className="ml-auto flex items-center gap-1.5">
            {doc.statusValidacao !== 'APROVADO' && (
              <button
                onClick={() => onAprovar(doc)}
                disabled={validating}
                className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-green-50 text-green-600 hover:bg-green-100 disabled:opacity-50 transition-colors"
              >
                Aprovar
              </button>
            )}
            {doc.statusValidacao !== 'REPROVADO' && (
              <button
                onClick={() => onIniciarReprovacao(doc)}
                disabled={validating}
                className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-red-50 text-red-600 hover:bg-red-100 disabled:opacity-50 transition-colors"
              >
                Reprovar
              </button>
            )}
          </div>
        )}

        {rejecting && (
          <div className="w-full flex flex-col gap-2 mt-1">
            <textarea
              value={rejectReason}
              onChange={e => onRejectReasonChange(e.target.value)}
              placeholder="Informe o motivo da reprovação..."
              rows={2}
              className="w-full text-xs border border-ds-border rounded-lg px-2 py-1.5 resize-none focus:outline-none focus:ring-2 focus:ring-red-200 focus:border-red-400"
            />
            <div className="flex gap-1.5 justify-end">
              <button
                onClick={onCancelarReprovacao}
                className="px-2.5 py-1 rounded-lg text-[11px] border border-ds-border text-ds-mid hover:bg-ds-hover transition-colors"
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
      </div>
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

  const [docs, setDocs]     = useState<Partial<Record<TipoDocumentoMedico, DocumentoMedico>>>({})
  const [previews, setPreviews] = useState<Partial<Record<TipoDocumentoMedico, string>>>({})
  const [uploading, setUploading]   = useState<TipoDocumentoMedico | null>(null)
  const [validating, setValidating] = useState<string | null>(null) // docId
  const [rejecting, setRejecting]   = useState<{ tipo: TipoDocumentoMedico; docId: string } | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [error, setError]   = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  // Revoga blob URLs ao desmontar
  const previewsRef = useRef(previews)
  previewsRef.current = previews
  useEffect(() => () => {
    Object.values(previewsRef.current).forEach(url => {
      if (url?.startsWith('blob:')) URL.revokeObjectURL(url)
    })
  }, [])

  // Carrega documentos existentes
  useEffect(() => {
    medicosApi.listarDocumentos(medico.id)
      .then(list => {
        const byTipo: Partial<Record<TipoDocumentoMedico, DocumentoMedico>> = {}
        for (const doc of list) byTipo[doc.tipo] = doc
        setDocs(byTipo)

        // Carrega preview para imagens existentes via URL pré-assinada
        for (const doc of list) {
          if (isImageFile(doc.nomeArquivo)) {
            medicosApi.getDocumentoUrl(medico.id, doc.id)
              .then(url => setPreviews(p => ({ ...p, [doc.tipo]: url })))
              .catch(() => {})
          }
        }
      })
      .catch(() => setError('Erro ao carregar documentos'))
      .finally(() => setLoading(false))
  }, [medico.id])

  const aprovados = TIPOS_OBRIGATORIOS.filter(t => docs[t]?.statusValidacao === 'APROVADO').length

  async function handleUpload(tipo: TipoDocumentoMedico, file: File) {
    const err = validarArquivo(file)
    if (err) { setError(err); return }
    setError(null)

    // Preview local imediato para imagens
    if (isImageFile(file.name)) {
      const localUrl = URL.createObjectURL(file)
      setPreviews(p => ({ ...p, [tipo]: localUrl }))
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
    if (!rejecting) return
    setValidating(rejecting.docId)
    setError(null)
    try {
      const updated = await medicosApi.validarDocumento(medico.id, rejecting.docId, {
        statusValidacao: 'REPROVADO',
        motivoReprovacao: rejectReason.trim(),
      })
      setDocs(d => ({ ...d, [rejecting.tipo]: updated }))
      setRejecting(null)
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

  return (
    <Modal open onClose={onClose} title="" size="xl">
      <div className="flex flex-col gap-5">
        {/* Header */}
        <div className="flex items-start justify-between gap-4 -mt-1">
          <div>
            <h2 className="text-base font-bold text-ds-text">Documentos do Médico</h2>
            <p className="text-xs text-ds-light mt-0.5">{medico.nome} · CRM {medico.crm}/{medico.crmUf.trim()}</p>
          </div>
          <div className="shrink-0 flex flex-col items-end gap-1">
            <span className={`px-3 py-1 rounded-full text-sm font-bold ${aprovados === 4 ? 'bg-green-50 text-green-600' : 'bg-amber-50 text-amber-700'}`}>
              {aprovados}/4 aprovados
            </span>
            {/* Barra de progresso */}
            <div className="w-24 h-1.5 rounded-full bg-ds-border overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${aprovados === 4 ? 'bg-green-500' : 'bg-amber-400'}`}
                style={{ width: `${(aprovados / 4) * 100}%` }}
              />
            </div>
          </div>
        </div>

        {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

        {loading ? (
          <div className="flex justify-center py-12"><Spinner size="lg" /></div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {TIPOS_OBRIGATORIOS.map(tipo => (
              <DocumentoCard
                key={tipo}
                tipo={tipo}
                doc={docs[tipo] ?? null}
                preview={previews[tipo] ?? null}
                uploading={uploading === tipo}
                validating={validating === docs[tipo]?.id}
                canValidate={canValidate}
                rejecting={rejecting?.tipo === tipo}
                rejectReason={rejectReason}
                onUpload={handleUpload}
                onAprovar={handleAprovar}
                onIniciarReprovacao={doc => { setRejecting({ tipo, docId: doc.id }); setRejectReason('') }}
                onConfirmarReprovacao={handleConfirmarReprovacao}
                onCancelarReprovacao={() => { setRejecting(null); setRejectReason('') }}
                onRejectReasonChange={setRejectReason}
                onVerArquivo={handleVerArquivo}
                error={null}
              />
            ))}
          </div>
        )}

        <div className="flex justify-end pt-1 border-t border-ds-border">
          <Button variant="outline" onClick={onClose}>Fechar</Button>
        </div>
      </div>
    </Modal>
  )
}
