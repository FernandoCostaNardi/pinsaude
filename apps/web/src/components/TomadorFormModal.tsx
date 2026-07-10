import { useEffect, useRef, useState } from 'react'
import { Loader2, CheckCircle, Info, Plus, Trash2 } from 'lucide-react'
import { Modal, Input, Button, Alert } from '@pinsaude/ui'
import { CnpjInput } from './CnpjInput'
import { CpfInput } from './CpfInput'
import { isValidCnpj } from '../utils/cnpj'
import { Tomador, TomadorRequest, TipoTomador, TomadorCnae, tomadoresApi } from '../api/tomadoresApi'
import { CnaeSelect, formatCnae } from './CnaeSelect'

const TIPO_OPTIONS: { value: TipoTomador; label: string }[] = [
  { value: 'HOSPITAL',    label: 'Hospital'    },
  { value: 'CLINICA',     label: 'Clínica'     },
  { value: 'OPERADORA',   label: 'Operadora'   },
  { value: 'PACIENTE_PF', label: 'Paciente PF' },
]

const TIPO_COLORS: Record<TipoTomador, string> = {
  HOSPITAL:    'border-primary bg-primary-50 text-primary',
  CLINICA:     'border-green-500 bg-green-50 text-green-700',
  OPERADORA:   'border-violet-500 bg-violet-50 text-violet-700',
  PACIENTE_PF: 'border-orange-400 bg-orange-50 text-orange-700',
}

const TRIBUTOS = ['ISS', 'IR', 'CSLL', 'PIS', 'COFINS'] as const
type Tributo = typeof TRIBUTOS[number]

const EMPTY_ALIQ: Record<Tributo, string> = { ISS: '', IR: '', CSLL: '', PIS: '', COFINS: '' }

interface PendingCnae { codigo: string; descricao: string; key: number }

interface Props {
  tomador: Tomador | null
  onClose: () => void
  onSaved: (t: Tomador) => void
}

const emptyForm = (): TomadorRequest => ({
  tipo: 'HOSPITAL',
  cnpjCpf: '',
  razaoSocialNome: '',
  nomeFantasia: '',
  municipio: '',
  inscricaoMunicipal: '',
  indicadorRetencaoFederal: false,
  indicadorRetencaoIss: false,
  email: '',
  telefone: '',
  logradouro: '',
  bairro: '',
  cep: '',
  uf: '',
  pais: 'Brasil',
})

let cnaeKey = 1

