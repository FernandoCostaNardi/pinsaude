import { useCallback, useEffect, useRef, useState } from 'react'
import type { DragEvent, ElementType } from 'react'
import {
  Upload, FileSpreadsheet, CheckCircle2, XCircle, AlertTriangle,
  Loader2, ArrowRight, History, Banknote, TrendingUp, TrendingDown,
  RefreshCw, Calendar, Building2,
} from 'lucide-react'
import {
  uploadExtrato, listarExtratos,
  type BancoEnum, type ExtratoResponse,
} from '../api/conciliacaoApi'
import { useAuth } from '../auth/AuthContext'

// ─── Client-side preview types ───────────────────────────────────────────────

interface LancamentoPreview {
  data: string
  descricao: string
  valor: number
  tipo: 'CREDITO' | 'DEBITO'
}

interface ExtratoPreview {
  lancamentos: LancamentoPreview[]
  total: number
  totalCreditos: number
  totalDebitos: number
  periodoInicio: string | null
  periodoFim: string | null
}

// ─── Parsers (client-side, somente preview — parsing real é feito pelo backend)

function parseBRDate(str: string): string | null {
  const m = str.trim().match(/^(\d{2})\/(\d{2})\/(\d{4})$/)
  return m ? `${m[3]}-${m[2]}-${m[1]}` : null
}

function buildSummary(all: LancamentoPreview[]): ExtratoPreview {
  const creditos = all.filter(l => l.tipo === 'CREDITO').reduce((s, l) => s + l.valor, 0)
  const debitos  = all.filter(l => l.tipo === 'DEBITO').reduce((s, l) => s + l.valor, 0)
  const datas = all.map(l => l.data).filter(Boolean).sort()
  return {
    lancamentos: all.slice(0, 10),
    total: all.length,
    totalCreditos: creditos,
    totalDebitos: debitos,
    periodoInicio: datas[0] ?? null,
    periodoFim: datas[datas.length - 1] ?? null,
  }
}

function parseInterCsv(text: string): ExtratoPreview {
  const clean = text.charCodeAt(0) === 0xFEFF ? text.slice(1) : text
  const lines = clean.split('\n').map(l => l.trim()).filter(Boolean)
  const all: LancamentoPreview[] = []
  for (const line of lines.slice(1)) {
    const cols = line.split(';')
    if (cols.length < 4) continue
    const data = parseBRDate(cols[0])
    if (!data) continue
    const tipo: 'CREDITO' | 'DEBITO' =
      cols[1].trim().toLowerCase() === 'entrada' ? 'CREDITO' : 'DEBITO'
    const valorStr = cols[3].replace(/[^0-9,]/g, '').replace(',', '.')
    const valor = Math.abs(Math.round(parseFloat(valorStr || '0') * 100))
    all.push({ data, descricao: cols[2].trim(), valor, tipo })
  }
  return buildSummary(all)
}

function splitCsvLine(line: string): string[] {
  const result: string[] = []
  let cur = '', inQ = false
  for (const ch of line) {
    if (ch === '"') inQ = !inQ
    else if (ch === ',' && !inQ) { result.push(cur); cur = '' }
    else cur += ch
  }
  result.push(cur)
  return result.map(s => s.replace(/^"|"$/g, ''))
}

function parseBtgCsv(text: string): ExtratoPreview {
  const clean = text.charCodeAt(0) === 0xFEFF ? text.slice(1) : text
  const lines = clean.split('\n').map(l => l.trim()).filter(Boolean)
  const all: LancamentoPreview[] = []
  for (const line of lines.slice(1)) {
    const cols = splitCsvLine(line)
    if (cols.length < 3) continue
    const data = parseBRDate(cols[0])
    if (!data) continue
    const rawVal = parseFloat(cols[2].replace(/\./g, '').replace(',', '.').replace(/[^0-9.-]/g, ''))
    if (isNaN(rawVal)) continue
    const tipo: 'CREDITO' | 'DEBITO' = rawVal >= 0 ? 'CREDITO' : 'DEBITO'
    all.push({ data, descricao: cols[1].trim(), valor: Math.abs(Math.round(rawVal * 100)), tipo })
  }
  return buildSummary(all)
}

