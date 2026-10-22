import { useState } from 'react'
import { Loader2, Plus, Trash2, Pencil, Mail, Phone, User, X } from 'lucide-react'
import { Input } from '@pinsaude/ui'
import { TomadorContato, TomadorContatoRequest, tomadoresApi } from '../api/tomadoresApi'

// Contato exibido na lista: persistido (id preenchido) ou pendente (cadastro de tomador novo,
// salvo só depois que o tomador é criado — mesmo padrão dos CNAEs/Serviços).
export interface ContatoItem extends TomadorContatoRequest {
  key: string
  id?: string
}

let contatoKey = 1

export function novoContatoItem(req: TomadorContatoRequest, id?: string): ContatoItem {
  return { ...req, id, key: id ?? `pending-${contatoKey++}` }
}

export function contatoItemFrom(c: TomadorContato): ContatoItem {
  return novoContatoItem({
    apelido: c.apelido,
    nome: c.nome ?? '',
    email: c.email ?? '',
    telefone: c.telefone ?? '',
    observacao: c.observacao ?? '',
  }, c.id)
}

const EMPTY: TomadorContatoRequest = { apelido: '', nome: '', email: '', telefone: '', observacao: '' }
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

type Erros = Partial<Record<keyof TomadorContatoRequest, string>>

function validar(c: TomadorContatoRequest): Erros {
  const errs: Erros = {}
  if (!c.apelido.trim()) errs.apelido = 'Informe um apelido (ex.: Financeiro)'
  if (!c.email.trim() && !c.telefone.trim()) {
    errs.email = 'Informe o e-mail ou o telefone'
    errs.telefone = 'Informe o e-mail ou o telefone'
  } else if (c.email.trim() && !EMAIL_RE.test(c.email.trim())) {
    errs.email = 'E-mail inválido'
  }
  return errs
}

interface Props {
  // null = tomador ainda não cadastrado: alterações ficam só na lista local (pendentes)
  tomadorId: string | null
  contatos: ContatoItem[]
  onChange: (contatos: ContatoItem[]) => void
}

