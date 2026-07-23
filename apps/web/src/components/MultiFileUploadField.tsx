import { useRef, useState } from 'react'
import { CheckCircle2, Loader2, Upload } from 'lucide-react'

export interface UploadedFileRef {
  id: string
  nomeArquivo: string
}

interface MultiFileUploadFieldProps<T extends string> {
  label: string
  tipo: T
  arquivos: UploadedFileRef[]
  onUpload: (tipo: T, file: File) => Promise<unknown>
  /** Permite mais de um arquivo por tipo (ex.: títulos de especialista). Default true. */
  multiplos?: boolean
  accept?: string
  hint?: string
}

/**
 * Campo de upload com drag-and-drop, extraído do padrão de DocumentosModal.tsx (EPIC-03.4)
 * para aceitar uma função de upload injetada — funciona tanto com módulos de API autenticados
 * (medicosApi) quanto com o módulo público sem token (candidaturaMedicoApi), usado nas etapas
 * de documentos do wizard de auto-cadastro (EPIC-14.6/14.7).
 */
export function MultiFileUploadField<T extends string>({
  label, tipo, arquivos, onUpload, multiplos = true, accept = '.pdf,.jpg,.jpeg,.png', hint,
}: MultiFileUploadFieldProps<T>) {
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const podeAdicionarMais = multiplos || arquivos.length === 0

  function handleFile(file: File | undefined) {
    if (!file || !podeAdicionarMais) return
    setUploading(true)
    Promise.resolve(onUpload(tipo, file)).finally(() => setUploading(false))
  }

  return (
    <div className="flex flex-col gap-2">
      <div
        onDragOver={e => { if (podeAdicionarMais) { e.preventDefault(); setDragging(true) } }}
        onDragLeave={() => setDragging(false)}
        onDrop={e => { e.preventDefault(); setDragging(false); handleFile(e.dataTransfer.files[0]) }}
        onClick={() => podeAdicionarMais && inputRef.current?.click()}
        className={[
          'flex items-center gap-3 px-4 py-3 rounded-xl border-2 border-dashed transition-colors',
          !podeAdicionarMais
            ? 'opacity-60 cursor-not-allowed border-gray-200'
            : dragging
              ? 'border-primary bg-primary-50 cursor-pointer'
              : 'border-gray-200 hover:border-primary hover:bg-primary-50 cursor-pointer',
        ].join(' ')}
      >
        {uploading ? (
          <Loader2 className="animate-spin text-primary shrink-0" size={18} />
        ) : arquivos.length > 0 ? (
          <CheckCircle2 className="text-secondary-600 shrink-0" size={18} />
        ) : (
          <Upload className={['shrink-0', dragging ? 'text-primary' : 'text-gray-400'].join(' ')} size={18} />
        )}
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-gray-700">{label}</p>
          <p className="text-xs text-gray-400 truncate">
            {uploading
              ? 'Enviando...'
              : arquivos.length > 0
                ? (multiplos
                    ? `${arquivos.length} arquivo(s) enviado(s) — clique para adicionar mais`
                    : `Enviado: ${arquivos[0].nomeArquivo}`)
                : (hint ?? 'Clique ou arraste o arquivo aqui (PDF, JPG, PNG)')}
          </p>
        </div>
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          className="hidden"
          onChange={e => { handleFile(e.target.files?.[0]); e.target.value = '' }}
        />
      </div>
      {arquivos.length > 0 && multiplos && (
        <ul className="flex flex-col gap-1 pl-1">
          {arquivos.map(a => (
            <li key={a.id} className="flex items-center gap-1.5 text-xs text-gray-500">
              <CheckCircle2 size={12} className="text-secondary-600 shrink-0" />
              <span className="truncate">{a.nomeArquivo}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