function parseOfx(text: string): ExtratoPreview {
  const all: LancamentoPreview[] = []

  // OFX 2.x XML
  const xmlMatches = [...text.matchAll(/<STMTTRN>([\s\S]*?)<\/STMTTRN>/gi)]
  if (xmlMatches.length > 0) {
    for (const m of xmlMatches) {
      const block = m[1]
      const amt = parseFloat(block.match(/<TRNAMT>([\d.-]+)/i)?.[1] ?? 'NaN')
      if (isNaN(amt)) continue
      const dtRaw = block.match(/<DTPOSTED>(\d{8})/i)?.[1]
      const name = (block.match(/<NAME>([^\n<]+)/i) ?? block.match(/<MEMO>([^\n<]+)/i))?.[1] ?? 'Lançamento'
      const data = dtRaw ? `${dtRaw.slice(0, 4)}-${dtRaw.slice(4, 6)}-${dtRaw.slice(6, 8)}` : ''
      all.push({ data, descricao: name.trim(), valor: Math.abs(Math.round(amt * 100)), tipo: amt >= 0 ? 'CREDITO' : 'DEBITO' })
    }
    return buildSummary(all)
  }

  // OFX 1.x SGML
  let inTrn = false, dtmp = '', atmp = 0, ntmp = '', ttmp: 'CREDITO' | 'DEBITO' = 'CREDITO'
  for (const raw of text.split('\n')) {
    const line = raw.trim()
    if (line === '<STMTTRN>') { inTrn = true; dtmp = ''; atmp = 0; ntmp = ''; continue }
    if (line === '</STMTTRN>') {
      if (inTrn && dtmp) all.push({ data: dtmp, descricao: ntmp || 'Lançamento', valor: Math.abs(Math.round(atmp * 100)), tipo: ttmp })
      inTrn = false; continue
    }
    if (!inTrn) continue
    const mLine = line.match(/^<([A-Z]+)>(.+)$/)
    if (!mLine) continue
    const [, tag, val] = mLine
    if (tag === 'TRNAMT') { atmp = parseFloat(val); ttmp = atmp >= 0 ? 'CREDITO' : 'DEBITO' }
    if (tag === 'DTPOSTED') { const d = val.slice(0, 8); dtmp = `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}` }
    if (tag === 'NAME' || tag === 'MEMO') ntmp = val.trim()
  }
  return buildSummary(all)
}

function parseArquivo(arquivo: File, banco: BancoEnum): Promise<ExtratoPreview> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = e => {
      const text = e.target?.result as string
      try {
        if (banco === 'INTER') resolve(parseInterCsv(text))
        else if (banco === 'BTG') resolve(parseBtgCsv(text))
        else resolve(parseOfx(text))
      } catch (err) { reject(err) }
    }
    reader.onerror = () => reject(new Error('Erro ao ler arquivo'))
    reader.readAsText(arquivo, 'ISO-8859-1')
  })
}

// ─── Formatting helpers ───────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [a, m, d] = iso.split('-')
  return `${d}/${m}/${a}`
}

