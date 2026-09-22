import { useState } from 'react'
import { CreditCard, Landmark, Pencil, Plus, XCircle } from 'lucide-react'
import { Modal, Input, Button, Alert } from '@pinsaude/ui'
import { BancoSelect, BancoAvatar, bancos } from './BancoSelect'
import { SelectField, maskPixKey } from './MedicoWizardModal'
import {
  DadosBancariosMedico, DadosBancariosMedicoRequest,
  TipoPix, TipoRecebimento, TipoConta, medicosApi,
} from '../api/medicosApi'

// ─── Constants ────────────────────────────────────────────────────────────────

const TIPO_PIX_OPTIONS: { value: TipoPix; label: string }[] = [
  { value: 'CPF',       label: 'CPF' },
  { value: 'CNPJ',      label: 'CNPJ' },
  { value: 'EMAIL',     label: 'E-mail' },
  { value: 'TELEFONE',  label: 'Telefone' },
  { value: 'ALEATORIA', label: 'Chave Aleatória' },
]

function pixPlaceholder(tipo: TipoPix | ''): string {
  switch (tipo) {
    case 'CPF':       return '000.000.000-00'
    case 'CNPJ':      return '00.000.000/0001-00'
    case 'EMAIL':     return 'email@exemplo.com'
    case 'TELEFONE':  return '+5511999999999'
    case 'ALEATORIA': return 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
    default:          return 'Selecione o tipo primeiro'
  }
}

interface BankForm {
  apelido: string
  tipoRecebimento: TipoRecebimento
  // PIX
  tipoPix: TipoPix | ''
  chavePix: string
  cpfsAdicionaisSplit: string
  // TED
  bancoCodigo: string
  bancoNome: string
  agencia: string
  conta: string
  tipoConta: TipoConta | ''
}

const emptyBank = (): BankForm => ({
  apelido: '',
  tipoRecebimento: 'PIX',
  tipoPix: '', chavePix: '', cpfsAdicionaisSplit: '',
  bancoCodigo: '', bancoNome: '', agencia: '', conta: '', tipoConta: '',
})

function formToBank(c: DadosBancariosMedico): BankForm {
  return {
    apelido:             c.apelido ?? '',
    tipoRecebimento:     c.tipoRecebimento ?? 'PIX',
    tipoPix:             c.tipoPix ?? '',
    chavePix:            c.chavePix ?? '',
    cpfsAdicionaisSplit: c.cpfsAdicionaisSplit ?? '',
    bancoCodigo:         c.bancoCodigo ?? '',
    bancoNome:           c.bancoNome ?? '',
    agencia:             c.agencia ?? '',
    conta:               c.conta ?? '',
    tipoConta:           c.tipoConta ?? '',
  }
}

// ─── Section ──────────────────────────────────────────────────────────────────

interface Props {
  medicoId: string
  contas: DadosBancariosMedico[]
  canEdit: boolean
  onChanged: (contas: DadosBancariosMedico[]) => void
}

export function ContasBancariasSection({ medicoId, contas, canEdit, onChanged }: Props) {
  const [editing, setEditing] = useState<DadosBancariosMedico | null | undefined>(undefined) // undefined = fechado
  const [removingId, setRemovingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleRemover(contaId: string) {
    setError(null)
    setRemovingId(contaId)
    try {
      await medicosApi.removerDadosBancarios(medicoId, contaId)
      onChanged(contas.filter(c => c.id !== contaId))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao remover conta bancária')
    } finally {
      setRemovingId(null)
    }
  }

  function handleSaved(saved: DadosBancariosMedico) {
    const idx = contas.findIndex(c => c.id === saved.id)
    onChanged(idx >= 0 ? contas.map(c => c.id === saved.id ? saved : c) : [...contas, saved])
    setEditing(undefined)
  }

  return (
    <div className="flex flex-col gap-4">
      {contas.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-12 text-center">
          <CreditCard size={28} className="text-ds-light mb-3" />
          <p className="text-sm font-medium text-ds-mid">Nenhuma conta bancária cadastrada</p>
          <p className="text-xs text-ds-light mt-1">Configure PIX e/ou TED para recebimento de repasses</p>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {contas.map(c => (
            <ContaRow
              key={c.id}
              conta={c}
              canEdit={canEdit}
              removing={removingId === c.id}
              onEdit={() => setEditing(c)}
              onRemove={() => handleRemover(c.id)}
            />
          ))}
        </div>
      )}

      {error && <p className="text-xs text-red-600">{error}</p>}

      {canEdit && (
        <Button size="sm" variant="outline" className="self-start" onClick={() => setEditing(null)}>
          <Plus size={14} /> Adicionar conta bancária
        </Button>
      )}

      {editing !== undefined && (
        <ContaBancariaFormModal
          medicoId={medicoId}
          conta={editing}
          onClose={() => setEditing(undefined)}
          onSaved={handleSaved}
        />
      )}
    </div>
  )
}

// ─── Row ──────────────────────────────────────────────────────────────────────

