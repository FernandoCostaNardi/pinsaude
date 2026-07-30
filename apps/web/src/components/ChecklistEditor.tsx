import { useState } from 'react'
import { CheckCircle2, XCircle } from 'lucide-react'
import { Button } from '@pinsaude/ui'
import { Medico, medicosApi } from '../api/medicosApi'

type Checklist = NonNullable<Medico['checklist']>

export function ChecklistEditor({
  checklist,
  medicoId,
  canEdit,
  verificadoPor,
  onSaved,
}: {
  checklist: Checklist
  medicoId: string
  canEdit: boolean
  verificadoPor: string
  onSaved: (updated: Checklist) => void
}) {
  const [conselho,    setConselho]    = useState(checklist.numeroConselhoVerificado)
  const [disciplinar, setDisciplinar] = useState(checklist.registrosDisciplinares)
  const [processos,   setProcessos]   = useState(checklist.processosMedicos)
  const [saving,      setSaving]      = useState(false)
  const [error,       setError]       = useState<string | null>(null)

  const dirty =
    conselho    !== checklist.numeroConselhoVerificado ||
    disciplinar !== checklist.registrosDisciplinares  ||
    processos   !== checklist.processosMedicos

  async function handleSave() {
    setSaving(true)
    setError(null)
    try {
      const updated = await medicosApi.atualizarChecklist(medicoId, {
        numeroConselhoVerificado: conselho,
        registrosDisciplinares:  disciplinar,
        processosMedicos:        processos,
        verificadoPor,
      })
      onSaved(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao salvar checklist')
    } finally {
      setSaving(false)
    }
  }

  const items = [
    { label: 'Número do conselho verificado', value: conselho,    setter: setConselho    },
    { label: 'Registros disciplinares verificados', value: disciplinar, setter: setDisciplinar },
    { label: 'Processos médicos verificados', value: processos,   setter: setProcessos   },
  ]

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide">Checklist de Conduta</p>
        {checklist.completo && (
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-green-50 text-green-700 border border-green-200">
            Completo
          </span>
        )}
      </div>
      <div className="flex flex-col gap-2">
        {items.map(({ label, value, setter }) =>
          canEdit ? (
            <label key={label} className="flex items-center gap-2.5 cursor-pointer group">
              <input
                type="checkbox"
                checked={value}
                onChange={e => setter(e.target.checked)}
                className="w-4 h-4 rounded border-ds-border text-primary focus:ring-primary/30 cursor-pointer"
              />
              <span className="text-sm text-ds-text group-hover:text-primary transition-colors">{label}</span>
            </label>
          ) : (
            <div key={label} className="flex items-center gap-2">
              {value
                ? <CheckCircle2 size={15} className="text-green-500 shrink-0" />
                : <XCircle size={15} className="text-gray-300 shrink-0" />}
              <span className={`text-sm ${value ? 'text-ds-text' : 'text-ds-light'}`}>{label}</span>
            </div>
          )
        )}
      </div>
      {canEdit && dirty && (
        <div className="mt-3 flex items-center gap-2">
          <Button size="sm" onClick={handleSave} disabled={saving}>
            {saving ? 'Salvando...' : 'Salvar checklist'}
          </Button>
          <button
            onClick={() => { setConselho(checklist.numeroConselhoVerificado); setDisciplinar(checklist.registrosDisciplinares); setProcessos(checklist.processosMedicos) }}
            className="text-xs text-ds-mid hover:text-ds-text transition-colors"
          >
            Cancelar
          </button>
        </div>
      )}
      {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
      {checklist.verificadoPor && (
        <p className="text-xs text-ds-light mt-2">
          Verificado por {checklist.verificadoPor}
          {checklist.verificadoEm ? ` em ${new Date(checklist.verificadoEm).toLocaleDateString('pt-BR')}` : ''}
        </p>
      )}
    </div>
  )
}
