import { useEffect, useRef, useState } from 'react'
import {
  FileText, Building2, Shield, MapPin, Stethoscope, UserCheck,
  FileSearch, Scale, Landmark, Users,
  Upload, CheckCircle2, XCircle, Clock, Eye, Plus, Trash2, Download,
  History, ChevronDown, ChevronUp,
} from 'lucide-react'
import { Modal, Button, Alert, Spinner } from '@pinsaude/ui'
import {
  Empresa, TipoDocumentoEmpresa, StatusValidacaoDocumentoEmpresa,
  DocumentoEmpresa, empresasApi,
} from '../api/empresasApi'
import { useAuth } from '../auth/useAuth'

// ─── Constantes ───────────────────────────────────────────────────────────────

const TODOS_TIPOS: TipoDocumentoEmpresa[] = [
  'CONTRATO_SOCIAL',
  'CONSELHO',
  'ENDERECO_FISCAL',
  'DIRECAO_TECNICA',
  'DOCUMENTACAO_SOCIO_ADMINISTRADOR',
  'NADA_CONSTA_ESTADUAL',
  'CND_FALENCIA',
  'CND_FEDERAL',
  'CND_FGTS',
  'CND_MUNICIPAL',
  'CND_TRABALHISTA',
]

const TIPO_INFO: Record<TipoDocumentoEmpresa, { label: string; Icon: React.ElementType }> = {
  CONTRATO_SOCIAL:                    { label: 'Contrato Social',                      Icon: FileText    },
  CONSELHO:                           { label: 'Conselho',                             Icon: Shield      },
  ENDERECO_FISCAL:                    { label: 'Endereço Fiscal',                      Icon: MapPin      },
  DIRECAO_TECNICA:                    { label: 'Direção Técnica',                      Icon: Stethoscope },
  DOCUMENTACAO_SOCIO_ADMINISTRADOR:   { label: 'Documentação do Sócio Administrador',  Icon: UserCheck   },
  NADA_CONSTA_ESTADUAL:               { label: 'Nada Consta Estadual',                 Icon: FileSearch  },
  CND_FALENCIA:                       { label: 'CND Falência — Tribunal de Justiça',   Icon: Scale       },
  CND_FEDERAL:                        { label: 'CND Federal',                          Icon: Building2   },
  CND_FGTS:                           { label: 'CND do FGTS',                          Icon: Landmark    },
  CND_MUNICIPAL:                      { label: 'CND Municipal',                        Icon: MapPin      },
  CND_TRABALHISTA:                    { label: 'CND Trabalhista',                      Icon: Users       },
}

const STATUS_CONFIG: Record<StatusValidacaoDocumentoEmpresa, { label: string; cls: string; Icon: React.ElementType }> = {
  PENDENTE:  { label: 'Aguardando', cls: 'bg-gray-100 text-gray-500',  Icon: Clock        },
  APROVADO:  { label: 'Aprovado',   cls: 'bg-green-50 text-green-600', Icon: CheckCircle2 },
  REPROVADO: { label: 'Reprovado',  cls: 'bg-red-50 text-red-600',     Icon: XCircle      },
}

const ACCEPT = '.pdf,.jpg,.jpeg,.png'
const MAX_MB = 10

function isImagem(nome: string) { return /\.(jpg|jpeg|png)$/i.test(nome) }
function validarArquivo(f: File) {
  if (f.size > MAX_MB * 1024 * 1024) return `Arquivo muito grande (máx. ${MAX_MB} MB)`
  if (!/\.(pdf|jpg|jpeg|png)$/i.test(f.name)) return 'Apenas PDF, JPG e PNG são aceitos'
  return null
}

// ─── Linha de tipo ────────────────────────────────────────────────────────────

interface RowProps {
  tipo: TipoDocumentoEmpresa
  docs: DocumentoEmpresa[]
  previews: Record<string, string>
  uploadingThisTipo: boolean
  validating: string | null
  canValidate: boolean
  rejectingDocId: string | null
  rejectReason: string
  downloadingId: string | null
  onAprovar: (doc: DocumentoEmpresa) => void
  onIniciarReprovacao: (doc: DocumentoEmpresa) => void
  onConfirmarReprovacao: () => void
  onCancelarReprovacao: () => void
  onRejectReasonChange: (v: string) => void
  onVerArquivo: (doc: DocumentoEmpresa) => void
  onDownload: (doc: DocumentoEmpresa) => void
  onDeletar: (doc: DocumentoEmpresa) => void
  onAdicionarMais: (tipo: TipoDocumentoEmpresa) => void
}