function ContaRow({
  conta, canEdit, removing, onEdit, onRemove,
}: {
  conta: DadosBancariosMedico
  canEdit: boolean
  removing: boolean
  onEdit: () => void
  onRemove: () => void
}) {
  const isTed = conta.tipoRecebimento === 'TED'
  const banco = bancos.find(b => b.compe === conta.bancoCodigo) ?? null

  return (
    <div className="flex items-center justify-between px-3 py-2.5 rounded-lg bg-ds-input border border-ds-border">
      <div className="flex items-center gap-3 min-w-0">
        {isTed
          ? (banco ? <BancoAvatar banco={banco} size={28} /> : <Landmark size={16} className="text-primary shrink-0" />)
          : <CreditCard size={16} className="text-primary shrink-0" />
        }
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold text-ds-text truncate">
              {conta.apelido || (isTed ? 'TED — Transferência' : 'PIX')}
            </p>
            <span className={[
              'px-1.5 py-0.5 rounded text-[10px] font-bold shrink-0',
              isTed ? 'bg-blue-50 text-blue-700 border border-blue-200' : 'bg-teal-50 text-teal-700 border border-teal-200',
            ].join(' ')}>
              {conta.tipoRecebimento ?? 'PIX'}
            </span>
          </div>
          <p className="text-xs text-ds-light truncate">
            {isTed
              ? `${conta.bancoNome ?? `Banco ${conta.bancoCodigo}`} · Ag. ${conta.agencia} · Cc. ${conta.conta}${conta.tipoConta ? ` (${conta.tipoConta === 'POUPANCA' ? 'Poupança' : 'Corrente'})` : ''}`
              : `${conta.tipoPix ?? ''}${conta.tipoPix && conta.chavePix ? ' · ' + maskPixKey(conta.tipoPix, conta.chavePix) : ''}`}
          </p>
        </div>
      </div>
      {canEdit && (
        <div className="flex items-center gap-1 shrink-0">
          <button
            onClick={onEdit}
            className="p-1.5 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
            title="Editar conta"
          >
            <Pencil size={14} />
          </button>
          <button
            onClick={onRemove}
            disabled={removing}
            className="p-1.5 rounded text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors disabled:opacity-50"
            title="Remover conta"
          >
            <XCircle size={14} />
          </button>
        </div>
      )}
    </div>
  )
}

// ─── Form modal (adicionar / editar) ──────────────────────────────────────────

