import { useEffect, useState } from 'react'
import { X, Save, FileText, AlertTriangle, CheckCircle, XCircle, Clock } from 'lucide-react'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import {
  configuracaoFiscalApi,
  ConfiguracaoFiscal,
  ConfiguracaoFiscalRequest,
  RegimePresuncao,
  StatusCertificado,
} from '../api/configuracaoFiscalApi'

interface Props {
  empresaId: string
  empresaNome: string
  onClose: () => void
}

function mesAtual(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${y}-${m}`
}

function CertificadoBadge({ status }: { status: StatusCertificado }) {
  const map: Record<StatusCertificado, { label: string; className: string; Icon: typeof CheckCircle }> = {
    VALIDO:        { label: 'Válido',         className: 'bg-green-100 text-green-700',  Icon: CheckCircle },
    EXPIRANDO:     { label: 'Expirando',      className: 'bg-yellow-100 text-yellow-700', Icon: AlertTriangle },
    VENCIDO:       { label: 'Vencido',        className: 'bg-red-100 text-red-700',      Icon: XCircle },
    NAO_CONFIGURADO: { label: 'Não configurado', className: 'bg-gray-100 text-gray-500',  Icon: Clock },
  }
  const { label, className, Icon } = map[status]
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium ${className}`}>
      <Icon size={12} />
      {label}
    </span>
  )
}

function AliquotaInput({
  label, name, value, onChange,
}: { label: string; name: string; value: string; onChange: (n: string, v: string) => void }) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>
      <div className="relative">
        <input
          type="number"
          min="0" max="100" step="0.01"
          value={value}
          onChange={e => onChange(name, e.target.value)}
          className="block w-full pr-7 pl-3 py-1.5 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
        />
        <span className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 text-xs">%</span>
      </div>
    </div>
  )
}