function DocumentoRow({
  tipo, docs, previews, uploadingThisTipo, validating, canValidate,
  rejectingDocId, rejectReason, downloadingId,
  onAprovar, onIniciarReprovacao, onConfirmarReprovacao,
  onCancelarReprovacao, onRejectReasonChange, onVerArquivo, onDownload, onDeletar, onAdicionarMais,
}: RowProps) {
  const { Icon, label } = TIPO_INFO[tipo]

  return (
    <div className="flex flex-col">
      <div className="flex items-center gap-3 py-2.5 px-4 bg-ds-input">
        <div className="w-7 h-7 rounded-lg bg-primary-50 flex items-center justify-center shrink-0">
          <Icon size={14} className="text-primary" />
        </div>
        <span className="flex-1 text-sm font-semibold text-ds-text">{label}</span>
        {docs.length > 0 && (
          <span className="text-[11px] text-ds-light">
            {docs.length} arquivo{docs.length > 1 ? 's' : ''}
          </span>
        )}
        <button
          onClick={() => onAdicionarMais(tipo)}
          disabled={uploadingThisTipo}
          title="Adicionar arquivo"
          className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors disabled:opacity-50"
        >
          <Plus size={14} />
        </button>
      </div>

      {docs.length === 0 && !uploadingThisTipo && (
        <p className="text-[11px] text-ds-light px-4 py-2 border-t border-ds-border/50">
          Nenhum arquivo enviado
        </p>
      )}

      {uploadingThisTipo && (
        <div className="flex items-center gap-2 px-4 py-2 border-t border-ds-border/50">
          <Spinner size="sm" />
          <p className="text-[11px] text-primary">Enviando...</p>
        </div>
      )}

      {docs.map(doc => {
        const statusCfg = STATUS_CONFIG[doc.statusValidacao]
        const isRejecting = rejectingDocId === doc.id
        const preview = previews[doc.id]

        return (
          <div key={doc.id} className="flex flex-col border-t border-ds-border/50">
            <div className="flex items-center gap-2 py-2 px-6">
              <div className="flex-1 min-w-0">
                <p className="text-[12px] text-ds-mid truncate">{doc.nomeArquivo}</p>
              </div>

              <span className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold shrink-0 ${statusCfg.cls}`}>
                <statusCfg.Icon size={11} />
                {statusCfg.label}
              </span>

              {!isRejecting && (
                <div className="flex items-center gap-1 shrink-0">
                  <button onClick={() => onVerArquivo(doc)}
                    className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
                    title="Visualizar arquivo">
                    <Eye size={13} />
                  </button>
                  <button onClick={() => onDownload(doc)}
                    disabled={downloadingId === doc.id}
                    className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors disabled:opacity-50"
                    title="Baixar arquivo">
                    <Download size={13} className={downloadingId === doc.id ? 'animate-bounce' : ''} />
                  </button>
                  <button onClick={() => onDeletar(doc)}
                    className="p-1 rounded text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors"
                    title="Remover arquivo">
                    <Trash2 size={13} />
                  </button>
                  {canValidate && doc.statusValidacao !== 'APROVADO' && (
                    <button onClick={() => onAprovar(doc)}
                      disabled={validating === doc.id}
                      className="px-2 py-0.5 rounded text-[11px] font-semibold bg-green-50 text-green-600 hover:bg-green-100 disabled:opacity-50 transition-colors">
                      Aprovar
                    </button>
                  )}
                  {canValidate && doc.statusValidacao !== 'REPROVADO' && (
                    <button onClick={() => onIniciarReprovacao(doc)}
                      disabled={validating === doc.id}
                      className="px-2 py-0.5 rounded text-[11px] font-semibold bg-red-50 text-red-600 hover:bg-red-100 disabled:opacity-50 transition-colors">
                      Reprovar
                    </button>
                  )}
                </div>
              )}
            </div>

            {doc.motivoReprovacao && !isRejecting && (
              <p className="text-[11px] text-red-500 bg-red-50 px-6 pb-2 -mt-1">
                Motivo: {doc.motivoReprovacao}
              </p>
            )}

            {isRejecting && (
              <div className="flex flex-col gap-2 px-6 pb-3 -mt-1 bg-red-50">
                <textarea
                  value={rejectReason}
                  onChange={e => onRejectReasonChange(e.target.value)}
                  placeholder="Informe o motivo da reprovação..."
                  rows={2}
                  autoFocus
                  className="w-full text-xs border border-red-200 rounded-lg px-2 py-1.5 resize-none focus:outline-none focus:ring-2 focus:ring-red-200 focus:border-red-400 bg-white"
                />
                <div className="flex gap-1.5 justify-end">
                  <button onClick={onCancelarReprovacao}
                    className="px-2.5 py-1 rounded-lg text-[11px] border border-ds-border text-ds-mid hover:bg-white transition-colors">
                    Cancelar
                  </button>
                  <button onClick={onConfirmarReprovacao}
                    disabled={!rejectReason.trim() || validating === doc.id}
                    className="px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-red-600 text-white hover:bg-red-700 disabled:opacity-50 transition-colors">
                    {validating === doc.id ? 'Salvando...' : 'Confirmar reprovação'}
                  </button>
                </div>
              </div>
            )}

            {preview && isImagem(doc.nomeArquivo) && !isRejecting && (
              <div className="px-6 pb-2 -mt-1">
                <img src={preview} alt={label} className="h-14 rounded-lg border border-ds-border object-cover" />
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

// ─── Painel histórico Contrato Social ─────────────────────────────────────────

interface HistoricoProps {
  empresaId: string
  onVerArquivo: (doc: DocumentoEmpresa) => void
  onDownload: (doc: DocumentoEmpresa) => void
  onDeletar: (doc: DocumentoEmpresa) => Promise<void>
  downloadingId: string | null
}

function HistoricoContratoSocial({ empresaId, onVerArquivo, onDownload, onDeletar, downloadingId }: HistoricoProps) {
  const [aberto, setAberto] = useState(false)
  const [loading, setLoading] = useState(false)
  const [historico, setHistorico] = useState<DocumentoEmpresa[]>([])
  const [error, setError] = useState<string | null>(null)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  async function carregar() {
    if (historico.length > 0) { setAberto(v => !v); return }
    setAberto(true)
    setLoading(true)
    try {
      const list = await empresasApi.historicoContratoSocial(empresaId)
      setHistorico(list)
    } catch {
      setError('Erro ao carregar histórico')
    } finally {
      setLoading(false)
    }
  }

  async function handleDeletarHistorico(doc: DocumentoEmpresa) {
    setDeletingId(doc.id)
    try {
      await onDeletar(doc)
      setHistorico(h => h.filter(d => d.id !== doc.id))
    } catch {
      setError('Erro ao remover versão anterior')
    } finally {
      setDeletingId(null)
    }
  }

  const anteriores = historico.filter(d => !d.versaoAtual)

  return (
    <div className="border border-ds-border rounded-xl overflow-hidden">
      <button
        onClick={carregar}
        className="w-full flex items-center gap-2 px-4 py-2.5 bg-ds-input hover:bg-primary-50 transition-colors text-left"
      >
        <History size={14} className="text-primary shrink-0" />
        <span className="flex-1 text-xs font-semibold text-ds-mid">Versões anteriores do Contrato Social</span>
        {aberto ? <ChevronUp size={14} className="text-ds-light" /> : <ChevronDown size={14} className="text-ds-light" />}
      </button>

      {aberto && (
        <div className="border-t border-ds-border">
          {error && <p className="text-xs text-red-500 px-4 py-2">{error}</p>}
          {loading && <div className="flex justify-center py-4"><Spinner size="sm" /></div>}
          {!loading && anteriores.length === 0 && (
            <p className="text-[11px] text-ds-light px-4 py-3">Nenhuma versão anterior registrada.</p>
          )}
          {anteriores.map((doc, i) => (
            <div key={doc.id} className="flex items-center gap-2 px-4 py-2 border-t border-ds-border/50 first:border-0">
              <div className="flex-1 min-w-0">
                <p className="text-[12px] text-ds-mid truncate">{doc.nomeArquivo}</p>
                <p className="text-[11px] text-ds-light">
                  Versão {anteriores.length - i} · {new Date(doc.createdAt).toLocaleDateString('pt-BR')}
                </p>
              </div>
              <button onClick={() => onVerArquivo(doc)}
                className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
                title="Visualizar">
                <Eye size={13} />
              </button>
              <button onClick={() => onDownload(doc)}
                disabled={downloadingId === doc.id}
                className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors disabled:opacity-50"
                title="Baixar">
                <Download size={13} className={downloadingId === doc.id ? 'animate-bounce' : ''} />
              </button>
              <button onClick={() => handleDeletarHistorico(doc)}
                disabled={deletingId === doc.id}
                className="p-1 rounded text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors disabled:opacity-50"
                title="Remover versão">
                <Trash2 size={13} />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ─── Modal principal ──────────────────────────────────────────────────────────

interface Props {
  empresa: Empresa
  onClose: () => void
}

export function DocumentosEmpresaModal({ empresa, onClose }: Props) {
  const { user } = useAuth()
  const canValidate = user?.realm_access?.roles.some(r => r === 'gestao' || r === 'operacao') ?? false

  const [selectedTipo, setSelectedTipo] = useState<TipoDocumentoEmpresa>('CONTRATO_SOCIAL')
  const [docs, setDocs] = useState<Partial<Record<TipoDocumentoEmpresa, DocumentoEmpresa[]>>>({})
  const [previews, setPreviews] = useState<Record<string, string>>({})
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState<TipoDocumentoEmpresa | null>(null)
  const [validating, setValidating] = useState<string | null>(null)
  const [rejectingDocId, setRejectingDocId] = useState<string | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const fileInputRef = useRef<HTMLInputElement>(null)
  const previewsRef = useRef(previews)
  previewsRef.current = previews

  useEffect(() => () => {
    Object.values(previewsRef.current).forEach(url => {
      if (url?.startsWith('blob:')) URL.revokeObjectURL(url)
    })
  }, [])

  useEffect(() => {
    empresasApi.listarDocumentos(empresa.id)
      .then(list => {
        const byTipo: Partial<Record<TipoDocumentoEmpresa, DocumentoEmpresa[]>> = {}
        for (const doc of list) {
          byTipo[doc.tipo] = [...(byTipo[doc.tipo] ?? []), doc]
        }
        setDocs(byTipo)
        for (const doc of list) {
          if (isImagem(doc.nomeArquivo)) {
            empresasApi.downloadDocumentoEmpresa(empresa.id, doc.id)
              .then(blob => {
                const url = URL.createObjectURL(blob)
                setPreviews(p => ({ ...p, [doc.id]: url }))
              })
              .catch(() => {})
          }
        }
      })
      .catch(() => setError('Erro ao carregar documentos'))
      .finally(() => setLoading(false))
  }, [empresa.id])

  async function handleUpload(tipo: TipoDocumentoEmpresa, file: File) {
    const err = validarArquivo(file)
    if (err) { setError(err); return }
    setError(null)
    setUploading(tipo)
    try {
      const doc = await empresasApi.uploadDocumento(empresa.id, tipo, file)
      if (isImagem(file.name)) {
        const url = URL.createObjectURL(file)
        setPreviews(p => ({ ...p, [doc.id]: url }))
      }
      // Contrato Social: substituir versão anterior na listagem local
      if (tipo === 'CONTRATO_SOCIAL') {
        setDocs(d => ({
          ...d,
          CONTRATO_SOCIAL: [doc],
        }))
      } else {
        setDocs(d => ({ ...d, [tipo]: [...(d[tipo] ?? []), doc] }))
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao enviar arquivo')
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

  function handleAdicionarMais(tipo: TipoDocumentoEmpresa) {
    setSelectedTipo(tipo)
    setTimeout(() => fileInputRef.current?.click(), 0)
  }

  async function handleAprovar(doc: DocumentoEmpresa) {
    setValidating(doc.id)
    setError(null)
    try {
      const updated = await empresasApi.validarDocumentoEmpresa(empresa.id, doc.id, { statusValidacao: 'APROVADO' })
      setDocs(d => ({
        ...d,
        [doc.tipo]: (d[doc.tipo] ?? []).map(x => x.id === updated.id ? updated : x),
      }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao aprovar documento')
    } finally {
      setValidating(null)
    }
  }

  async function handleConfirmarReprovacao() {
    if (!rejectingDocId) return
    const tipo = (Object.keys(docs) as TipoDocumentoEmpresa[]).find(k =>
      docs[k]?.some(d => d.id === rejectingDocId)
    )
    if (!tipo) return
    setValidating(rejectingDocId)
    setError(null)
    try {
      const updated = await empresasApi.validarDocumentoEmpresa(empresa.id, rejectingDocId, {
        statusValidacao: 'REPROVADO',
        motivoReprovacao: rejectReason.trim(),
      })
      setDocs(d => ({
        ...d,
        [tipo]: (d[tipo] ?? []).map(x => x.id === updated.id ? updated : x),
      }))
      setRejectingDocId(null)
      setRejectReason('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao reprovar documento')
    } finally {
      setValidating(null)
    }
  }

  async function handleDeletar(doc: DocumentoEmpresa) {
    setError(null)
    try {
      await empresasApi.deletarDocumentoEmpresa(empresa.id, doc.id)
      setPreviews(p => {
        const n = { ...p }
        if (n[doc.id]?.startsWith('blob:')) URL.revokeObjectURL(n[doc.id])
        delete n[doc.id]
        return n
      })
      setDocs(d => ({
        ...d,
        [doc.tipo]: (d[doc.tipo] ?? []).filter(x => x.id !== doc.id),
      }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao remover arquivo')
    }
  }

  async function salvarBlob(blob: Blob, nomeArquivo: string) {
    if ('showSaveFilePicker' in window) {
      try {
        const ext = nomeArquivo.includes('.') ? nomeArquivo.split('.').pop()!.toLowerCase() : ''
        const mimeMap: Record<string, string> = {
          pdf: 'application/pdf', jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png',
        }
        const mime = mimeMap[ext] ?? blob.type ?? 'application/octet-stream'
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const handle = await (window as any).showSaveFilePicker({
          suggestedName: nomeArquivo,
          types: [{ description: 'Arquivo', accept: { [mime]: ext ? [`.${ext}`] : [] } }],
        })
        const writable = await handle.createWritable()
        await writable.write(blob)
        await writable.close()
        return
      } catch (e: unknown) {
        if ((e as { name?: string })?.name === 'AbortError') return
      }
    }
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = nomeArquivo
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
  }

  async function handleVerArquivo(doc: DocumentoEmpresa) {
    try {
      const blob = await empresasApi.downloadDocumentoEmpresa(empresa.id, doc.id)
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank', 'noopener,noreferrer')
      setTimeout(() => URL.revokeObjectURL(url), 10000)
    } catch {
      setError('Não foi possível obter o arquivo')
    }
  }

  async function handleDownload(doc: DocumentoEmpresa) {
    setDownloadingId(doc.id)
    setError(null)
    try {
      const blob = await empresasApi.downloadDocumentoEmpresa(empresa.id, doc.id)
      await salvarBlob(blob, doc.nomeArquivo)
    } catch {
      setError('Não foi possível baixar o arquivo')
    } finally {
      setDownloadingId(null)
    }
  }

  const allDocs = TODOS_TIPOS.flatMap(t => docs[t] ?? [])
  const totalEnviados = allDocs.length
  const aprovados = allDocs.filter(d => d.statusValidacao === 'APROVADO').length

  return (
    <Modal open onClose={onClose} title="" size="lg">
      <div className="flex flex-col gap-4">

        {/* Header */}
        <div className="flex items-start justify-between gap-4 -mt-1">
          <div>
            <h2 className="text-base font-bold text-ds-text">Documentos da Empresa</h2>
            <p className="text-xs text-ds-light mt-0.5">{empresa.razaoSocial}</p>
          </div>
          <div className="shrink-0 flex flex-col items-end gap-2">
            <span className={`px-3 py-1 rounded-full text-sm font-bold ${totalEnviados > 0 && aprovados === totalEnviados ? 'bg-green-50 text-green-600' : 'bg-amber-50 text-amber-700'}`}>
              {aprovados} de {totalEnviados} aprovados
            </span>
            <div className="w-28 h-1.5 rounded-full bg-ds-border overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${totalEnviados > 0 && aprovados === totalEnviados ? 'bg-green-500' : 'bg-amber-400'}`}
                style={{ width: `${totalEnviados > 0 ? (aprovados / totalEnviados) * 100 : 0}%` }}
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
              onChange={e => setSelectedTipo(e.target.value as TipoDocumentoEmpresa)}
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

        {/* Lista de documentos por tipo */}
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
                  docs={docs[tipo] ?? []}
                  previews={previews}
                  uploadingThisTipo={uploading === tipo}
                  validating={validating}
                  canValidate={canValidate}
                  rejectingDocId={rejectingDocId}
                  rejectReason={rejectReason}
                  downloadingId={downloadingId}
                  onAprovar={handleAprovar}
                  onIniciarReprovacao={doc => { setRejectingDocId(doc.id); setRejectReason('') }}
                  onConfirmarReprovacao={handleConfirmarReprovacao}
                  onCancelarReprovacao={() => { setRejectingDocId(null); setRejectReason('') }}
                  onRejectReasonChange={setRejectReason}
                  onVerArquivo={handleVerArquivo}
                  onDownload={handleDownload}
                  onDeletar={handleDeletar}
                  onAdicionarMais={handleAdicionarMais}
                />
              ))}
            </div>
          )}
        </div>

        {/* Histórico de versões do Contrato Social */}
        <HistoricoContratoSocial
          empresaId={empresa.id}
          onVerArquivo={handleVerArquivo}
          onDownload={handleDownload}
          onDeletar={handleDeletar}
          downloadingId={downloadingId}
        />

        <div className="flex justify-end pt-1 border-t border-ds-border">
          <Button variant="outline" onClick={onClose}>Fechar</Button>
        </div>
      </div>
    </Modal>
  )
}
