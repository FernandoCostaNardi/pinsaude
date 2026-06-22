import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  FileText, CheckCircle2, Clock, XCircle, AlertTriangle, ShieldCheck,
  Loader2, Search, RefreshCw, Eye,
} from 'lucide-react'
import { Spinner, Alert, Table, THead, TBody, TRow, TH, TD } from '@pinsaude/ui'
import { listarNotas, NotaFiscal, StatusNota } from '../api/nfseApi'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

// ─── Status badge ─────────────────────────────────────────────────────────────

const STATUS_CFG: Record<StatusNota, { label: string; cls: string; Icon: React.ElementType }> = {
  PENDENTE:                  { label: 'Na fila',            cls: 'bg-blue-50 text-blue-700',    Icon: Clock          },
  PROCESSANDO:               { label: 'Processando',        cls: 'bg-primary-50 text-primary',  Icon: Loader2        },
  EMITIDA:                   { label: 'Emitida',            cls: 'bg-green-50 text-green-700',  Icon: CheckCircle2   },
  CANCELADA:                 { label: 'Cancelada',          cls: 'bg-gray-100 text-gray-500',   Icon: XCircle        },
  ERRO:                      { label: 'Erro',               cls: 'bg-red-50 text-red-700',      Icon: XCircle        },
  REJEITADA:                 { label: 'Rejeitada',          cls: 'bg-red-50 text-red-700',      Icon: XCircle        },
  AGUARDANDO_EMISSAO_MANUAL: { label: 'Emissão Manual',    cls: 'bg-orange-50 text-orange-700',Icon: AlertTriangle  },
  AGUARDANDO_VALIDACAO:      { label: 'Aguard. Validação', cls: 'bg-yellow-50 text-yellow-700',Icon: ShieldCheck    },
}

function StatusBadge({ status }: { status: StatusNota }) {
  const { label, cls, Icon } = STATUS_CFG[status]
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold ${cls}`}>
      <Icon size={11} className={status === 'PROCESSANDO' ? 'animate-spin' : ''} />
      {label}
    </span>
  )
}

// ─── Página ───────────────────────────────────────────────────────────────────

export function NotasPage() {
  const navigate = useNavigate()
  const [notas, setNotas] = useState<NotaFiscal[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [q, setQ] = useState('')

  const carregar = async () => {
    setLoading(true)
    setErro(null)
    try {
      setNotas(await listarNotas())
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro ao carregar notas')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { carregar() }, [])

  const filtered = notas.filter(n => {
    if (!q) return true
    const qLow = q.toLowerCase()
    return (
      n.competencia.includes(q) ||
      n.status.toLowerCase().includes(qLow) ||
      (n.numeroNota ?? '').toLowerCase().includes(qLow) ||
      (n.observacoes ?? '').toLowerCase().includes(qLow)
    )
  })

  const stats = {
    total: notas.length,
    emitidas: notas.filter(n => n.status === 'EMITIDA').length,
    pendentes: notas.filter(n => n.status === 'PENDENTE' || n.status === 'PROCESSANDO').length,
    atencao: notas.filter(n =>
      n.status === 'ERRO' || n.status === 'REJEITADA' ||
      n.status === 'AGUARDANDO_VALIDACAO' || n.status === 'AGUARDANDO_EMISSAO_MANUAL'
    ).length,
  }

  return (
    <div className="flex-1 overflow-auto">
      {/* Header */}
      <div className="bg-white border-b border-ds-border px-6 py-5">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold text-ds-mid">Notas Fiscais</h1>
            <p className="text-sm text-ds-light mt-0.5">Histórico de emissões NFS-e</p>
          </div>
          <button
            onClick={carregar}
            className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-ds-border text-ds-light hover:text-ds-mid hover:bg-ds-input transition-colors text-sm"
          >
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
            Atualizar
          </button>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-4 gap-3 mt-4">
          {[
            { label: 'Total', value: stats.total, cls: 'text-ds-mid' },
            { label: 'Emitidas', value: stats.emitidas, cls: 'text-green-700' },
            { label: 'Em andamento', value: stats.pendentes, cls: 'text-primary' },
            { label: 'Atenção', value: stats.atencao, cls: 'text-orange-600' },
          ].map(({ label, value, cls }) => (
            <div key={label} className="bg-ds-surface rounded-xl px-4 py-3 text-center">
              <p className={`text-xl font-bold ${cls}`}>{value}</p>
              <p className="text-xs text-ds-light mt-0.5">{label}</p>
            </div>
          ))}
        </div>
      </div>

      <div className="px-6 py-5 space-y-4">
        {/* Filtro */}
        <div className="relative max-w-sm">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ds-light" />
          <input
            type="text"
            placeholder="Filtrar por competência, status, número..."
            value={q}
            onChange={e => setQ(e.target.value)}
            className="w-full pl-9 pr-3 py-2 rounded-lg border border-ds-border bg-white text-sm text-ds-mid placeholder:text-ds-light focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>

        {/* Conteúdo */}
        {erro && <Alert variant="error">{erro}</Alert>}

        {loading ? (
          <div className="flex items-center justify-center py-16">
            <Spinner />
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-16">
            <FileText size={40} className="mx-auto text-ds-light mb-3" />
            <p className="text-ds-mid font-medium">Nenhuma nota encontrada</p>
            <p className="text-ds-light text-sm mt-1">
              {q ? 'Tente outro filtro.' : 'Vá em Produções e clique em "Emitir NFS-e".'}
            </p>
          </div>
        ) : (
          <div className="bg-white rounded-2xl border border-ds-border overflow-hidden">
            <Table>
              <THead>
                <TRow>
                  <TH>Competência</TH>
                  <TH>Número</TH>
                  <TH>Status</TH>
                  <TH>Médico ID</TH>
                  <TH>Observações</TH>
                  <TH>Emitida em</TH>
                  <TH></TH>
                </TRow>
              </THead>
              <TBody>
                {filtered.map(nota => (
                  <TRow key={nota.notaId} className="hover:bg-ds-surface cursor-pointer">
                    <TD className="font-mono text-sm">{formatCompetencia(nota.competencia)}</TD>
                    <TD className="font-mono text-sm font-semibold">{nota.numeroNota ?? '—'}</TD>
                    <TD><StatusBadge status={nota.status} /></TD>
                    <TD className="font-mono text-xs text-ds-light">{nota.medicoId.substring(0, 8)}...</TD>
                    <TD className="text-xs text-ds-light max-w-48 truncate">{nota.observacoes ?? '—'}</TD>
                    <TD className="text-xs text-ds-light">
                      {nota.emitidaAt
                        ? new Date(nota.emitidaAt).toLocaleString('pt-BR', { day:'2-digit', month:'2-digit', year:'numeric', hour:'2-digit', minute:'2-digit' })
                        : '—'}
                    </TD>
                    <TD>
                      <button
                        onClick={() => navigate(`/notas/emitir/${nota.producaoId}`)}
                        className="p-1.5 rounded-lg text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
                        title="Ver detalhes"
                      >
                        <Eye size={15} />
                      </button>
                    </TD>
                  </TRow>
                ))}
              </TBody>
            </Table>
          </div>
        )}
      </div>
    </div>
  )
}
