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
    <div className={['w-full overflow-x-auto rounded-xl border border-ds-border', className].join(' ')} {...props}>
      <table className="min-w-full divide-y divide-ds-border text-sm">
        {children}
      </table>
    </div>
  )
}

export function THead({ children, className = '', ...props }: THeadProps) {
  return (
    <thead className={['bg-ds-input', className].join(' ')} {...props}>
      {children}
    </thead>
  )
}

export function TBody({ children, className = '', ...props }: TBodyProps) {
  return (
    <tbody className={['divide-y divide-ds-border bg-white', className].join(' ')} {...props}>
      {children}
    </tbody>
  )
}

export function TRow({ children, className = '', ...props }: TRowProps) {
  return (
    <tr className={['hover:bg-ds-hover transition-colors', className].join(' ')} {...props}>
      {children}
    </tr>
  )
}

export function TH({ children, className = '', ...props }: THProps) {
  return (
    <th
      className={['px-4 py-3 text-left text-xs font-semibold text-ds-light uppercase tracking-wide', className].join(' ')}
      {...props}
    >
      {children}
    </th>
  )
}

export function TD({ children, className = '', ...props }: TDProps) {
  return (
    <td className={['px-4 py-3 text-ds-mid', className].join(' ')} {...props}>
      {children}
    </td>
  )
}