export function TomadorFormModal({ tomador, onClose, onSaved }: Props) {
  const isEditing = tomador !== null
  const [form, setForm]     = useState<TomadorRequest>(emptyForm)
  const [errors, setErrors] = useState<Partial<Record<keyof TomadorRequest, string>>>({})
  const [apiError, setApiError] = useState<string | null>(null)
  const [loading, setLoading]   = useState(false)
  const [receitaLoading, setReceitaLoading] = useState(false)
  const [receitaOk, setReceitaOk]           = useState(false)
  const receitaTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Alíquotas — todos os 5 campos exibidos de uma vez
  const [aliqForm, setAliqForm] = useState<Record<Tributo, string>>({ ...EMPTY_ALIQ })

  // CNAEs
  const [cnaes, setCnaes]               = useState<TomadorCnae[]>([])
  const [pendingCnaes, setPendingCnaes] = useState<PendingCnae[]>([])
  const [novoCnae, setNovoCnae]         = useState({ codigo: '', descricao: '' })
  const [cnaeLoading, setCnaeLoading]   = useState(false)
  const [cnaeError, setCnaeError]       = useState<string | null>(null)

  useEffect(() => {
    if (tomador) {
      setForm({
        tipo: tomador.tipo,
        cnpjCpf: tomador.cnpjCpf,
        razaoSocialNome: tomador.razaoSocialNome,
        nomeFantasia: tomador.nomeFantasia ?? '',
        municipio: tomador.municipio ?? '',
        inscricaoMunicipal: tomador.inscricaoMunicipal ?? '',
        indicadorRetencaoFederal: tomador.indicadorRetencaoFederal,
        indicadorRetencaoIss: tomador.indicadorRetencaoIss,
        email: tomador.email ?? '',
        telefone: tomador.telefone ?? '',
        logradouro: tomador.logradouro ?? '',
        bairro: tomador.bairro ?? '',
        cep: tomador.cep ?? '',
        uf: tomador.uf ?? '',
        pais: tomador.pais ?? 'Brasil',
      })
      const map = { ...EMPTY_ALIQ }
      for (const a of tomador.aliquotas ?? []) {
        map[a.tipoTributo as Tributo] = String(Number(a.valorAliquota))
      }
      setAliqForm(map)
      setCnaes(tomador.cnaes ?? [])
      setPendingCnaes([])
    } else {
      setForm(emptyForm())
      setAliqForm({ ...EMPTY_ALIQ })
      setCnaes([])
      setPendingCnaes([])
    }
    setErrors({})
    setApiError(null)
    setReceitaOk(false)
    setCnaeError(null)
    setNovoCnae({ codigo: '', descricao: '' })
  }, [tomador])

  // Auto-fill Receita Federal
  useEffect(() => {
    if (form.tipo === 'PACIENTE_PF') return
    const digits = form.cnpjCpf.replace(/\D/g, '')
    if (digits.length !== 14 || !isValidCnpj(form.cnpjCpf)) return
    if (receitaTimer.current) clearTimeout(receitaTimer.current)
    receitaTimer.current = setTimeout(async () => {
      setReceitaLoading(true)
      setReceitaOk(false)
      try {
        const data = await tomadoresApi.consultarReceita(digits)
        if (data) {
          const logradouroCompleto = [data.logradouro, data.numero].filter(Boolean).join(', ')
          setForm(f => ({
            ...f,
            razaoSocialNome: data.razaoSocial  ?? f.razaoSocialNome,
            nomeFantasia:    data.nomeFantasia ?? f.nomeFantasia,
            municipio:       data.municipio    ?? f.municipio,
            uf:              data.uf           ?? f.uf,
            logradouro:      logradouroCompleto || f.logradouro,
            bairro:          data.bairro       ?? f.bairro,
            cep:             data.cep          ?? f.cep,
            email:           data.email        ?? f.email,
            telefone:        data.telefone     ?? f.telefone,
          }))
          setReceitaOk(true)
        }
      } catch { /* silent fail */ } finally {
        setReceitaLoading(false)
      }
    }, 600)
    return () => { if (receitaTimer.current) clearTimeout(receitaTimer.current) }
  }, [form.cnpjCpf, form.tipo])

  function set<K extends keyof TomadorRequest>(key: K, val: TomadorRequest[K]) {
    setForm(f => ({ ...f, [key]: val }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function handleTipoChange(tipo: TipoTomador) {
    setForm(f => ({ ...f, tipo, cnpjCpf: '' }))
    setErrors(e => ({ ...e, tipo: undefined, cnpjCpf: undefined }))
    setReceitaOk(false)
  }

  function validate(): boolean {
    const errs: Partial<Record<keyof TomadorRequest, string>> = {}
    if (!form.cnpjCpf) errs.cnpjCpf = 'Campo obrigatório'
    if (!form.razaoSocialNome.trim()) errs.razaoSocialNome = 'Campo obrigatório'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    setApiError(null)
    try {
      const saved = isEditing
        ? await tomadoresApi.atualizar(tomador!.id, form)
        : await tomadoresApi.criar(form)

      const id = saved.id

      // Salvar alíquotas: upsert se preenchida, remover se vazia e existia antes
      for (const tributo of TRIBUTOS) {
        const val = parseFloat(aliqForm[tributo].replace(',', '.'))
        if (!isNaN(val) && val > 0) {
          await tomadoresApi.salvarAliquota(id, tributo, val)
        } else if (isEditing) {
          const existing = tomador!.aliquotas?.find(a => a.tipoTributo === tributo)
          if (existing) await tomadoresApi.removerAliquota(id, existing.id)
        }
      }

      // Salvar CNAEs pendentes (somente no cadastro; edição salva na hora)
      if (!isEditing) {
        for (const cnae of pendingCnaes) {
          await tomadoresApi.adicionarCnae(id, cnae.codigo, cnae.descricao)
        }
      }

      const final = await tomadoresApi.buscarPorId(id)
      onSaved(final)
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Erro ao salvar')
    } finally {
      setLoading(false)
    }
  }

  async function handleAdicionarCnae() {
    if (!novoCnae.codigo.trim()) { setCnaeError('Selecione um CNAE'); return }
    setCnaeError(null)

    if (isEditing) {
      setCnaeLoading(true)
      try {
        const saved = await tomadoresApi.adicionarCnae(tomador!.id, novoCnae.codigo.trim(), novoCnae.descricao.trim())
        setCnaes(prev => [...prev, saved])
        setNovoCnae({ codigo: '', descricao: '' })
      } catch (err) {
        setCnaeError(err instanceof Error ? err.message : 'Erro ao adicionar CNAE')
      } finally {
        setCnaeLoading(false)
      }
    } else {
      if (pendingCnaes.some(c => c.codigo === novoCnae.codigo.trim())) {
        setCnaeError('Este CNAE já foi adicionado')
        return
      }
      setPendingCnaes(prev => [...prev, { ...novoCnae, key: cnaeKey++ }])
      setNovoCnae({ codigo: '', descricao: '' })
    }
  }

  async function handleRemoverCnae(cnae: TomadorCnae) {
    try {
      await tomadoresApi.removerCnae(tomador!.id, cnae.id)
      setCnaes(prev => prev.filter(c => c.id !== cnae.id))
    } catch (err) {
      setCnaeError(err instanceof Error ? err.message : 'Erro ao remover CNAE')
    }
  }

  const isPf = form.tipo === 'PACIENTE_PF'

  // Lista unificada para exibição de CNAEs (edit = persistidos; create = pendentes)
  const cnaesExibidos: { key: string; codigoCnae: string; descricao: string | null; isPending: boolean; pendingKey?: number; ref?: TomadorCnae }[] = isEditing
    ? cnaes.map(c => ({ key: c.id, codigoCnae: c.codigoCnae, descricao: c.descricao, isPending: false, ref: c }))
    : pendingCnaes.map(c => ({ key: String(c.key), codigoCnae: c.codigo, descricao: c.descricao, isPending: true, pendingKey: c.key }))

  return (
    <Modal open title={isEditing ? 'Editar Tomador' : 'Novo Tomador'} onClose={onClose} size="lg">
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">

        {apiError && (
          <Alert variant="error" onClose={() => setApiError(null)}>{apiError}</Alert>
        )}

        {/* ── Tipo ── */}
        <div>
          <p className="text-xs font-semibold text-ds-mid mb-2 uppercase tracking-wide">Tipo *</p>
          <div className="flex flex-wrap gap-2">
            {TIPO_OPTIONS.map(opt => (
              <button
                key={opt.value}
                type="button"
                onClick={() => handleTipoChange(opt.value)}
                className={[
                  'px-3 py-1.5 rounded-lg border text-xs font-semibold transition-all',
                  form.tipo === opt.value
                    ? TIPO_COLORS[opt.value]
                    : 'border-ds-border text-ds-mid hover:border-ds-mid',
                ].join(' ')}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {/* ── Documento ── */}
        <div>
          {isPf ? (
            <CpfInput
              label="CPF *"
              value={form.cnpjCpf}
              onChange={v => set('cnpjCpf', v)}
              error={errors.cnpjCpf}
            />
          ) : (
            <div>
              <div className="flex items-end gap-2">
                <div className="flex-1">
                  <CnpjInput
                    label="CNPJ *"
                    value={form.cnpjCpf}
                    onChange={v => set('cnpjCpf', v)}
                    error={errors.cnpjCpf}
                  />
                </div>
                <div className="pb-0.5 shrink-0 flex items-center h-9">
                  {receitaLoading && <Loader2 size={18} className="animate-spin text-primary" />}
                  {receitaOk && !receitaLoading && (
                    <span className="flex items-center gap-1 text-xs text-green-600 font-medium">
                      <CheckCircle size={14} /> Receita preenchida
                    </span>
                  )}
                </div>
              </div>
              <p className="mt-1 text-[11px] text-ds-light">
                Digite o CNPJ completo para buscar dados na Receita Federal automaticamente
              </p>
            </div>
          )}
        </div>

        {/* ── Dados básicos ── */}
        <div className="grid grid-cols-1 gap-4">
          <Input
            label={isPf ? 'Nome Completo *' : 'Razão Social *'}
            value={form.razaoSocialNome}
            onChange={e => set('razaoSocialNome', e.target.value)}
            placeholder={isPf ? 'Nome do paciente' : 'Razão social da empresa'}
            error={errors.razaoSocialNome}
          />
          {!isPf && (
            <Input
              label="Nome Fantasia"
              value={form.nomeFantasia}
              onChange={e => set('nomeFantasia', e.target.value)}
              placeholder="Nome fantasia (opcional)"
            />
          )}
          <div className="grid grid-cols-2 gap-3">
            {!isPf && (
              <Input
                label="Município"
                value={form.municipio}
                onChange={e => set('municipio', e.target.value)}
                placeholder="Cidade"
              />
            )}
            <Input
              label="Inscrição Municipal"
              value={form.inscricaoMunicipal}
              onChange={e => set('inscricaoMunicipal', e.target.value)}
              placeholder="N.º inscrição"
              className={isPf ? 'col-span-2' : ''}
            />
          </div>
        </div>

        {/* ── Contato e Endereço ── */}
        <div className="rounded-xl border border-ds-border p-4 bg-ds-input">
          <p className="text-xs font-semibold text-ds-mid mb-3 uppercase tracking-wide">Contato e Endereço</p>
          <div className="grid grid-cols-2 gap-3">
            <Input
              label="E-mail"
              type="email"
              value={form.email}
              onChange={e => set('email', e.target.value)}
              placeholder="email@empresa.com.br"
            />
            <Input
              label="Telefone"
              value={form.telefone}
              onChange={e => set('telefone', e.target.value)}
              placeholder="(XX) XXXXX-XXXX"
            />
            <Input
              label="Logradouro"
              value={form.logradouro}
              onChange={e => set('logradouro', e.target.value)}
              placeholder="Rua, Av, número..."
              className="col-span-2"
            />
            <Input
              label="Bairro"
              value={form.bairro}
              onChange={e => set('bairro', e.target.value)}
              placeholder="Bairro"
            />
            <Input
              label="CEP"
              value={form.cep}
              onChange={e => set('cep', e.target.value)}
              placeholder="XXXXX-XXX"
            />
            <Input
              label="UF"
              value={form.uf}
              onChange={e => set('uf', e.target.value.toUpperCase().slice(0, 2))}
              placeholder="PE"
              className="w-24"
            />
            <Input
              label="País"
              value={form.pais}
              onChange={e => set('pais', e.target.value)}
              placeholder="Brasil"
            />
          </div>
        </div>

        {/* ── Retenções na Fonte ── */}
        <div className="rounded-xl border border-ds-border p-4 bg-ds-input">
          <p className="text-xs font-semibold text-ds-mid mb-3 uppercase tracking-wide">Retenções na Fonte</p>
          <div className="flex flex-col gap-3">
            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={form.indicadorRetencaoFederal}
                onChange={e => set('indicadorRetencaoFederal', e.target.checked)}
                className="mt-0.5 rounded border-ds-border text-primary focus:ring-primary"
              />
              <div>
                <div className="flex items-center gap-1.5">
                  <span className="text-sm font-medium text-ds-text">Retenção Federal</span>
                  <span className="cursor-help text-ds-light hover:text-primary transition-colors"
                    title="Retém IR, CSLL, PIS e COFINS sobre o valor do serviço na emissão da nota fiscal">
                    <Info size={13} />
                  </span>
                </div>
                <p className="text-xs text-ds-light mt-0.5">IR, CSLL, PIS e COFINS retidos na fonte</p>
              </div>
            </label>

            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={form.indicadorRetencaoIss}
                onChange={e => set('indicadorRetencaoIss', e.target.checked)}
                className="mt-0.5 rounded border-ds-border text-primary focus:ring-primary"
              />
              <div>
                <div className="flex items-center gap-1.5">
                  <span className="text-sm font-medium text-ds-text">Retenção de ISS</span>
                  <span className="cursor-help text-ds-light hover:text-primary transition-colors"
                    title="ISS retido na fonte pelo tomador — a nota fiscal não destaca o ISS a pagar">
                    <Info size={13} />
                  </span>
                </div>
                <p className="text-xs text-ds-light mt-0.5">ISS retido na fonte pelo tomador</p>
              </div>
            </label>
          </div>
        </div>

        {/* ── Alíquotas Diferenciadas (PJ apenas) ── */}
        {!isPf && (
          <div className="rounded-xl border border-ds-border p-4 bg-ds-input">
            <p className="text-xs font-semibold text-ds-mid mb-0.5 uppercase tracking-wide">Alíquotas Diferenciadas</p>
            <p className="text-[11px] text-ds-light mb-4">
              Deixe em branco para usar as alíquotas padrão do serviço. Preencha apenas os tributos que este tomador tem alíquota específica.
            </p>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              {TRIBUTOS.map(tributo => (
                <div key={tributo}>
                  <label className="block text-xs font-medium text-ds-mid mb-1">{tributo} (%)</label>
                  <div className="relative">
                    <input
                      type="text"
                      value={aliqForm[tributo]}
                      onChange={e => setAliqForm(f => ({ ...f, [tributo]: e.target.value }))}
                      placeholder="padrão"
                      className="w-full h-9 rounded-lg border border-ds-border bg-white text-sm text-ds-mid px-3 pr-7 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/30"
                    />
                    <span className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-ds-light">%</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── CNAEs (PJ apenas) ── */}
        {!isPf && (
          <div className="rounded-xl border border-ds-border p-4 bg-ds-input">
            <p className="text-xs font-semibold text-ds-mid mb-0.5 uppercase tracking-wide">CNAEs</p>
            <p className="text-[11px] text-ds-light mb-3">CNAEs disponíveis para seleção na emissão da NFS-e</p>

            {cnaeError && (
              <p className="text-xs text-red-600 mb-2">{cnaeError}</p>
            )}

            {cnaesExibidos.length > 0 && (
              <div className="mb-3 space-y-1.5">
                {cnaesExibidos.map(c => (
                  <div key={c.key} className="flex items-center justify-between bg-white rounded-lg px-3 py-2 border border-ds-border">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-xs font-bold text-primary bg-primary-50 px-1.5 py-0.5 rounded">
                        {formatCnae(c.codigoCnae)}
                      </span>
                      {c.descricao && (
                        <span className="text-xs text-ds-light">
                          {c.descricao.charAt(0) + c.descricao.slice(1).toLowerCase()}
                        </span>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        if (c.isPending) {
                          setPendingCnaes(prev => prev.filter(p => p.key !== c.pendingKey))
                        } else {
                          handleRemoverCnae(c.ref!)
                        }
                      }}
                      className="text-ds-light hover:text-red-500 transition-colors"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div className="flex items-end gap-2">
              <div className="flex-1">
                <CnaeSelect
                  cnaeCodigo={novoCnae.codigo}
                  cnaeDescricao={novoCnae.descricao}
                  onChange={(codigo, descricao) => setNovoCnae({ codigo, descricao })}
                />
              </div>
              <button
                type="button"
                onClick={handleAdicionarCnae}
                disabled={cnaeLoading || !novoCnae.codigo}
                className="h-[42px] px-3 rounded-lg bg-primary text-white text-xs font-semibold flex items-center gap-1.5 hover:bg-primary-600 disabled:opacity-50 transition-colors self-end"
              >
                {cnaeLoading ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
                Adicionar
              </button>
            </div>
          </div>
        )}

        {/* ── Ações ── */}
        <div className="flex justify-end gap-3 pt-1 border-t border-ds-border">
          <Button type="button" variant="ghost" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="submit" loading={loading}>
            {isEditing ? 'Salvar alterações' : 'Cadastrar tomador'}
          </Button>
        </div>

      </form>
    </Modal>
  )
}
