import { useEffect, useMemo, useRef, useState } from 'react'
import { Scale } from 'lucide-react'
import rawLc116 from '../data/lc116.json'

// ─── Types & Data ─────────────────────────────────────────────────────────────

export interface Lc116Item {
  codigo: string   // e.g. "4.01"
  descricao: string
}

export const lc116Items = rawLc116 as Lc116Item[]

// ─── Helpers ──────────────────────────────────────────────────────────────────

function normalize(s: string) {
  return s.toLowerCase().normalize('NFD').replace(/\p{Mn}/gu, '')
}

// ─── Component ────────────────────────────────────────────────────────────────

interface Props {
  value: string      // selected codigo e.g. "4.01"
  onChange: (codigo: string, descricao: string) => void
  error?: string
}

export function Lc116Select({ value, onChange, error }: Props) {
  const selected = lc116Items.find(i => i.codigo === value) ?? null

  const [query, setQuery] = useState(() =>
    selected ? `${selected.codigo} — ${selected.descricao}` : ''
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
    if (!value) {
      setQuery('')
    } else {
      const found = lc116Items.find(i => i.codigo === value)
      if (found) setQuery(`${found.codigo} — ${found.descricao}`)
    }
  }, [value])

  const filtered = useMemo<Lc116Item[]>(() => {
    const q = query.trim()
    if (q.length < 1) return []

    const norm = normalize(q)

    const byCode  = lc116Items.filter(i => i.codigo.startsWith(q))
    const byDescr = lc116Items.filter(i => !byCode.includes(i) && normalize(i.descricao).includes(norm))

    return [...byCode, ...byDescr].slice(0, 12)
  }, [query])

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
        if (value) {
          const found = lc116Items.find(i => i.codigo === value)
          if (found) setQuery(`${found.codigo} — ${found.descricao}`)
        } else {
          setQuery('')
        }
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [value])

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
    setOpen(true)
    setActiveIndex(-1)
  }

  function handleSelect(item: Lc116Item) {
    setQuery(`${item.codigo} — ${item.descricao}`)
    setOpen(false)
    setActiveIndex(-1)
    onChange(item.codigo, item.descricao)
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (!open || filtered.length === 0) return
    if (e.key === 'ArrowDown') { e.preventDefault(); setActiveIndex(i => Math.min(i + 1, filtered.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActiveIndex(i => Math.max(i - 1, 0)) }
    else if (e.key === 'Enter' && activeIndex >= 0) { e.preventDefault(); handleSelect(filtered[activeIndex]) }
    else if (e.key === 'Escape') setOpen(false)
  }

  return (
    <div ref={containerRef} className="flex flex-col gap-1">
      <label className="text-sm font-medium text-gray-700">Código LC 116</label>
      <div className="relative flex items-center">
        <div className="absolute left-2.5 pointer-events-none flex items-center">
          {selected
            ? (
              <span className="text-xs font-bold text-violet-700 bg-violet-50 px-1.5 py-0.5 rounded font-mono">
                {selected.codigo}
              </span>
            )
            : <Scale size={14} className="text-ds-light" />
          }
        </div>
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={handleInputChange}
          onFocus={handleFocus}
          onKeyDown={handleKeyDown}
          placeholder="Código (4.01) ou descrição (medicina, clínica...)..."
          autoComplete="off"
          className={[
            'block w-full rounded-lg border py-2.5 pr-3 text-sm text-ds-text placeholder-ds-light',
            'focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary',
            selected ? 'pl-16' : 'pl-9',
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
          {filtered.map((item, i) => (
            <li
              key={item.codigo}
              onMouseDown={() => handleSelect(item)}
              onMouseEnter={() => setActiveIndex(i)}
              className={[
                'flex items-start gap-3 px-3 py-3 cursor-pointer border-b border-ds-border last:border-0',
                i === activeIndex ? 'bg-primary-50' : 'hover:bg-ds-hover',
              ].join(' ')}
            >
              <span className="shrink-0 font-mono text-xs font-bold text-violet-700 bg-violet-50 px-1.5 py-0.5 rounded mt-0.5 min-w-[2.8rem] text-center">
                {item.codigo}
              </span>
              <span className="text-sm text-ds-mid leading-snug">{item.descricao}</span>
            </li>
          ))}
        </ul>
      )}

      {open && query.trim().length >= 1 && filtered.length === 0 && (
        <div
          style={{ position: 'fixed', top: dropPos.top, left: dropPos.left, width: dropPos.width, zIndex: 200 }}
          className="rounded-xl border border-ds-border bg-white px-4 py-3 text-sm text-ds-light shadow-xl"
        >
          Nenhum item LC 116 encontrado para &ldquo;{query}&rdquo;
        </div>
      )}

      {error && <p className="text-xs text-red-600">{error}</p>}
      {!error && selected && (
        <p className="text-xs text-ds-light leading-snug">{selected.descricao}</p>
      )}
    </div>
  )
}
