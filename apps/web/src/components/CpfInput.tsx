import { ChangeEvent, forwardRef, InputHTMLAttributes } from 'react'
import { CheckCircle, XCircle } from 'lucide-react'
import { formatCpf, isValidCpf } from '../utils/cpf'

interface CpfInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'> {
  label?: string
  value: string
  onChange: (formatted: string) => void
  error?: string
}

export const CpfInput = forwardRef<HTMLInputElement, CpfInputProps>(
  ({ label, value, onChange, error, disabled, ...props }, ref) => {
    const digits = value.replace(/\D/g, '')
    const complete = digits.length === 11
    const valid = complete && isValidCpf(value)
    const invalid = complete && !valid

    function handleChange(e: ChangeEvent<HTMLInputElement>) {
      onChange(formatCpf(e.target.value))
    }

    const borderClass = error || invalid
      ? 'border-red-400 focus:ring-red-300 focus:border-red-400'
      : valid
        ? 'border-green-400 focus:ring-green-300 focus:border-green-400'
        : 'border-gray-300 focus:ring-primary-300 focus:border-primary'

    return (
      <div className="flex flex-col gap-1">
        {label && <label className="text-sm font-medium text-gray-700">{label}</label>}
        <div className="relative">
          <input
            ref={ref}
            value={value}
            onChange={handleChange}
            disabled={disabled}
            placeholder="000.000.000-00"
            maxLength={14}
            className={[
              'block w-full rounded-lg border px-3 py-2 text-sm',
              'focus:outline-none focus:ring-2',
              'disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed',
              complete ? 'pr-9' : '',
              borderClass,
            ].join(' ')}
            {...props}
          />
          {valid && (
            <CheckCircle size={16} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-green-500 pointer-events-none" />
          )}
          {invalid && !error && (
            <XCircle size={16} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-red-500 pointer-events-none" />
          )}
        </div>
        {(error || (invalid && !error)) && (
          <p className="text-xs text-red-500">{error ?? 'CPF inválido'}</p>
        )}
      </div>
    )
  }
)

CpfInput.displayName = 'CpfInput'