export function TomadorContatosSection({ tomadorId, contatos, onChange }: Props) {
  const [form, setForm]         = useState<TomadorContatoRequest>(EMPTY)
  const [editKey, setEditKey]   = useState<string | null>(null)
  const [erros, setErros]       = useState<Erros>({})
  const [apiError, setApiError] = useState<string | null>(null)
  const [loading, setLoading]   = useState(false)

  function set<K extends keyof TomadorContatoRequest>(key: K, val: string) {
    setForm(f => ({ ...f, [key]: val }))
    setErros(e => (key === 'email' || key === 'telefone')
      ? { ...e, email: undefined, telefone: undefined }
      : { ...e, [key]: undefined })
  }

  function limpar() {
    setForm(EMPTY)
    setEditKey(null)
    setErros({})
  }

  function editar(c: ContatoItem) {
    setForm({ apelido: c.apelido, nome: c.nome, email: c.email, telefone: c.telefone, observacao: c.observacao })
    setEditKey(c.key)
    setErros({})
    setApiError(null)
  }

  async function salvar() {
    const errs = validar(form)
    setErros(errs)
    if (Object.keys(errs).length > 0) return
    setApiError(null)

    const editando = editKey ? contatos.find(c => c.key === editKey) : undefined

    if (!tomadorId) {
      onChange(editando
        ? contatos.map(c => c.key === editKey ? { ...c, ...form } : c)
        : [...contatos, novoContatoItem(form)])
      limpar()
      return
    }

    setLoading(true)
    try {
      if (editando?.id) {
        const saved = await tomadoresApi.atualizarContato(tomadorId, editando.id, form)
        onChange(contatos.map(c => c.key === editKey ? contatoItemFrom(saved) : c))
      } else {
        const saved = await tomadoresApi.criarContato(tomadorId, form)
        onChange([...contatos, contatoItemFrom(saved)])
      }
      limpar()
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Erro ao salvar contato')
    } finally {
      setLoading(false)
    }
  }

  async function remover(c: ContatoItem) {
    if (!window.confirm(`Remover o contato "${c.apelido}"?`)) return
    setApiError(null)
    if (tomadorId && c.id) {
      try {
        await tomadoresApi.removerContato(tomadorId, c.id)
      } catch (err) {
        setApiError(err instanceof Error ? err.message : 'Erro ao remover contato')
        return
      }
    }
    onChange(contatos.filter(x => x.key !== c.key))
    if (editKey === c.key) limpar()
  }

  return (
    <div className="rounded-xl border border-ds-border p-4 bg-ds-input">
      <p className="text-xs font-semibold text-ds-mid mb-0.5 uppercase tracking-wide">Contatos</p>
      <p className="text-[11px] text-ds-light mb-3">
        Pessoas a contatar neste tomador. Use o apelido para identificar o contato (ex.: Financeiro, Compras).
        Cada contato precisa de e-mail ou telefone.
      </p>

      {apiError && <p className="text-xs text-red-600 mb-2">{apiError}</p>}

      {contatos.length > 0 && (
        <div className="mb-4 space-y-1.5">
          {contatos.map(c => (
            <div
              key={c.key}
              className={[
                'bg-white rounded-lg px-3 py-2 border',
                editKey === c.key ? 'border-primary ring-1 ring-primary/30' : 'border-ds-border',
              ].join(' ')}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-xs font-bold text-primary bg-primary-50 px-1.5 py-0.5 rounded">
                      {c.apelido}
                    </span>
                    {c.nome && (
                      <span className="flex items-center gap-1 text-xs font-semibold text-ds-text">
                        <User size={12} className="text-ds-light" /> {c.nome}
                      </span>
                    )}
                    {!c.id && <span className="text-[10px] text-ds-light">(será salvo com o tomador)</span>}
                  </div>
                  <div className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-ds-mid">
                    {c.email && (
                      <span className="flex items-center gap-1 min-w-0 break-all">
                        <Mail size={12} className="text-ds-light shrink-0" /> {c.email}
                      </span>
                    )}
                    {c.telefone && (
                      <span className="flex items-center gap-1">
                        <Phone size={12} className="text-ds-light shrink-0" /> {c.telefone}
                      </span>
                    )}
                  </div>
                  {c.observacao && (
                    <p className="mt-1 text-xs text-ds-light whitespace-pre-line">{c.observacao}</p>
                  )}
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <button
                    type="button"
                    onClick={() => editar(c)}
                    title="Editar contato"
                    className="text-ds-light hover:text-primary transition-colors"
                  >
                    <Pencil size={14} />
                  </button>
                  <button
                    type="button"
                    onClick={() => remover(c)}
                    title="Remover contato"
                    className="text-ds-light hover:text-red-500 transition-colors"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {contatos.length === 0 && (
        <p className="mb-4 text-xs text-ds-light italic">Nenhum contato cadastrado.</p>
      )}

      <div className="rounded-lg border border-dashed border-ds-border bg-white p-3">
        <p className="text-xs font-semibold text-ds-mid mb-2">
          {editKey ? 'Editar contato' : 'Novo contato'}
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Input
            id="contato-apelido"
            label="Apelido *"
            value={form.apelido}
            onChange={e => set('apelido', e.target.value)}
            placeholder="Ex.: Financeiro"
            maxLength={80}
            error={erros.apelido}
          />
          <Input
            id="contato-nome"
            label="Nome da pessoa"
            value={form.nome}
            onChange={e => set('nome', e.target.value)}
            placeholder="Com quem falar"
            maxLength={150}
          />
          <Input
            id="contato-email"
            label="E-mail"
            type="email"
            value={form.email}
            onChange={e => set('email', e.target.value)}
            placeholder="email@empresa.com.br"
            maxLength={255}
            error={erros.email}
          />
          <Input
            id="contato-telefone"
            label="Telefone"
            value={form.telefone}
            onChange={e => set('telefone', e.target.value)}
            placeholder="(XX) XXXXX-XXXX"
            maxLength={30}
            error={erros.telefone}
          />
          <div className="sm:col-span-2 flex flex-col gap-1">
            <label htmlFor="contato-observacao" className="text-sm font-medium text-gray-700">Observação</label>
            <textarea
              id="contato-observacao"
              value={form.observacao}
              onChange={e => set('observacao', e.target.value)}
              rows={2}
              maxLength={2000}
              placeholder="Anotações livres sobre o contato"
              className="w-full rounded-lg border border-gray-300 text-sm text-gray-900 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary resize-none"
            />
          </div>
        </div>
        <div className="mt-3 flex justify-end gap-2">
          {editKey && (
            <button
              type="button"
              onClick={limpar}
              className="h-9 px-3 rounded-lg border border-ds-border text-xs font-semibold text-ds-mid flex items-center gap-1.5 hover:border-ds-mid transition-colors"
            >
              <X size={14} /> Cancelar edição
            </button>
          )}
          <button
            type="button"
            onClick={salvar}
            disabled={loading}
            className="h-9 px-3 rounded-lg bg-primary text-white text-xs font-semibold flex items-center gap-1.5 hover:bg-primary-600 disabled:opacity-50 transition-colors"
          >
            {loading ? <Loader2 size={14} className="animate-spin" /> : editKey ? <Pencil size={14} /> : <Plus size={14} />}
            {editKey ? 'Salvar contato' : 'Adicionar contato'}
          </button>
        </div>
      </div>
    </div>
  )
}
