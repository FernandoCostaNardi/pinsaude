import { CheckCircle } from 'lucide-react'
import type { ComponentType } from 'react'

export interface StepWizardStep {
  label: string
  icon: ComponentType<{ className?: string }>
}

interface StepWizardProps {
  steps: StepWizardStep[]
  current: number
  maxVisited: number
  onStepClick: (index: number) => void
  className?: string
}

/**
 * Indicador de progresso de wizard multi-etapas (círculos numerados + linha de conexão).
 * Extraído de MedicoWizardModal/EmpresaWizardModal/ContaBancariaWizardModal (EPIC-14.5) —
 * só renderiza o indicador; navegação (Voltar/Próximo/Salvar) e validação por etapa
 * continuam responsabilidade de quem usa. Sem acoplamento a Modal — funciona tanto dentro
 * de um Modal (vira bottom-sheet no mobile) quanto numa página full-page.
 */
export function StepWizard({ steps, current, maxVisited, onStepClick, className = '' }: StepWizardProps) {
  return (
    <nav className={['flex items-start justify-center', className].join(' ')}>
      {steps.map(({ label, icon: Icon }, i) => {
        const isDone = i < current
        const isActive = i === current
        const isClickable = i <= maxVisited

        return (
          <div key={i} className="flex items-start">
            <button
              type="button"
              onClick={() => isClickable && onStepClick(i)}
              disabled={!isClickable}
              className="flex flex-col items-center gap-1 w-14 sm:w-24"
            >
              <div
                className={[
                  'w-9 h-9 sm:w-11 sm:h-11 rounded-full flex items-center justify-center border-2 transition-all duration-300',
                  isDone
                    ? 'bg-secondary-100 border-secondary-400 text-secondary-700'
                    : isActive
                    ? 'bg-primary border-primary text-white shadow-lg shadow-primary/20'
                    : 'bg-white border-gray-200 text-gray-300',
                ].join(' ')}
              >
                {isDone
                  ? <CheckCircle className="w-4 h-4 sm:w-5 sm:h-5 text-secondary-600" />
                  : <Icon className="w-4 h-4 sm:w-5 sm:h-5" />
                }
              </div>
              {/* Label visível apenas no desktop */}
              <span className={[
                'hidden sm:block text-xs font-medium text-center leading-tight w-20',
                isDone ? 'text-secondary-700' : isActive ? 'text-primary-700' : 'text-gray-400',
              ].join(' ')}>
                {label}
              </span>
              {/* Número compacto no mobile */}
              <span className={[
                'sm:hidden text-[10px] font-semibold',
                isDone ? 'text-secondary-600' : isActive ? 'text-primary' : 'text-gray-400',
              ].join(' ')}>
                {i + 1}
              </span>
            </button>

            {i < steps.length - 1 && (
              <div className="flex-1 h-0.5 mt-[18px] sm:mt-5 mx-1 bg-gray-200 rounded-full overflow-hidden">
                <div
                  className="h-full bg-primary rounded-full transition-all duration-500"
                  style={{ width: i < current ? '100%' : '0%' }}
                />
              </div>
            )}
          </div>
        )
      })}
    </nav>
  )
}
