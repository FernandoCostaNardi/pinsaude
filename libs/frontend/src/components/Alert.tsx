import { ReactNode } from 'react'
import { CheckCircle2, AlertTriangle, XCircle, Info, X } from 'lucide-react'

type AlertVariant = 'info' | 'success' | 'warning' | 'error'

interface AlertProps {
  variant?: AlertVariant
  title?: string
  children: ReactNode
  onClose?: () => void
  className?: string
}

const config: Record<AlertVariant, { icon: typeof Info; bg: string; border: string; text: string; iconColor: string }> = {
  info:    { icon: Info,          bg: 'bg-blue-50',   border: 'border-blue-200',  text: 'text-blue-800',  iconColor: 'text-blue-500'  },
  success: { icon: CheckCircle2,  bg: 'bg-green-50',  border: 'border-green-200', text: 'text-green-800', iconColor: 'text-green-500' },
  warning: { icon: AlertTriangle, bg: 'bg-yellow-50', border: 'border-yellow-200',text: 'text-yellow-800',iconColor: 'text-yellow-500'},
  error:   { icon: XCircle,       bg: 'bg-red-50',    border: 'border-red-200',   text: 'text-red-800',   iconColor: 'text-red-500'   },
}

export function Alert({ variant = 'info', title, children, onClose, className = '' }: AlertProps) {
  const { icon: Icon, bg, border, text, iconColor } = config[variant]

  return (
    <div className={['flex gap-3 p-4 rounded-lg border', bg, border, className].join(' ')} role="alert">
      <Icon className={['mt-0.5 shrink-0', iconColor].join(' ')} size={18} />
      <div className="flex-1 min-w-0">
        {title && <p className={['font-semibold text-sm mb-0.5', text].join(' ')}>{title}</p>}
        <div className={['text-sm', text].join(' ')}>{children}</div>
      </div>
      {onClose && (
        <button
          onClick={onClose}
          className={['shrink-0 p-0.5 rounded hover:opacity-70 transition-opacity', text].join(' ')}
        >
          <X size={16} />
        </button>
      )}
    </div>
  )
}
