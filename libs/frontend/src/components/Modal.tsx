import { ReactNode, useEffect } from 'react'
import { X } from 'lucide-react'

interface ModalProps {
  open: boolean
  onClose: () => void
  title?: ReactNode
  children: ReactNode
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl'
}

const sizeClasses = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-2xl',
  '2xl': 'max-w-3xl',
}

export function Modal({ open, onClose, title, children, size = 'md' }: ModalProps) {
  useEffect(() => {
    if (!open) return
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [open, onClose])

  if (!open) return null

  return (
    // Mobile: bottom sheet anchored to bottom edge, full width
    // Desktop (sm+): centered dialog with padding
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center sm:p-4">
      <div
        className="absolute inset-0 bg-black/40 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className={[
          'relative w-full bg-white flex flex-col shadow-xl',
          // Mobile: rounded top corners only, takes up to 92% of dynamic viewport
          'rounded-t-2xl max-h-[92dvh]',
          // Desktop: fully rounded, slightly shorter max height
          'sm:rounded-2xl sm:max-h-[90vh]',
          sizeClasses[size],
        ].join(' ')}
        role="dialog"
        aria-modal="true"
      >
        {/* Drag handle — visible only on mobile */}
        <div className="sm:hidden flex justify-center pt-3 shrink-0">
          <div className="w-9 h-1 rounded-full bg-gray-300" />
        </div>

        {title && (
          <div className="flex items-center justify-between px-4 sm:px-6 py-3 sm:py-4 border-b border-ds-border shrink-0">
            <div className="flex-1 min-w-0">
              {typeof title === 'string'
                ? <h2 className="text-base font-bold text-ds-text">{title}</h2>
                : title
              }
            </div>
            <button
              onClick={onClose}
              className="ml-3 shrink-0 p-1.5 rounded-lg text-ds-light hover:text-ds-mid hover:bg-ds-input transition-colors"
            >
              <X size={18} />
            </button>
          </div>
        )}
        <div className="overflow-y-auto px-4 sm:px-6 py-4 flex-1">{children}</div>
      </div>
    </div>
  )
}
