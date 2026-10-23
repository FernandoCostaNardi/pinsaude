import { useRef, useState } from 'react'
import { X, Upload, FileText, FileCode2, Loader2, CheckCircle2, Trash2 } from 'lucide-react'
import { Alert } from '@pinsaude/ui'
import { ArquivosNota } from '../api/nfseApi'

// ─── Helpers ─────────────────────────────────────────────────────────────────

const TAMANHO_MAXIMO = 10 * 1024 * 1024

function hojeIso(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function formatTamanho(bytes: number): string {
  return bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function primeiroTexto(doc: Document, tags: string[]): string | null {
  for (const tag of tags) {
    const el = doc.getElementsByTagNameNS('*', tag)[0] ?? doc.getElementsByTagName(tag)[0]
    const txt = el?.textContent?.trim()
    if (txt) return txt
  }
  return null
}

// Lê número e data da NFS-e do XML para pré-preencher o formulário. Cobre o padrão nacional
// (nNFSe / dhProc / dhEmi) e o ABRASF usado pela maioria das prefeituras (Numero / DataEmissao).
async function extrairDadosXml(arquivo: File): Promise<{ numero: string | null; data: string | null }> {
  try {
    const doc = new DOMParser().parseFromString(await arquivo.text(), 'application/xml')
    if (doc.getElementsByTagName('parsererror').length > 0) return { numero: null, data: null }
    const numero = primeiroTexto(doc, ['nNFSe', 'NumeroNfse', 'Numero', 'NumeroNota'])
    const dataRaw = primeiroTexto(doc, ['dhProc', 'DataEmissao', 'dhEmi', 'DataEmissaoNfse'])
    const data = dataRaw && /^\d{4}-\d{2}-\d{2}/.test(dataRaw) ? dataRaw.slice(0, 10) : null
    return { numero: numero && numero.length <= 20 ? numero : null, data }
  } catch {
    return { numero: null, data: null }
  }
}

// ─── Seletor de arquivo ──────────────────────────────────────────────────────

function SeletorArquivo({
  tipo, arquivo, jaAnexado, onSelecionar, onErro,
}: {
  tipo: 'pdf' | 'xml'
  arquivo: File | null
  jaAnexado?: boolean
  onSelecionar: (f: File | null) => void
  onErro: (msg: string) => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [arrastando, setArrastando] = useState(false)
  const Icon = tipo === 'pdf' ? FileText : FileCode2
  const rotulo = tipo === 'pdf' ? 'PDF da nota (DANFSe)' : 'XML da nota'

  function validarEEscolher(f: File | undefined) {
    if (!f) return
    const ext = f.name.toLowerCase().split('.').pop()
    if (ext !== tipo) {
      onErro(`Selecione um arquivo .${tipo} para o campo "${rotulo}".`)
      return
    }
    if (f.size > TAMANHO_MAXIMO) {
      onErro(`O arquivo ${f.name} passa de 10 MB.`)
      return
    }
    onSelecionar(f)
  }

  return (
    <div>
      <p className="text-xs font-bold text-ds-mid mb-1">
        {rotulo}
        {jaAnexado && !arquivo && <span className="ml-1.5 font-medium text-green-600">· já anexado</span>}
      </p>
      {arquivo ? (
        <div className="flex items-center gap-3 rounded-xl border border-green-200 bg-green-50 px-3 py-2.5">
          <CheckCircle2 size={16} className="text-green-600 shrink-0" />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-ds-mid truncate">{arquivo.name}</p>
            <p className="text-[11px] text-ds-light">{formatTamanho(arquivo.size)}</p>
          </div>
          <button
            type="button"
            onClick={() => onSelecionar(null)}
            className="p-1.5 rounded-lg text-ds-light hover:text-red-600 hover:bg-white transition-colors"
            aria-label={`Remover ${rotulo}`}
          >
            <Trash2 size={14} />
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          onDragOver={e => { e.preventDefault(); setArrastando(true) }}
          onDragLeave={() => setArrastando(false)}
          onDrop={e => {
            e.preventDefault()
            setArrastando(false)
            validarEEscolher(e.dataTransfer.files[0])
          }}
          className={`w-full flex items-center gap-3 rounded-xl border-2 border-dashed px-3 py-3 text-left transition-colors ${
            arrastando ? 'border-primary bg-primary-50' : 'border-ds-border hover:border-primary hover:bg-ds-surface'
          }`}
        >
          <Icon size={18} className="text-ds-light shrink-0" />
          <span className="text-sm text-ds-light">
            {jaAnexado ? 'Substituir: clique ou arraste' : 'Clique ou arraste'} o arquivo .{tipo}
          </span>
          <Upload size={14} className="ml-auto text-ds-light shrink-0" />
        </button>
      )}
      <input
        ref={inputRef}
        type="file"
        accept={tipo === 'pdf' ? 'application/pdf,.pdf' : 'application/xml,text/xml,.xml'}
        className="hidden"
        onChange={e => {
          validarEEscolher(e.target.files?.[0])
          e.target.value = ''
        }}
      />
    </div>
  )
}

// ─── Modal ────────────────────────────────────────────────────────────────────

export interface DadosNotaEmitida {
  numeroNota: string
  dataEmissao: string
  arquivos: ArquivosNota
}

interface Props {
  // 'registrar': nota emitida no portal da prefeitura (número + data + arquivos);
  // 'anexar': nota já registrada, só anexa/substitui PDF e/ou XML.
  modo: 'registrar' | 'anexar'
  numeroAtual?: string | null
  temPdf?: boolean
  temXml?: boolean
  onClose: () => void
  onSalvar: (dados: DadosNotaEmitida) => Promise<void>
}

export function NotaEmitidaArquivosModal({ modo, numeroAtual, temPdf, temXml, onClose, onSalvar }: Props) {
  const [numeroNota, setNumeroNota] = useState('')
  const [dataEmissao, setDataEmissao] = useState(hojeIso())
  const [pdf, setPdf] = useState<File | null>(null)
  const [xml, setXml] = useState<File | null>(null)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [preenchidoDoXml, setPreenchidoDoXml] = useState(false)

  const registrar = modo === 'registrar'
  const faltaArquivo = !pdf && !xml
  const faltaNumero = registrar && !numeroNota.trim()
  const dataFutura = registrar && dataEmissao > hojeIso()
  const podeSalvar = !faltaArquivo && !faltaNumero && !!dataEmissao && !dataFutura && !salvando

  async function escolherXml(f: File | null) {
    setXml(f)
    setErro(null)
    if (!f || !registrar) return
    const { numero, data } = await extrairDadosXml(f)
    let preencheu = false
    if (numero && !numeroNota.trim()) { setNumeroNota(numero); preencheu = true }
    if (data && data <= hojeIso()) { setDataEmissao(data); preencheu = true }
    setPreenchidoDoXml(preencheu)
  }

  async function salvar() {
    if (!podeSalvar) return
    setSalvando(true)
    setErro(null)
    try {
      await onSalvar({ numeroNota: numeroNota.trim(), dataEmissao, arquivos: { pdf, xml } })
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Erro ao salvar os arquivos da nota')
      setSalvando(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={salvando ? undefined : onClose}>
      <div className="absolute inset-0 bg-black/50" />
      <div
        className="relative bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[94vh] flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-start justify-between px-5 py-4 border-b border-ds-border shrink-0">
          <div>
            <h2 className="text-base font-bold text-ds-mid">
              {registrar ? 'Registrar nota emitida' : 'Anexar arquivos da nota'}
            </h2>
            <p className="text-xs text-ds-light mt-0.5">
              {registrar
                ? 'Informe os dados da NFS-e emitida no portal da prefeitura e anexe o PDF e/ou o XML.'
                : `NFS-e nº ${numeroAtual ?? '—'}. O arquivo enviado substitui o anterior do mesmo tipo.`}
            </p>
          </div>
          <button
            onClick={onClose}
            disabled={salvando}
            className="p-1.5 rounded-lg text-ds-light hover:text-ds-mid hover:bg-ds-input transition-colors disabled:opacity-50"
          >
            <X size={16} />
          </button>
        </div>

        <div className="overflow-y-auto px-5 py-4 space-y-4">
          {erro && <Alert variant="error" onClose={() => setErro(null)}>{erro}</Alert>}

          {registrar && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold text-ds-mid mb-1">Número da NFS-e *</label>
                <input
                  value={numeroNota}
                  onChange={e => setNumeroNota(e.target.value)}
                  maxLength={20}
                  placeholder="Ex.: 2026000123"
                  className="w-full h-10 rounded-lg border border-ds-border bg-white text-sm text-ds-mid px-3 focus:outline-none focus:border-primary"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-ds-mid mb-1">Data de emissão *</label>
                <input
                  type="date"
                  value={dataEmissao}
                  max={hojeIso()}
                  onChange={e => setDataEmissao(e.target.value)}
                  className="w-full h-10 rounded-lg border border-ds-border bg-white text-sm text-ds-mid px-3 focus:outline-none focus:border-primary"
                />
              </div>
              {preenchidoDoXml && (
                <p className="sm:col-span-2 text-[11px] text-primary -mt-1">
                  Número e data preenchidos a partir do XML. Confira antes de salvar.
                </p>
              )}
              {dataFutura && (
                <p className="sm:col-span-2 text-[11px] text-red-600 -mt-1">A data de emissão não pode ser futura.</p>
              )}
            </div>
          )}

          <SeletorArquivo tipo="pdf" arquivo={pdf} jaAnexado={temPdf} onSelecionar={f => { setPdf(f); setErro(null) }} onErro={setErro} />
          <SeletorArquivo tipo="xml" arquivo={xml} jaAnexado={temXml} onSelecionar={escolherXml} onErro={setErro} />

          <p className="text-[11px] text-ds-light">
            {registrar
              ? 'Anexe ao menos um dos dois arquivos. Dá para incluir o outro depois, na própria nota.'
              : 'Selecione ao menos um arquivo.'}
            {' '}Máximo de 10 MB por arquivo.
          </p>
        </div>

        <div className="flex justify-end gap-2 px-5 py-3.5 border-t border-ds-border shrink-0">
          <button
            onClick={onClose}
            disabled={salvando}
            className="px-4 py-2 rounded-lg border border-ds-border text-ds-mid text-sm font-medium hover:bg-ds-surface disabled:opacity-50 transition-colors"
          >
            Cancelar
          </button>
          <button
            onClick={salvar}
            disabled={!podeSalvar}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-primary text-white text-sm font-semibold hover:bg-primary-600 disabled:opacity-50 transition-colors"
          >
            {salvando ? <Loader2 size={14} className="animate-spin" /> : <Upload size={14} />}
            {salvando ? 'Salvando...' : registrar ? 'Registrar nota emitida' : 'Salvar arquivos'}
          </button>
        </div>
      </div>
    </div>
  )
}
