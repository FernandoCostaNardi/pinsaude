import { HTMLAttributes, ReactNode, TdHTMLAttributes, ThHTMLAttributes } from 'react'

interface TableProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
}

interface THeadProps extends HTMLAttributes<HTMLTableSectionElement> { children: ReactNode }
interface TBodyProps extends HTMLAttributes<HTMLTableSectionElement> { children: ReactNode }
interface TRowProps  extends HTMLAttributes<HTMLTableRowElement>     { children: ReactNode }
interface THProps    extends ThHTMLAttributes<HTMLTableCellElement>  { children: ReactNode }
interface TDProps    extends TdHTMLAttributes<HTMLTableCellElement>  { children: ReactNode }

export function Table({ children, className = '', ...props }: TableProps) {
  return (
    <div className={['w-full overflow-x-auto rounded-xl border border-gray-200', className].join(' ')} {...props}>
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        {children}
      </table>
    </div>
  )
}

export function THead({ children, className = '', ...props }: THeadProps) {
  return (
    <thead className={['bg-gray-50', className].join(' ')} {...props}>
      {children}
    </thead>
  )
}

export function TBody({ children, className = '', ...props }: TBodyProps) {
  return (
    <tbody className={['divide-y divide-gray-100 bg-white', className].join(' ')} {...props}>
      {children}
    </tbody>
  )
}

export function TRow({ children, className = '', ...props }: TRowProps) {
  return (
    <tr className={['hover:bg-gray-50 transition-colors', className].join(' ')} {...props}>
      {children}
    </tr>
  )
}

export function TH({ children, className = '', ...props }: THProps) {
  return (
    <th
      className={['px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide', className].join(' ')}
      {...props}
    >
      {children}
    </th>
  )
}

export function TD({ children, className = '', ...props }: TDProps) {
  return (
    <td className={['px-4 py-3 text-gray-700', className].join(' ')} {...props}>
      {children}
    </td>
  )
}