function fmtDatetime(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function SummaryCard({ icon: Icon, label, value, iconBg, iconColor }: {
  icon: ElementType; label: string; value: string; iconBg: string; iconColor: string
}) {
  return (
    <div className="rounded-xl border border-ds-border bg-ds-surface p-3 flex items-center gap-3">
      <div className={`w-9 h-9 rounded-lg ${iconBg} flex items-center justify-center shrink-0`}>
        <Icon size={16} className={iconColor} />
      </div>
      <div className="min-w-0">
        <p className="text-xs font-semibold text-ds-mid truncate">{label}</p>
        <p className="text-sm font-black text-ds-text leading-tight truncate">{value}</p>
      </div>
    </div>
  )
}

const BANCO_LABELS: Record<BancoEnum, string> = {
  INTER: 'Banco Inter',
  BTG: 'BTG Pactual',
  OUTRO: 'Outro (OFX)',
}

const STATUS_CFG: Record<string, { label: string; cls: string }> = {
  OK:          { label: 'Importado',   cls: 'bg-green-50 text-green-700' },
  PROCESSANDO: { label: 'Processando', cls: 'bg-primary-50 text-primary' },
  ERRO:        { label: 'Erro',        cls: 'bg-red-50 text-red-600' },
}

function ExtratoRow({ extrato }: { extrato: ExtratoResponse }) {
  const cfg = STATUS_CFG[extrato.statusImportacao] ?? { label: extrato.statusImportacao, cls: 'bg-gray-100 text-gray-500' }
  return (
    <div className="flex items-center gap-4 px-5 py-3.5 hover:bg-ds-surface/50 transition-colors">
      <div className="w-8 h-8 rounded-lg bg-primary-50 flex items-center justify-center shrink-0">
        <Building2 size={15} className="text-primary" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-semibold text-sm text-ds-text truncate">{extrato.nomeArquivo}</span>
          <span className="text-xs font-semibold text-primary bg-primary-50 px-1.5 py-0.5 rounded shrink-0">
            {BANCO_LABELS[extrato.banco] ?? extrato.banco}
          </span>
        </div>
        <p className="text-xs text-ds-mid mt-0.5">
          {fmtDate(extrato.periodoInicio)} – {fmtDate(extrato.periodoFim)}
          {' · '}{extrato.totalLancamentos} lançamentos
          {' · '}{fmtDatetime(extrato.dataUpload)}
        </p>
      </div>
      <span className={`text-xs font-semibold px-2 py-1 rounded-md shrink-0 ${cfg.cls}`}>
        {cfg.label}
      </span>
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

export function ConciliacaoUploadPage() {
  const { user } = useAuth()

  const [banco, setBanco] = useState<BancoEnum>('INTER')
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [preview, setPreview] = useState<ExtratoPreview | null>(null)
  const [dataInicio, setDataInicio] = useState('')
  const [dataFim, setDataFim] = useState('')
  const [alerta, setAlerta] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const [importando, setImportando] = useState(false)
  const [progresso, setProgresso] = useState(0)
  const [importacaoOk, setImportacaoOk] = useState(false)
  const [extratos, setExtratos] = useState<ExtratoResponse[]>([])
  const [loadingExtratos, setLoadingExtratos] = useState(false)

  const fileInputRef = useRef<HTMLInputElement>(null)
  const progressTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const carregarExtratos = useCallback(async () => {
    setLoadingExtratos(true)
    try { setExtratos(await listarExtratos()) }
    catch { /* silencioso */ }
    finally { setLoadingExtratos(false) }
  }, [])

  useEffect(() => { carregarExtratos() }, [carregarExtratos])

  const processarArquivo = useCallback(async (file: File, bancoSel: BancoEnum) => {
    setErro(null)
    setAlerta(null)
    setArquivo(file)
    try {
      const pv = await parseArquivo(file, bancoSel)
      setPreview(pv)
      if (pv.periodoInicio) setDataInicio(pv.periodoInicio)
      if (pv.periodoFim) setDataFim(pv.periodoFim)
      const dup = extratos.find(e =>
        e.banco === bancoSel &&
        e.periodoInicio === pv.periodoInicio &&
        e.periodoFim === pv.periodoFim,
      )
      if (dup) setAlerta('Atenção: já existe um extrato importado para este banco e período. Lançamentos duplicados serão ignorados automaticamente.')
    } catch (e: unknown) {
      setErro(`Erro ao processar arquivo: ${e instanceof Error ? e.message : 'formato inválido'}`)
      setPreview(null)
    }
  }, [extratos])

  const handleFile = useCallback((file: File) => {
    processarArquivo(file, banco)
  }, [banco, processarArquivo])

  const handleDrop = useCallback((e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) handleFile(file)
  }, [handleFile])

  const resetForm = useCallback(() => {
    setArquivo(null)
    setPreview(null)
    setDataInicio('')
    setDataFim('')
    setAlerta(null)
    setErro(null)
    setProgresso(0)
    setImportacaoOk(false)
  }, [])

  const importar = async () => {
    if (!arquivo || !dataInicio || !dataFim) return
    setErro(null)
    setImportando(true)
    setProgresso(10)

    let pct = 10
    progressTimerRef.current = setInterval(() => {
      pct = Math.min(pct + 7, 82)
      setProgresso(pct)
    }, 350)

    try {
      await uploadExtrato(arquivo, banco, dataInicio, dataFim)
      clearInterval(progressTimerRef.current!)
      setProgresso(95)
      setTimeout(() => {
        setProgresso(100)
        setImportando(false)
        setImportacaoOk(true)
        carregarExtratos()
      }, 600)
    } catch (e: unknown) {
      clearInterval(progressTimerRef.current!)
      setImportando(false)
      setProgresso(0)
      const msg = e instanceof Error ? e.message : 'Erro ao importar extrato'
      if (msg.includes('409')) setAlerta('Este extrato já foi importado anteriormente.')
      else setErro(msg)
    }
  }

  useEffect(() => () => {
    if (progressTimerRef.current) clearInterval(progressTimerRef.current)
  }, [])

  const roles = user?.realm_access?.roles ?? []
  if (!['operacao', 'gestao', 'financeiro', 'contabil'].some(r => roles.includes(r))) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-sm text-ds-mid">Acesso restrito. Perfil sem permissão para importar extratos.</p>
      </div>
    )
  }

  const showProgress = importando || (progresso > 0 && progresso < 100)
  const showSuccess = progresso === 100 && importacaoOk

  return (
    <div className="flex-1 overflow-auto p-5 space-y-6">

      {/* Header */}
      <div>
        <h1 className="text-2xl font-black text-ds-text">Upload de Extrato Bancário</h1>
        <p className="text-sm text-ds-mid mt-1">
          Importe extratos CSV ou OFX para conciliação automática com as notas fiscais emitidas.
        </p>
      </div>

      {/* Upload card */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm p-6 space-y-5">

        {/* Banco + Período */}
        <div className="flex flex-wrap items-end gap-4">
          <div>
            <label className="block text-xs font-semibold text-ds-mid mb-1">Banco</label>
            <select
              value={banco}
              onChange={e => { setBanco(e.target.value as BancoEnum); resetForm() }}
              className="border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/40"
            >
              <option value="INTER">Banco Inter</option>
              <option value="BTG">BTG Pactual</option>
              <option value="OUTRO">Outro (OFX / QFX)</option>
            </select>
          </div>
          {preview && !showSuccess && (
            <>
              <div>
                <label className="block text-xs font-semibold text-ds-mid mb-1">Período — início</label>
                <input
                  type="date" value={dataInicio}
                  onChange={e => setDataInicio(e.target.value)}
                  className="border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/40"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-ds-mid mb-1">Período — fim</label>
                <input
                  type="date" value={dataFim}
                  onChange={e => setDataFim(e.target.value)}
                  className="border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/40"
                />
              </div>
            </>
          )}
        </div>

        {/* Drop zone */}
        {!preview && !showProgress && !showSuccess && (
          <div
            onDragOver={e => { e.preventDefault(); setDragging(true) }}
            onDragLeave={() => setDragging(false)}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            className={`flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed cursor-pointer py-16 px-8 transition-colors ${
              dragging
                ? 'border-primary bg-primary-50'
                : 'border-ds-border hover:border-primary/50 hover:bg-ds-surface'
            }`}
          >
            <div className="w-14 h-14 rounded-full bg-primary-50 flex items-center justify-center">
              <Upload size={26} className="text-primary" />
            </div>
            <div className="text-center">
              <p className="font-semibold text-ds-text">Arraste o arquivo aqui ou clique para selecionar</p>
              <p className="text-sm text-ds-light mt-1">CSV (Inter, BTG) · OFX · QFX — máx. 20 MB</p>
            </div>
            <input
              ref={fileInputRef} type="file" accept=".csv,.ofx,.qfx"
              className="hidden"
              onChange={e => { const f = e.target.files?.[0]; if (f) handleFile(f) }}
            />
          </div>
        )}

        {/* Parsing spinner */}
        {arquivo && !preview && !showProgress && !erro && (
          <div className="flex items-center gap-2 text-sm text-ds-mid">
            <Loader2 size={15} className="animate-spin text-primary" />
            <span>Analisando <strong>{arquivo.name}</strong>...</span>
          </div>
        )}

        {/* Alerts */}
        {alerta && (
          <div className="flex items-start gap-2 rounded-lg bg-yellow-50 border border-yellow-200 text-yellow-800 px-4 py-3 text-sm">
            <AlertTriangle size={16} className="mt-0.5 shrink-0 text-yellow-500" />
            <span>{alerta}</span>
          </div>
        )}
        {erro && (
          <div className="flex items-start gap-2 rounded-lg bg-red-50 border border-red-200 text-red-700 px-4 py-3 text-sm">
            <XCircle size={16} className="mt-0.5 shrink-0" />
            <span>{erro}</span>
          </div>
        )}

        {/* Preview */}
        {preview && !showProgress && !showSuccess && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm">
                <FileSpreadsheet size={16} className="text-primary" />
                <span className="font-semibold text-ds-text">{arquivo?.name}</span>
              </div>
              <button onClick={resetForm} className="text-xs text-ds-light hover:text-ds-mid transition-colors">
                Trocar arquivo
              </button>
            </div>

            {/* Summary cards */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <SummaryCard icon={FileSpreadsheet} label="Lançamentos" value={`${preview.total}`}
                iconBg="bg-primary-50" iconColor="text-primary" />
              <SummaryCard icon={TrendingUp} label="Total Créditos" value={formatBRL(preview.totalCreditos)}
                iconBg="bg-green-50" iconColor="text-green-600" />
              <SummaryCard icon={TrendingDown} label="Total Débitos" value={formatBRL(preview.totalDebitos)}
                iconBg="bg-red-50" iconColor="text-red-500" />
              <SummaryCard icon={Calendar} label="Período detectado"
                value={preview.periodoInicio && preview.periodoFim
                  ? `${fmtDate(preview.periodoInicio)} – ${fmtDate(preview.periodoFim)}`
                  : 'Não detectado'}
                iconBg="bg-gray-50" iconColor="text-ds-mid" />
            </div>

            {/* Preview table */}
            {preview.lancamentos.length > 0 && (
              <div className="overflow-x-auto rounded-lg border border-ds-border">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-ds-surface border-b border-ds-border text-xs font-semibold text-ds-mid">
                      <th className="px-3 py-2.5 text-left">Data</th>
                      <th className="px-3 py-2.5 text-left">Descrição</th>
                      <th className="px-3 py-2.5 text-right">Valor</th>
                      <th className="px-3 py-2.5 text-center">Tipo</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ds-border">
                    {preview.lancamentos.map((l, i) => (
                      <tr key={i} className="hover:bg-ds-surface/50 transition-colors">
                        <td className="px-3 py-2.5 text-ds-mid whitespace-nowrap">{fmtDate(l.data)}</td>
                        <td className="px-3 py-2.5 text-ds-text max-w-xs truncate">{l.descricao}</td>
                        <td className={`px-3 py-2.5 text-right font-semibold whitespace-nowrap ${l.tipo === 'CREDITO' ? 'text-green-600' : 'text-red-500'}`}>
                          {formatBRL(l.valor)}
                        </td>
                        <td className="px-3 py-2.5 text-center">
                          <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold ${
                            l.tipo === 'CREDITO' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-600'
                          }`}>
                            {l.tipo === 'CREDITO' ? <TrendingUp size={10} /> : <TrendingDown size={10} />}
                            {l.tipo === 'CREDITO' ? 'Crédito' : 'Débito'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {preview.total > 10 && (
                  <div className="px-4 py-2 text-xs text-ds-light bg-ds-surface border-t border-ds-border text-center">
                    Exibindo os primeiros 10 de {preview.total} lançamentos detectados
                  </div>
                )}
              </div>
            )}

            {/* Import button */}
            <div className="flex items-center justify-end gap-4 pt-1">
              {(!dataInicio || !dataFim) && (
                <p className="text-xs text-amber-600">Informe o período antes de importar.</p>
              )}
              <button
                onClick={importar}
                disabled={!dataInicio || !dataFim}
                className="flex items-center gap-2 px-6 py-2.5 bg-primary text-white text-sm font-semibold rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Importar extrato
                <ArrowRight size={16} />
              </button>
            </div>
          </div>
        )}

        {/* Progress bar */}
        {showProgress && (
          <div className="space-y-3 py-2">
            <div className="flex items-center justify-between text-sm">
              <span className="font-semibold text-ds-text flex items-center gap-2">
                <Loader2 size={14} className="animate-spin text-primary" />
                {progresso < 85 ? 'Enviando e processando arquivo...' : 'Disparando matching automático...'}
              </span>
              <span className="text-ds-mid font-semibold tabular-nums">{progresso}%</span>
            </div>
            <div className="w-full bg-ds-surface rounded-full h-3 overflow-hidden">
              <div
                className="h-full rounded-full bg-primary transition-all duration-500 ease-out"
                style={{ width: `${progresso}%` }}
              />
            </div>
            <p className="text-xs text-ds-light">
              O matching automático continuará em background — você pode fechar esta tela após o upload.
            </p>
          </div>
        )}

        {/* Success */}
        {showSuccess && (
          <div className="flex items-start gap-3 rounded-xl bg-green-50 border border-green-200 text-green-700 px-5 py-4">
            <CheckCircle2 size={20} className="shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="font-bold text-sm">Extrato importado com sucesso!</p>
              <p className="text-xs mt-0.5">
                O motor de matching foi disparado e está processando os lançamentos em background.
              </p>
            </div>
            <button
              onClick={resetForm}
              className="text-xs font-semibold underline underline-offset-2 hover:text-green-800 transition-colors shrink-0 mt-0.5"
            >
              Importar outro
            </button>
          </div>
        )}
      </div>

      {/* History */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm">
        <div className="flex items-center justify-between px-5 py-4 border-b border-ds-border">
          <h2 className="font-bold text-ds-text flex items-center gap-2">
            <History size={16} className="text-ds-mid" />
            Histórico de Extratos Importados
          </h2>
          <button
            onClick={carregarExtratos}
            disabled={loadingExtratos}
            className="flex items-center gap-1.5 text-xs text-ds-mid hover:text-ds-text transition-colors"
          >
            <RefreshCw size={13} className={loadingExtratos ? 'animate-spin' : ''} />
            Atualizar
          </button>
        </div>

        {loadingExtratos && extratos.length === 0 ? (
          <div className="flex items-center justify-center py-14 gap-2 text-ds-light">
            <Loader2 size={18} className="animate-spin" />
            <span className="text-sm">Carregando...</span>
          </div>
        ) : extratos.length === 0 ? (
          <div className="flex flex-col items-center py-16 gap-3 text-ds-light">
            <Banknote size={32} className="text-ds-border" />
            <p className="text-sm">Nenhum extrato importado ainda.</p>
          </div>
        ) : (
          <div className="divide-y divide-ds-border">
            {extratos.map(e => <ExtratoRow key={e.id} extrato={e} />)}
          </div>
        )}
      </div>
    </div>
  )
}