export function ConfiguracaoFiscalModal({ empresaId, empresaNome, onClose }: Props) {
  const [config, setConfig] = useState<ConfiguracaoFiscal | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const [cnaeCodigo, setCnaeCodigo] = useState('')
  const [cnaeDescricao, setCnaeDescricao] = useState('')
  const [codigoLc116, setCodigoLc116] = useState('')
  const [equiparacao, setEquiparacao] = useState(false)
  const [vencimentoA1, setVencimentoA1] = useState('')
  const [competencia, setCompetencia] = useState(mesAtual())
  const competenciaPassada = competencia < mesAtual()
  const [regimePresuncao, setRegimePresuncao] = useState<RegimePresuncao>('CHEIA')
  const [aliquotas, setAliquotas] = useState({ iss: '0.00', ir: '0.00', csll: '0.00', pis: '0.00', cofins: '0.00' })

  useEffect(() => {
    configuracaoFiscalApi.buscar(empresaId)
      .then(data => {
        setConfig(data)
        setCnaeCodigo(data.cnaeCodigo ?? '')
        setCnaeDescricao(data.cnaeDescricao ?? '')
        setCodigoLc116(data.codigoLc116 ?? '')
        setEquiparacao(data.indicadorEquiparacaoHospitalar)
        setVencimentoA1(data.vencimentoCertificadoA1 ?? '')
        // alíquotas são preenchidas pelo useEffect [competencia, config]
      })
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [empresaId])

  // Ao mudar a competência (ou ao carregar os dados), preenche com os valores
  // daquela competência se existir no histórico — zera tudo caso contrário.
  useEffect(() => {
    if (!config) return
    const encontrada = config.historicoAliquotas.find(a => a.competencia === competencia)
    if (encontrada) {
      setAliquotas({
        iss:    Number(encontrada.iss).toFixed(2),
        ir:     Number(encontrada.ir).toFixed(2),
        csll:   Number(encontrada.csll).toFixed(2),
        pis:    Number(encontrada.pis).toFixed(2),
        cofins: Number(encontrada.cofins).toFixed(2),
      })
      setRegimePresuncao(encontrada.regimePresuncao)
    } else {
      setAliquotas({ iss: '0.00', ir: '0.00', csll: '0.00', pis: '0.00', cofins: '0.00' })
      setRegimePresuncao('CHEIA')
    }
  }, [competencia, config])

  function handleAliquota(name: string, value: string) {
    setAliquotas(prev => ({ ...prev, [name]: value }))
  }

  async function handleSalvar() {
    setSaving(true)
    setError(null)
    setSuccess(false)
    try {
      const payload: ConfiguracaoFiscalRequest = {
        cnaeCodigo: cnaeCodigo || null,
        cnaeDescricao: cnaeDescricao || null,
        codigoLc116: codigoLc116 || null,
        indicadorEquiparacaoHospitalar: equiparacao,
        vencimentoCertificadoA1: vencimentoA1 || null,
        aliquota: {
          competencia,
          iss: parseFloat(aliquotas.iss) || 0,
          ir: parseFloat(aliquotas.ir) || 0,
          csll: parseFloat(aliquotas.csll) || 0,
          pis: parseFloat(aliquotas.pis) || 0,
          cofins: parseFloat(aliquotas.cofins) || 0,
          regimePresuncao,
        },
      }
      const updated = await configuracaoFiscalApi.salvar(empresaId, payload)
      setConfig(updated)
      setSuccess(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao salvar')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-3xl max-h-[90vh] flex flex-col">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <div className="flex items-center gap-2">
            <FileText size={18} className="text-primary" />
            <div>
              <h2 className="text-base font-semibold text-gray-900">Configuração Fiscal</h2>
              <p className="text-xs text-gray-500">{empresaNome}</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            {config && <CertificadoBadge status={config.statusCertificado} />}
            <button onClick={onClose} className="p-1.5 rounded hover:bg-gray-100 text-gray-500">
              <X size={16} />
            </button>
          </div>
        </div>

        {/* Body */}
        <div className="overflow-y-auto flex-1 px-6 py-5 space-y-6">
          {loading ? (
            <div className="flex justify-center py-12"><Spinner size="lg" /></div>
          ) : (
            <>
              {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}
              {success && <Alert variant="success" onClose={() => setSuccess(false)}>Configuração salva com sucesso!</Alert>}

              {/* ─── Dados Fiscais ─── */}
              <section>
                <h3 className="text-sm font-semibold text-gray-700 mb-3 pb-1 border-b border-gray-200">
                  Dados Fiscais
                </h3>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">CNAE Principal (código)</label>
                    <input value={cnaeCodigo} onChange={e => setCnaeCodigo(e.target.value)}
                      placeholder="Ex: 8630503" maxLength={10}
                      className="block w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">CNAE — Descrição</label>
                    <input value={cnaeDescricao} onChange={e => setCnaeDescricao(e.target.value)}
                      placeholder="Ex: Atividade médica ambulatorial"
                      className="block w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Código LC 116</label>
                    <select value={codigoLc116} onChange={e => setCodigoLc116(e.target.value)}
                      className="block w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary">
                      <option value="">Selecione...</option>
                      <option value="4.02">4.02 — Medicina e biomedicina</option>
                      <option value="4.03">4.03 — Serviços hospitalares</option>
                      <option value="outro">Outro</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Vencimento Certificado A1</label>
                    <input type="date" value={vencimentoA1} onChange={e => setVencimentoA1(e.target.value)}
                      className="block w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary" />
                  </div>
                </div>
                <div className="mt-3">
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input type="checkbox" checked={equiparacao} onChange={e => setEquiparacao(e.target.checked)}
                      className="w-4 h-4 rounded border-gray-300 text-primary focus:ring-primary-300" />
                    <span className="text-sm text-gray-700">Equiparação hospitalar (LC 116, Art. 2°)</span>
                  </label>
                </div>
              </section>

              {/* ─── Alíquotas por Competência ─── */}
              <section>
                <h3 className="text-sm font-semibold text-gray-700 mb-3 pb-1 border-b border-gray-200">
                  Alíquotas por Competência
                </h3>
                <div className="grid grid-cols-3 gap-4 mb-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Competência (YYYY-MM)</label>
                    <input type="month" value={competencia} min={mesAtual()} onChange={e => setCompetencia(e.target.value)}
                      className="block w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary" />
                    {competenciaPassada
                      ? <p className="mt-1 text-xs text-red-500">Competências passadas não podem ser alteradas.</p>
                      : config && (
                          config.historicoAliquotas.some(a => a.competencia === competencia)
                            ? <p className="mt-1 text-xs text-blue-500">Editando configuração existente para esta competência.</p>
                            : <p className="mt-1 text-xs text-gray-400">Nova competência — campos iniciados zerados.</p>
                        )
                    }
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Regime de Presunção</label>
                    <select value={regimePresuncao} onChange={e => setRegimePresuncao(e.target.value as RegimePresuncao)}
                      className="block w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary">
                      <option value="CHEIA">Cheia</option>
                      <option value="REDUZIDA">Reduzida (equiparação)</option>
                    </select>
                  </div>
                </div>
                <div className="grid grid-cols-5 gap-3">
                  <AliquotaInput label="ISS" name="iss" value={aliquotas.iss} onChange={handleAliquota} />
                  <AliquotaInput label="IR" name="ir" value={aliquotas.ir} onChange={handleAliquota} />
                  <AliquotaInput label="CSLL" name="csll" value={aliquotas.csll} onChange={handleAliquota} />
                  <AliquotaInput label="PIS" name="pis" value={aliquotas.pis} onChange={handleAliquota} />
                  <AliquotaInput label="COFINS" name="cofins" value={aliquotas.cofins} onChange={handleAliquota} />
                </div>
                <p className="mt-2 text-xs text-gray-400">
                  Alíquotas entre 0% e 100%. Cada competência é independente — alterações não retroagem.
                </p>
              </section>

              {/* ─── Histórico ─── */}
              {config && config.historicoAliquotas.length > 0 && (
                <section>
                  <h3 className="text-sm font-semibold text-gray-700 mb-3 pb-1 border-b border-gray-200">
                    Histórico de Alíquotas
                  </h3>
                  <div className="overflow-x-auto">
                    <table className="min-w-full text-xs">
                      <thead>
                        <tr className="border-b border-gray-200">
                          <th className="text-left py-2 pr-4 font-medium text-gray-500">Competência</th>
                          <th className="text-right py-2 pr-3 font-medium text-gray-500">ISS</th>
                          <th className="text-right py-2 pr-3 font-medium text-gray-500">IR</th>
                          <th className="text-right py-2 pr-3 font-medium text-gray-500">CSLL</th>
                          <th className="text-right py-2 pr-3 font-medium text-gray-500">PIS</th>
                          <th className="text-right py-2 pr-3 font-medium text-gray-500">COFINS</th>
                          <th className="text-left py-2 pr-3 font-medium text-gray-500">Regime</th>
                          <th className="text-left py-2 font-medium text-gray-500">Usuário</th>
                        </tr>
                      </thead>
                      <tbody>
                        {config.historicoAliquotas.map(a => (
                          <tr key={a.id} className="border-b border-gray-50 hover:bg-gray-50">
                            <td className="py-2 pr-4 font-mono font-medium text-gray-700">{a.competencia}</td>
                            <td className="py-2 pr-3 text-right text-gray-600">{Number(a.iss).toFixed(2)}%</td>
                            <td className="py-2 pr-3 text-right text-gray-600">{Number(a.ir).toFixed(2)}%</td>
                            <td className="py-2 pr-3 text-right text-gray-600">{Number(a.csll).toFixed(2)}%</td>
                            <td className="py-2 pr-3 text-right text-gray-600">{Number(a.pis).toFixed(2)}%</td>
                            <td className="py-2 pr-3 text-right text-gray-600">{Number(a.cofins).toFixed(2)}%</td>
                            <td className="py-2 pr-3">
                              <span className={`px-1.5 py-0.5 rounded text-xs ${a.regimePresuncao === 'REDUZIDA' ? 'bg-blue-50 text-blue-600' : 'bg-gray-100 text-gray-600'}`}>
                                {a.regimePresuncao === 'REDUZIDA' ? 'Reduzida' : 'Cheia'}
                              </span>
                            </td>
                            <td className="py-2 text-gray-400">{a.createdBy ?? '—'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </section>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-gray-200 flex justify-end gap-3">
          <Button variant="outline" size="md" onClick={onClose} disabled={saving}>
            Fechar
          </Button>
          <Button size="md" onClick={handleSalvar} disabled={loading || saving || competenciaPassada}>
            {saving ? <Spinner size="sm" /> : <Save size={15} />}
            Salvar
          </Button>
        </div>
      </div>
    </div>
  )
}
