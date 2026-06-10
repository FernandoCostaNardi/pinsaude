import { ButtonHTMLAttributes, ReactNode } from 'react'

type Variant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
  children: ReactNode
}

const variantClasses: Record<Variant, string> = {
  primary:   'bg-primary text-white hover:bg-primary-600 focus:ring-primary-300 disabled:bg-primary-200',
  secondary: 'bg-brand-blue text-white hover:bg-blue-700 focus:ring-blue-300 disabled:bg-blue-200',
  outline:   'border border-primary text-primary bg-transparent hover:bg-primary-50 focus:ring-primary-200',
  ghost:     'text-gray-700 bg-transparent hover:bg-gray-100 focus:ring-gray-200',
  danger:    'bg-red-600 text-white hover:bg-red-700 focus:ring-red-300 disabled:bg-red-200',
}

const sizeClasses: Record<Size, string> = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2 text-sm',
  lg: 'px-6 py-2.5 text-base',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  className = '',
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      disabled={disabled || loading}
      className={[
        'inline-flex items-center justify-center gap-2 font-medium rounded-lg',
        'transition-colors focus:outline-none focus:ring-2 focus:ring-offset-1',
        'disabled:cursor-not-allowed',
        variantClasses[variant],
        sizeClasses[size],
        className,
      ].join(' ')}
      {...props}
    >
      {loading && (
        <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
        </svg>
      )}
      {children}
    </button>
  )
}
