import { ChangeEvent, forwardRef, InputHTMLAttributes } from 'react'
import { formatPhone } from '../utils/phone'

interface PhoneInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'> {
  label?: string
  value: string
  onChange: (formatted: string) => void
  error?: string
}

export const PhoneInput = forwardRef<HTMLInputElement, PhoneInputProps>(
  ({ label, value, onChange, error, disabled, ...props }, ref) => {
    function handleChange(e: ChangeEvent<HTMLInputElement>) {
      onChange(formatPhone(e.target.value))
    }

    return (
      <div className="flex flex-col gap-1">
        {label && <label className="text-sm font-medium text-gray-700">{label}</label>}
        <input
          ref={ref}
          value={value}
          onChange={handleChange}
          disabled={disabled}
          placeholder="(00) 00000-0000"
          maxLength={15}
          className={[
            'block w-full rounded-lg border px-3 py-2 text-sm',
            'focus:outline-none focus:ring-2',
            'disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed',
            error
              ? 'border-red-400 focus:ring-red-200 focus:border-red-400'
              : 'border-gray-300 focus:ring-primary-300 focus:border-primary',
          ].join(' ')}
          {...props}
        />
        {error && <p className="text-xs text-red-500">{error}</p>}
      </div>
    )
  }
)

PhoneInput.displayName = 'PhoneInput'
