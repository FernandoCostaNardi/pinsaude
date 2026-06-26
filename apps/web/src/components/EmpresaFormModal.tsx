import { FormEvent, useEffect, useState } from 'react'
import { Modal, Input, Button, Alert } from '@pinsaude/ui'
import { CnpjInput } from './CnpjInput'
import { Empresa, EmpresaRequest, RegimeTributario, empresasApi } from '../api/empresasApi'
import { isValidCnpj } from '../utils/cnpj'

const REGIME_OPTIONS: { value: RegimeTributario; label: string }[] = [
  { value: 'SIMPLES_NACIONAL', label: 'Simples Nacional' },
  { value: 'LUCRO_PRESUMIDO', label: 'Lucro Presumido' },
  { value: 'LUCRO_REAL', label: 'Lucro Real' },
]

interface Props {
  empresa: Empresa | null
  onClose: () => void
  onSaved: (empresa: Empresa) => void
}

const emptyForm = (): EmpresaRequest => ({
  cnpj: '',
  razaoSocial: '',
  inscricaoMunicipal: '',
  municipio: '',
  codigoMunicipioIbge: '',
  regimeTributario: 'SIMPLES_NACIONAL',
  logradouro: null,
  bairro: null,
  uf: null,
  cep: null,
  telefone: null,
  emailContato: null,
})

export function EmpresaFormModal({ empresa, onClose, onSaved }: Props) {
  const [form, setForm] = useState<EmpresaRequest>(emptyForm)
  const [errors, setErrors] = useState<Partial<Record<keyof EmpresaRequest, string>>>({})
  const [apiError, setApiError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const isEditing = empresa !== null

  useEffect(() => {
    if (empresa) {
      setForm({
        cnpj: empresa.cnpj,
        razaoSocial: empresa.razaoSocial,
        inscricaoMunicipal: empresa.inscricaoMunicipal ?? '',
        municipio: empresa.municipio ?? '',
        codigoMunicipioIbge: empresa.codigoMunicipioIbge ?? '',
        regimeTributario: empresa.regimeTributario,
        logradouro: empresa.logradouro,
        bairro: empresa.bairro,
        uf: empresa.uf,
        cep: empresa.cep,
        telefone: empresa.telefone,
        emailContato: empresa.emailContato,
      })
    } else {
      setForm(emptyForm())
    }
    setErrors({})
    setApiError(null)
  }, [empresa])

  function set<K extends keyof EmpresaRequest>(key: K, value: EmpresaRequest[K]) {
    setForm(f => ({ ...f, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function validate(): boolean {
    const errs: Partial<Record<keyof EmpresaRequest, string>> = {}
    if (!isEditing && (!form.cnpj || !isValidCnpj(form.cnpj))) errs.cnpj = 'CNPJ inválido'
    if (!form.razaoSocial.trim()) errs.razaoSocial = 'Obrigatório'
    if (!form.inscricaoMunicipal.trim()) errs.inscricaoMunicipal = 'Obrigatório'
    if (!form.regimeTributario) errs.regimeTributario = 'Obrigatório'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    setApiError(null)
    try {
      const saved = isEditing
        ? await empresasApi.atualizar(empresa.id, form)
        : await empresasApi.criar(form)
      onSaved(saved)
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Erro ao salvar empresa')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={isEditing ? 'Editar Empresa' : 'Nova Empresa'}
      size="lg"
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {apiError && <Alert variant="error">{apiError}</Alert>}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <CnpjInput
              label="CNPJ *"
              value={form.cnpj}
              onChange={v => set('cnpj', v)}
              error={errors.cnpj}
              disabled={isEditing}
            />
          </div>

          <div className="sm:col-span-2">
            <Input
              label="Razão Social *"
              value={form.razaoSocial}
              onChange={e => set('razaoSocial', e.target.value)}
              error={errors.razaoSocial}
              placeholder="Nome completo da empresa"
            />
          </div>

          <Input
            label="Inscrição Municipal *"
            value={form.inscricaoMunicipal}
            onChange={e => set('inscricaoMunicipal', e.target.value)}
            error={errors.inscricaoMunicipal}
            placeholder="Ex: 1234/2024"
          />

          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium text-gray-700">Regime Tributário *</label>
            <select
              value={form.regimeTributario}
              onChange={e => set('regimeTributario', e.target.value as RegimeTributario)}
              className={[
                'block w-full rounded-lg border px-3 py-2 text-sm',
                'focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary',
                errors.regimeTributario ? 'border-red-400' : 'border-gray-300',
              ].join(' ')}
            >
              {REGIME_OPTIONS.map(o => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            {errors.regimeTributario && (
              <p className="text-xs text-red-500">{errors.regimeTributario}</p>
            )}
          </div>

          <Input
            label="Município"
            value={form.municipio}
            onChange={e => set('municipio', e.target.value)}
            placeholder="Ex: São Paulo"
          />

          <Input
            label="Código IBGE"
            value={form.codigoMunicipioIbge}
            onChange={e => set('codigoMunicipioIbge', e.target.value)}
            placeholder="7 dígitos (ex: 3550308)"
            maxLength={7}
          />

          <div className="sm:col-span-2">
            <Input
              label="Logradouro"
              value={form.logradouro ?? ''}
              onChange={e => set('logradouro', e.target.value || null)}
              placeholder="Rua, Av., Travessa..."
            />
          </div>

          <Input
            label="Bairro"
            value={form.bairro ?? ''}
            onChange={e => set('bairro', e.target.value || null)}
            placeholder="Ex: Centro"
          />

          <div className="grid grid-cols-2 gap-3">
            <Input
              label="UF"
              value={form.uf ?? ''}
              onChange={e => set('uf', e.target.value.toUpperCase().slice(0, 2) || null)}
              placeholder="SP"
              maxLength={2}
            />
            <Input
              label="CEP"
              value={form.cep ?? ''}
              onChange={e => set('cep', e.target.value || null)}
              placeholder="00000-000"
              maxLength={9}
            />
          </div>

          <Input
            label="Telefone"
            value={form.telefone ?? ''}
            onChange={e => set('telefone', e.target.value || null)}
            placeholder="(11) 99999-9999"
          />

          <Input
            label="E-mail de contato"
            value={form.emailContato ?? ''}
            onChange={e => set('emailContato', e.target.value || null)}
            placeholder="contato@empresa.com.br"
            type="email"
          />

        </div>

        <div className="flex justify-end gap-3 pt-2 border-t">
          <Button type="button" variant="outline" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="submit" loading={loading}>
            {isEditing ? 'Salvar alterações' : 'Cadastrar empresa'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
