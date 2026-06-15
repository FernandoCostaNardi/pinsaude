import { useEffect, useMemo, useRef, useState } from 'react'
import { Hash } from 'lucide-react'
import rawCnaes from '../data/cnaes.json'

// ─── Types & Data ─────────────────────────────────────────────────────────────

export interface CnaeData {
  id: string        // 7-digit code e.g. "8630503"
  descricao: string // uppercase description from IBGE
}

export const cnaes = rawCnaes as CnaeData[]

// ─── Helpers ──────────────────────────────────────────────────────────────────

function normalize(s: string) {
  return s.toLowerCase().normalize('NFD').replace(/\p{Mn}/gu, '')
}

export function formatCnae(id: string) {
  // 8630503 → 8630-5/03
  if (id.length === 7) return `${id.slice(0, 4)}-${id[4]}/${id.slice(5)}`
  return id
}

// ─── Component ────────────────────────────────────────────────────────────────

interface Props {
  cnaeCodigo: string
  cnaeDescricao: string
  onChange: (codigo: string, descricao: string) => void
  error?: string
}

export function CnaeSelect({ cnaeCodigo, cnaeDescricao, onChange, error }: Props) {
  const [query, setQuery] = useState(() =>
    cnaeCodigo ? `${formatCnae(cnaeCodigo)} — ${cnaeDescricao}` : ''
  )
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const containerRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  // Fixed dropdown position — avoids overflow-hidden clipping inside modals
  const [dropPos, setDropPos] = useState({ top: 0, left: 0, width: 0 })

  function calcDropPos() {
    const rect = inputRef.current?.getBoundingClientRect()
    if (rect) setDropPos({ top: rect.bottom + 4, left: rect.left, width: rect.width })
  }

  // Sync when value changes externally
  useEffect(() => {
    if (!cnaeCodigo) {
      setQuery('')
    } else {
      setQuery(`${formatCnae(cnaeCodigo)} — ${cnaeDescricao}`)
    }
  }, [cnaeCodigo, cnaeDescricao])

  const filtered = useMemo<CnaeData[]>(() => {
    const q = query.trim()
    if (q.length < 2) return []

    const digits = q.replace(/\D/g, '')
    const norm = normalize(q)

    const byCode  = digits.length >= 2 ? cnaes.filter(c => c.id.startsWith(digits)) : []
    const byDescr = cnaes.filter(c => !byCode.includes(c) && normalize(c.descricao).includes(norm))

    return [...byCode, ...byDescr].slice(0, 10)
  }, [query])

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
        if (cnaeCodigo) {
          setQuery(`${formatCnae(cnaeCodigo)} — ${cnaeDescricao}`)
        } else {
          setQuery('')
        }
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [cnaeCodigo, cnaeDescricao])

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.value
    setQuery(v)
    calcDropPos()
    setOpen(true)
    setActiveIndex(-1)
    if (!v) onChange('', '')
  }

  function handleFocus() {
    calcDropPos()
    if (query.length >= 2) setOpen(true)
  }

  function handleSelect(c: CnaeData) {
    setQuery(`${formatCnae(c.id)} — ${c.descricao}`)
    setOpen(false)
    setActiveIndex(-1)
    onChange(c.id, c.descricao)
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (!open || filtered.length === 0) return
    if (e.key === 'ArrowDown') { e.preventDefault(); setActiveIndex(i => Math.min(i + 1, filtered.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActiveIndex(i => Math.max(i - 1, 0)) }
    else if (e.key === 'Enter' && activeIndex >= 0) { e.preventDefault(); handleSelect(filtered[activeIndex]) }
    else if (e.key === 'Escape') setOpen(false)
  }

  const isSelected = !!cnaeCodigo

  return (
    <div ref={containerRef} className="flex flex-col gap-1">
      <label className="text-sm font-medium text-gray-700">CNAE</label>
      <div className="relative flex items-center">
        <div className="absolute left-2.5 pointer-events-none flex items-center">
          {isSelected
            ? (
              <span className="text-xs font-bold text-primary bg-primary-50 px-1.5 py-0.5 rounded font-mono">
                {formatCnae(cnaeCodigo)}
              </span>
            )
            : <Hash size={14} className="text-ds-light" />
          }
        </div>
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={handleInputChange}
          onFocus={handleFocus}
          onKeyDown={handleKeyDown}
          placeholder="Código (8630) ou descrição (ambulatorial)..."
          autoComplete="off"
          className={[
            'block w-full rounded-lg border py-2.5 pr-3 text-sm text-ds-text placeholder-ds-light',
            'focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary',
            isSelected ? 'pl-24' : 'pl-9',
            error ? 'border-red-400 focus:ring-red-200 focus:border-red-500' : 'border-ds-border',
          ].join(' ')}
        />
      </div>

      {/* Fixed-position dropdown — never clipped by overflow containers */}
      {open && filtered.length > 0 && (
        <ul
          style={{ position: 'fixed', top: dropPos.top, left: dropPos.left, width: dropPos.width, zIndex: 200 }}
          className="max-h-64 overflow-y-auto rounded-xl border border-ds-border bg-white shadow-xl"
        >
          {filtered.map((c, i) => (
            <li
              key={c.id}
              onMouseDown={() => handleSelect(c)}
              onMouseEnter={() => setActiveIndex(i)}
              className={[
                'flex items-start gap-3 px-3 py-3 cursor-pointer border-b border-ds-border last:border-0',
                i === activeIndex ? 'bg-primary-50' : 'hover:bg-ds-hover',
              ].join(' ')}
            >
              <span className="shrink-0 font-mono text-xs font-bold text-primary bg-primary-50 px-1.5 py-0.5 rounded mt-0.5">
                {formatCnae(c.id)}
              </span>
              <span className="text-sm text-ds-mid leading-snug">
                {c.descricao.charAt(0) + c.descricao.slice(1).toLowerCase()}
              </span>
            </li>
          ))}
        </ul>
      )}

      {open && query.trim().length >= 2 && filtered.length === 0 && (
        <div
          style={{ position: 'fixed', top: dropPos.top, left: dropPos.left, width: dropPos.width, zIndex: 200 }}
          className="rounded-xl border border-ds-border bg-white px-4 py-3 text-sm text-ds-light shadow-xl"
        >
          Nenhum CNAE encontrado para &ldquo;{query}&rdquo;
        </div>
      )}

      {error && <p className="text-xs text-red-600">{error}</p>}
      {!error && isSelected && (
        <p className="text-xs text-ds-light leading-snug">
          {cnaeCodigo} — {cnaeDescricao.charAt(0) + cnaeDescricao.slice(1).toLowerCase()}
        </p>
      )}
    </div>
  )
}
