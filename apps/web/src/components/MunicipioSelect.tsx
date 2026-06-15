import { useEffect, useMemo, useRef, useState } from 'react'
import { MapPin } from 'lucide-react'
import { municipios, Municipio } from '../hooks/useMunicipios'

interface Props {
  municipio: string
  codigo: string
  onChange: (municipio: string, codigo: string, uf: string) => void
  error?: string
}

function normalize(s: string) {
  return s.toLowerCase().normalize('NFD').replace(/\p{Mn}/gu, '')
}

export function MunicipioSelect({ municipio, codigo, onChange, error }: Props) {
  const [query, setQuery] = useState(municipio)
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setQuery(municipio || '')
  }, [municipio])

  const filtered = useMemo<Municipio[]>(() => {
    const q = query.trim()
    if (q.length < 2) return []
    const norm = normalize(q)
    return municipios
      .filter(m =>
        normalize(m.nome).includes(norm) ||
        m.uf.toLowerCase() === q.toLowerCase()
      )
      .slice(0, 10)
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
    setOpen(true)
    setActiveIndex(-1)
    if (v !== municipio) onChange('', '', '')
  }

  function handleSelect(m: Municipio) {
    setQuery(`${m.nome} - ${m.uf}`)
    setOpen(false)
    setActiveIndex(-1)
    onChange(m.nome, m.codigo, m.uf)
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (!open || filtered.length === 0) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActiveIndex(i => Math.min(i + 1, filtered.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActiveIndex(i => Math.max(i - 1, 0))
    } else if (e.key === 'Enter' && activeIndex >= 0) {
      e.preventDefault()
      handleSelect(filtered[activeIndex])
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  const isSelected = !!codigo

  return (
    <div ref={containerRef} className="relative flex flex-col gap-1">
      <label className="text-sm font-medium text-gray-700">Município</label>
      <div className="relative">
        <MapPin
          size={14}
          className={[
            'absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none',
            isSelected ? 'text-primary' : 'text-gray-400',
          ].join(' ')}
        />
        <input
          type="text"
          value={query}
          onChange={handleInputChange}
          onFocus={() => query.length >= 2 && setOpen(true)}
          onKeyDown={handleKeyDown}
          placeholder="Digite o nome da cidade..."
          autoComplete="off"
          className={[
            'block w-full rounded-lg border pl-9 pr-3 py-2 text-sm text-gray-900 placeholder-gray-400',
            'focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary',
            error ? 'border-red-400 focus:ring-red-200 focus:border-red-500' : 'border-gray-300',
          ].join(' ')}
        />
        {isSelected && (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono text-gray-400 pointer-events-none">
            {codigo}
          </span>
        )}
      </div>

      {open && filtered.length > 0 && (
        <ul className="absolute top-[calc(100%-4px)] left-0 right-0 z-50 mt-1 max-h-56 overflow-y-auto rounded-lg border border-gray-200 bg-white shadow-xl">
          {filtered.map((m, i) => (
            <li
              key={m.codigo}
              onMouseDown={() => handleSelect(m)}
              onMouseEnter={() => setActiveIndex(i)}
              className={[
                'flex items-center justify-between px-3 py-2.5 text-sm cursor-pointer',
                i === activeIndex ? 'bg-primary-50' : 'hover:bg-gray-50',
              ].join(' ')}
            >
              <span className="text-gray-900">{m.nome}</span>
              <span className="ml-3 shrink-0 rounded px-1.5 py-0.5 text-xs font-medium bg-gray-100 text-gray-500">
                {m.uf}
              </span>
            </li>
          ))}
        </ul>
      )}

      {open && query.trim().length >= 2 && filtered.length === 0 && (
        <div className="absolute top-[calc(100%-4px)] left-0 right-0 z-50 mt-1 rounded-lg border border-gray-200 bg-white px-3 py-3 text-sm text-gray-500 shadow-xl">
          Nenhum município encontrado para &ldquo;{query}&rdquo;
        </div>
      )}

      {error && <p className="text-xs text-red-600">{error}</p>}
      {!error && isSelected && (
        <p className="text-xs text-gray-400">IBGE {codigo}</p>
      )}
    </div>
  )
}
