import { HTMLAttributes, ReactNode } from 'react'

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
}

interface CardHeaderProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
}

interface CardBodyProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
}

interface CardFooterProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
}

export function Card({ children, className = '', ...props }: CardProps) {
  return (
    <div
      className={['bg-white rounded-xl border border-gray-200 shadow-sm', className].join(' ')}
      {...props}
    >
      {children}
    </div>
  )
}

export function CardHeader({ children, className = '', ...props }: CardHeaderProps) {
  return (
    <div
      className={['px-5 py-4 border-b border-gray-100 flex items-center justify-between', className].join(' ')}
      {...props}
    >
      {children}
    </div>
  )
}

export function CardBody({ children, className = '', ...props }: CardBodyProps) {
  return (
    <div className={['px-5 py-4', className].join(' ')} {...props}>
      {children}
    </div>
  )
}

export function CardFooter({ children, className = '', ...props }: CardFooterProps) {
  return (
    <div
      className={['px-5 py-4 border-t border-gray-100 bg-gray-50 rounded-b-xl', className].join(' ')}
      {...props}
    >
      {children}
    </div>
  )
}
