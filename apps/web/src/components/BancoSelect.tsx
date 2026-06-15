import { useEffect, useMemo, useRef, useState } from 'react'
import { Landmark } from 'lucide-react'
import rawBancos from '../data/bancos.json'

// ─── Types & Data ─────────────────────────────────────────────────────────────

export interface BancoData {
  compe: string
  nome: string
  domain: string
}

const DISPLAY_NAMES: Record<string, string> = {
  '033': 'Santander',
  '341': 'Itaú Unibanco',
  '260': 'Nubank',
  '756': 'Sicoob',
  '748': 'Sicredi',
  '197': 'Stone',
  '290': 'PagSeguro',
  '380': 'PicPay',
  '323': 'Mercado Pago',
}

export const bancos: BancoData[] = (
  rawBancos as Array<{ c: string; n: string; d: string }>
).map(b => ({
  compe: b.c,
  nome: DISPLAY_NAMES[b.c] ?? b.n,
  domain: b.d,
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

const BG = ['#DBEAFE','#EDE9FE','#D1FAE5','#FEF3C7','#FCE7F3','#CFFAFE','#E0E7FF','#FEE2E2','#FEF9C3','#ECFDF5']
const FG = ['#1D4ED8','#7C3AED','#065F46','#92400E','#9D174D','#0E7490','#4338CA','#991B1B','#854D0E','#064E3B']

function bankColor(compe: string) {
  const idx = parseInt(compe, 10) % BG.length
  return { bg: BG[idx], fg: FG[idx] }
}

function initials(nome: string) {
  const words = nome.split(/\s+/).filter(w => w.length > 2)
  return (words.length >= 2 ? words.slice(0, 2) : nome.split(/\s+/).slice(0, 2))
    .map(w => w[0].toUpperCase()).join('').slice(0, 2) || nome.slice(0, 2).toUpperCase()
}

function normalize(s: string) {
  return s.toLowerCase().normalize('NFD').replace(/\p{Mn}/gu, '')
}

// ─── BancoAvatar ─────────────────────────────────────────────────────────────

export function BancoAvatar({ banco, size = 32 }: { banco: BancoData; size?: number }) {
  const [imgError, setImgError] = useState(false)
  const { bg, fg } = bankColor(banco.compe)

  if (banco.domain && !imgError) {
    return (
      <img
        src={`https://logo.clearbit.com/${banco.domain}?size=${size * 2}`}
        alt={banco.nome}
        width={size}
        height={size}
        className="rounded object-contain shrink-0 bg-white"
        onError={() => setImgError(true)}
        loading="lazy"
      />
    )
  }

  return (
    <div
      style={{ width: size, height: size, backgroundColor: bg, color: fg }}
      className="rounded flex items-center justify-center text-xs font-bold shrink-0 select-none"
    >
      {initials(banco.nome)}
    </div>
  )
}

// ─── BancoSelect ─────────────────────────────────────────────────────────────

interface Props {
  value: string
  onChange: (nome: string, compe: string) => void
  error?: string
}

export function BancoSelect({ value, onChange, error }: Props) {
  const [query, setQuery] = useState(value)
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const [selected, setSelected] = useState<BancoData | null>(
    () => bancos.find(b => b.nome === value) ?? null
  )
  const containerRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  // Fixed dropdown position — avoids overflow-hidden clipping inside modals
  const [dropPos, setDropPos] = useState({ top: 0, left: 0, width: 0 })

  function calcDropPos() {
    const rect = inputRef.current?.getBoundingClientRect()
    if (rect) setDropPos({ top: rect.bottom + 4, left: rect.left, width: rect.width })
  }

  useEffect(() => {
    if (!value) {
      setQuery('')
      setSelected(null)
    } else {
      const found = bancos.find(b => b.nome === value)
      if (found) {
        setQuery(found.nome)
        setSelected(found)
      }
    }
  }, [value])

  const filtered = useMemo<BancoData[]>(() => {
    const q = query.trim()
    if (q.length < 2) return []
    const norm = normalize(q)
    const byCode = bancos.filter(b => b.compe.startsWith(q))
    const byName = bancos.filter(b => !b.compe.startsWith(q) && normalize(b.nome).includes(norm))
    return [...byCode, ...byName].slice(0, 8)
  }, [query])

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.value
    setQuery(v)
    calcDropPos()
    setOpen(true)
    setActiveIndex(-1)
    setSelected(null)
    if (!v) onChange('', '')
  }

  function handleFocus() {
    calcDropPos()
    if (query.length >= 2) setOpen(true)
  }

  function handleSelect(b: BancoData) {
    setQuery(b.nome)
    setSelected(b)
    setOpen(false)
    setActiveIndex(-1)
    onChange(b.nome, b.compe)
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
      <label className="text-sm font-medium text-gray-700">Banco *</label>
      <div className="relative flex items-center">
        <div className="absolute left-2.5 pointer-events-none flex items-center">
          {selected
            ? <BancoAvatar banco={selected} size={20} />
            : <Landmark size={14} className="text-gray-400" />
          }
        </div>
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={handleInputChange}
          onFocus={handleFocus}
          onKeyDown={handleKeyDown}
          placeholder="Nome ou código (ex: 341, Itaú, Nubank)..."
          autoComplete="off"
          className={[
            'block w-full rounded-lg border pl-9 pr-3 py-2.5 text-sm text-gray-900 placeholder-gray-400',
            'focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary',
            error ? 'border-red-400 focus:ring-red-200 focus:border-red-500' : 'border-gray-300',
          ].join(' ')}
        />
      </div>

      {/* Fixed-position dropdown — never clipped by overflow containers */}
      {open && filtered.length > 0 && (
        <ul
          style={{ position: 'fixed', top: dropPos.top, left: dropPos.left, width: dropPos.width, zIndex: 200 }}
          className="max-h-64 overflow-y-auto rounded-lg border border-gray-200 bg-white shadow-xl"
        >
          {filtered.map((b, i) => (
            <li
              key={b.compe}
              onMouseDown={() => handleSelect(b)}
              onMouseEnter={() => setActiveIndex(i)}
              className={[
                'flex items-center gap-3 px-3 py-3 cursor-pointer',
                i === activeIndex ? 'bg-primary-50' : 'hover:bg-gray-50',
              ].join(' ')}
            >
              <BancoAvatar banco={b} size={28} />
              <span className="flex-1 text-sm text-gray-900">{b.nome}</span>
              <span className="shrink-0 font-mono text-xs text-gray-400">{b.compe}</span>
            </li>
          ))}
        </ul>
      )}

      {open && query.trim().length >= 2 && filtered.length === 0 && (
        <div
          style={{ position: 'fixed', top: dropPos.top, left: dropPos.left, width: dropPos.width, zIndex: 200 }}
          className="rounded-lg border border-gray-200 bg-white px-3 py-3 text-sm text-gray-500 shadow-xl"
        >
          Nenhum banco encontrado para &ldquo;{query}&rdquo;
        </div>
      )}

      {error && <p className="text-xs text-red-600">{error}</p>}
      {!error && selected && (
        <p className="text-xs text-gray-400">Código COMPE {selected.compe}</p>
      )}
    </div>
  )
}