function ContaBancariaFormModal({
  medicoId, conta, onClose, onSaved,
}: {
  medicoId: string
  conta: DadosBancariosMedico | null
  onClose: () => void
  onSaved: (conta: DadosBancariosMedico) => void
}) {
  const isEditing = conta !== null
  const [bank, setBank] = useState<BankForm>(() => conta ? formToBank(conta) : emptyBank())
  const [errors, setErrors] = useState<Partial<Record<string, string>>>({})
  const [apiError, setApiError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const isTed = bank.tipoRecebimento === 'TED'
  const bancoSelecionado = bancos.find(b => b.compe === bank.bancoCodigo) ?? null

  function setBankField<K extends keyof BankForm>(key: K, value: BankForm[K]) {
    setBank(b => ({ ...b, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function validate(): boolean {
    const errs: Record<string, string> = {}
    if (isTed) {
      if (!bank.bancoCodigo.trim()) errs.bancoNome = 'Selecione o banco'
      if (!bank.agencia.trim()) errs.agencia = 'Obrigatório'
      if (!bank.conta.trim()) errs.conta = 'Obrigatório'
      if (!bank.tipoConta) errs.tipoConta = 'Obrigatório'
    } else {
      if (!bank.tipoPix) errs.tipoPix = 'Obrigatório'
      if (!bank.chavePix.trim()) errs.chavePix = 'Obrigatório'
    }
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  async function handleSave() {
    if (!validate()) return
    setLoading(true)
    setApiError(null)
    try {
      const req: DadosBancariosMedicoRequest = {
        tipoRecebimento:     bank.tipoRecebimento,
        tipoPix:             !isTed ? bank.tipoPix || null : null,
        chavePix:            !isTed ? bank.chavePix.trim() || null : null,
        cpfsAdicionaisSplit: !isTed ? bank.cpfsAdicionaisSplit.trim() || null : null,
        bancoCodigo:         isTed ? bank.bancoCodigo.trim() || null : null,
        bancoNome:           isTed ? bank.bancoNome.trim() || null : null,
        agencia:             isTed ? bank.agencia.trim() || null : null,
        conta:               isTed ? bank.conta.trim() || null : null,
        tipoConta:           isTed ? bank.tipoConta || null : null,
        confirmarAlteracao:  true,
        apelido:             bank.apelido.trim() || null,
      }
      const saved = isEditing
        ? await medicosApi.atualizarDadosBancarios(medicoId, conta.id, req)
        : await medicosApi.adicionarDadosBancarios(medicoId, req)
      onSaved(saved)
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Erro ao salvar conta bancária')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal open onClose={onClose} title={isEditing ? 'Editar Conta Bancária' : 'Adicionar Conta Bancária'} size="lg">
      <div className="flex flex-col gap-4">
        <Input
          label="Apelido (opcional)"
          value={bank.apelido}
          onChange={e => setBankField('apelido', e.target.value)}
          placeholder="Ex: PIX principal, TED salário Itaú..."
        />

        {/* Toggle PIX / TED */}
        <div className="flex rounded-lg border border-gray-200 overflow-hidden self-start">
          {(['PIX', 'TED'] as TipoRecebimento[]).map(tipo => (
            <button
              key={tipo}
              type="button"
              onClick={() => setBankField('tipoRecebimento', tipo)}
              className={[
                'px-6 py-2 text-sm font-medium transition-colors',
                bank.tipoRecebimento === tipo
                  ? 'bg-primary text-white'
                  : 'bg-white text-gray-600 hover:bg-gray-50',
              ].join(' ')}
            >
              {tipo}
            </button>
          ))}
        </div>

        {!isTed ? (
          /* ── PIX ── */
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <SelectField
              label="Tipo de chave PIX *"
              value={bank.tipoPix}
              onChange={v => setBankField('tipoPix', v as TipoPix | '')}
              error={errors.tipoPix}
              placeholder="Selecione o tipo"
            >
              {TIPO_PIX_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </SelectField>
            <Input
              label="Chave PIX *"
              value={bank.chavePix}
              onChange={e => setBankField('chavePix', e.target.value)}
              error={errors.chavePix}
              placeholder={pixPlaceholder(bank.tipoPix)}
              disabled={!bank.tipoPix}
            />
            <div className="sm:col-span-2">
              <Input
                label="CPFs para split acima de R$ 40.000 (separados por vírgula)"
                value={bank.cpfsAdicionaisSplit}
                onChange={e => setBankField('cpfsAdicionaisSplit', e.target.value)}
                placeholder="Ex: 000.000.000-00, 111.111.111-11"
              />
            </div>
            {bank.tipoPix && bank.chavePix && (
              <div className="sm:col-span-2 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Pré-visualização (mascarado após salvar)</p>
                <p className="mt-1 text-sm font-semibold text-gray-900 font-mono">
                  {maskPixKey(bank.tipoPix as TipoPix, bank.chavePix)}
                </p>
              </div>
            )}
          </div>
        ) : (
          /* ── TED ── */
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <BancoSelect
                value={bank.bancoNome}
                onChange={(nome, compe) => {
                  setBank(b => ({ ...b, bancoNome: nome, bancoCodigo: compe }))
                  setErrors(e => ({ ...e, bancoNome: undefined }))
                }}
                error={errors.bancoNome}
              />
            </div>
            <Input
              label="Agência *"
              value={bank.agencia}
              onChange={e => setBankField('agencia', e.target.value)}
              error={errors.agencia}
              placeholder="Ex: 1234"
            />
            <Input
              label="Conta *"
              value={bank.conta}
              onChange={e => setBankField('conta', e.target.value)}
              error={errors.conta}
              placeholder="Ex: 12345-6"
            />
            <div className="sm:col-span-2 flex flex-col gap-1">
              <label className="text-sm font-medium text-gray-700">Tipo de conta *</label>
              <div className="flex gap-4">
                {(['CORRENTE', 'POUPANCA'] as TipoConta[]).map(tipo => (
                  <label key={tipo} className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="radio"
                      name="tipoConta"
                      value={tipo}
                      checked={bank.tipoConta === tipo}
                      onChange={() => setBankField('tipoConta', tipo)}
                      className="accent-primary"
                    />
                    <span className="text-sm text-gray-700">
                      {tipo === 'CORRENTE' ? 'Corrente' : 'Poupança'}
                    </span>
                  </label>
                ))}
              </div>
              {errors.tipoConta && <p className="text-xs text-red-500">{errors.tipoConta}</p>}
            </div>
            {bank.bancoCodigo && bank.agencia && bank.conta && (
              <div className="sm:col-span-2 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Resumo TED</p>
                <div className="mt-1 flex items-center gap-2">
                  {bancoSelecionado && <BancoAvatar banco={bancoSelecionado} size={20} />}
                  <p className="text-sm font-semibold text-gray-900">
                    {bank.bancoNome || `Banco ${bank.bancoCodigo}`} · Ag. {bank.agencia} · Cc. {bank.conta}
                    {bank.tipoConta ? ` (${bank.tipoConta === 'CORRENTE' ? 'Corrente' : 'Poupança'})` : ''}
                  </p>
                </div>
              </div>
            )}
          </div>
        )}

        {apiError && <Alert variant="error">{apiError}</Alert>}

        <div className="flex justify-between pt-3 border-t border-gray-200">
          <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
          <Button type="button" onClick={handleSave} loading={loading}>
            {isEditing ? 'Salvar alterações' : 'Adicionar conta'}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
