import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { BookOpen, ChevronDown, X, type LucideIcon } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { menuAreas, navItems, type Area, type NavItem } from '../config/menuCatalog'
import { perfisApi, type PerfilCustomizado } from '../api/perfisApi'

interface SidebarProps {
  mobileOpen: boolean
  onMobileClose: () => void
}

/**
 * Usuário com um dos 5 papéis legados segue o caminho de código idêntico ao de sempre —
 * nenhum papel legado bate com `perfil_custom_*`, então essa função nunca é chamada pra eles.
 * Um usuário com perfil customizado nunca vê o Portal do Médico (fora do catálogo, role
 * `medico` fixa) e sempre vê telas sem `perm` no catálogo (ex.: Dashboard — agrega dados,
 * sem operação sensível própria); as demais só aparecem se a permissão estiver no perfil.
 */
function resolveVisibleItemsPorPerfilCustomizado(
  keycloakRoleName: string,
  perfis: PerfilCustomizado[]
): NavItem[] {
  const perfil = perfis.find(p => p.keycloakRoleName === keycloakRoleName)
  const permissoes = new Set(perfil?.permissoes ?? [])
  return navItems.filter(item => {
    if (item.roles.includes('medico')) return false
    if (!item.perm) return true
    return permissoes.has(item.perm)
  })
}

/** Quanto do caminho atual o item cobre (0 = não é a tela atual). O item mais específico vence. */
function matchLength(item: NavItem, pathname: string): number {
  let best = 0
  for (const path of [item.to, ...(item.activeAlso ?? [])]) {
    const exact = pathname === path
    const prefix = !item.end && path !== '/' && pathname.startsWith(path + '/')
    if ((exact || prefix) && path.length + 1 > best) best = path.length + 1
  }
  return best
}

type MenuEntry =
  | { kind: 'item'; item: NavItem }
  | { kind: 'group'; area: Area; icon: LucideIcon; items: NavItem[] }

/**
 * Itens sem `area` (Dashboard, Portal do Médico) ficam soltos no topo; os demais entram no
 * grupo da sua área, na ordem de `menuAreas`. Grupo vazio some e grupo com um item só vira
 * link direto, sem subnível.
 */
function buildMenu(items: NavItem[]): MenuEntry[] {
  const entries: MenuEntry[] = items.filter(i => !i.area).map(item => ({ kind: 'item', item }))
  for (const { area, icon } of menuAreas) {
    const doGrupo = items.filter(i => i.area === area)
    if (doGrupo.length === 1) entries.push({ kind: 'item', item: doGrupo[0] })
    else if (doGrupo.length > 1) entries.push({ kind: 'group', area, icon, items: doGrupo })
  }
  return entries
}

function itemClass(isActive: boolean, nested: boolean): string {
  return [
    'relative flex items-center gap-3 rounded-lg text-sm transition-colors',
    nested ? 'pl-[2.6rem] pr-3 py-2' : 'px-3 py-2.5',
    isActive
      ? 'bg-primary-50 text-primary font-bold'
      : 'text-ds-nav font-medium hover:bg-ds-input hover:text-ds-mid',
  ].join(' ')
}

export function Sidebar({ mobileOpen, onMobileClose }: SidebarProps) {
  const { user } = useAuth()
  const userRoles = user?.realm_access?.roles ?? []
  const [perfis, setPerfis] = useState<PerfilCustomizado[]>([])

  useEffect(() => {
    perfisApi.listar().then(setPerfis).catch(() => setPerfis([]))
  }, [])

  const perfilCustomizadoRole = userRoles.find(r => r.startsWith('perfil_custom_'))

  const visibleItems = perfilCustomizadoRole
    ? resolveVisibleItemsPorPerfilCustomizado(perfilCustomizadoRole, perfis)
    : navItems.filter(item => item.roles === null || item.roles.some(r => userRoles.includes(r)))

  const { pathname } = useLocation()
  const activeItem = visibleItems.reduce<NavItem | null>((best, item) => {
    const len = matchLength(item, pathname)
    return len > 0 && (!best || len > matchLength(best, pathname)) ? item : best
  }, null)
  const activeArea = activeItem?.area ?? null
  const menu = buildMenu(visibleItems)

  // Um grupo aberto por vez; ao trocar de tela, abre o grupo dela.
  const [openArea, setOpenArea] = useState<Area | null>(activeArea)
  useEffect(() => {
    if (activeArea) setOpenArea(activeArea)
  }, [activeArea, pathname])

  const renderItem = (item: NavItem, nested: boolean) => {
    const isActive = item === activeItem
    const Icon = item.icon
    return (
      <Link
        key={item.to}
        to={item.to}
        onClick={onMobileClose}
        aria-current={isActive ? 'page' : undefined}
        className={itemClass(isActive, nested)}
      >
        {isActive && (
          <span className="absolute left-0 top-[20%] bottom-[20%] w-0.5 bg-primary rounded-r" />
        )}
        {!nested && <Icon size={17} />}
        <span className="flex-1 min-w-0 truncate">{item.label}</span>
        {item.soon && (
          <span className="shrink-0 text-[10px] font-semibold uppercase tracking-wide text-ds-light bg-ds-input rounded px-1.5 py-0.5">
            em breve
          </span>
        )}
      </Link>
    )
  }

  const content = (
    <nav className="flex flex-col h-full bg-white">
      {/* Logo */}
      <div className="relative flex flex-col items-center justify-center px-4 pt-6 pb-4 border-b border-ds-border shrink-0">
        <button
          onClick={onMobileClose}
          className="md:hidden absolute right-3 top-3 p-1 rounded-lg text-ds-light hover:text-ds-mid hover:bg-ds-input transition-colors"
        >
          <X size={20} />
        </button>
        <img
          src="/logo-pinsaude.png"
          alt="Pin Saúde"
          className="h-9 w-auto [filter:brightness(0)_saturate(100%)_invert(50%)_sepia(98%)_saturate(3000%)_hue-rotate(173deg)_brightness(103%)]"
        />
        <p className="mt-2 text-[9px] font-semibold text-ds-light tracking-widest uppercase">
          Sistema de Gestão
        </p>
      </div>

      {/* Nav items */}
      <div className="flex-1 overflow-y-auto py-2.5 px-2 space-y-0.5">
        {menu.map(entry => {
          if (entry.kind === 'item') return renderItem(entry.item, false)
          const { area, icon: Icon, items } = entry
          const open = openArea === area
          const temAtivo = activeArea === area
          return (
            <div key={area}>
              <button
                type="button"
                onClick={() => setOpenArea(open ? null : area)}
                aria-expanded={open}
                className={[
                  'w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-semibold transition-colors hover:bg-ds-input',
                  temAtivo ? 'text-primary' : 'text-ds-mid',
                ].join(' ')}
              >
                <Icon size={17} />
                <span className="flex-1 text-left">{area}</span>
                <ChevronDown
                  size={15}
                  className={`text-ds-light transition-transform ${open ? 'rotate-180' : ''}`}
                />
              </button>
              {open && (
                <div className="space-y-px pb-1">
                  {items.map(item => renderItem(item, true))}
                </div>
              )}
            </div>
          )
        })}
      </div>

      {/* Bottom */}
      <div className="px-2 py-3 border-t border-ds-border shrink-0">
        <a
          href="/docs/guia-cadastros.html"
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-3 px-3 py-2.5 w-full rounded-lg text-sm font-medium text-ds-nav hover:bg-ds-input hover:text-ds-mid transition-colors"
        >
          <BookOpen size={17} />
          Documentação
        </a>
        <div className="mt-2 px-3 py-1.5 text-[10px] text-ds-light text-center tracking-wide">
          v0.1.0 — MVP
        </div>
      </div>
    </nav>
  )

  return (
    <>
      {/* Desktop sidebar */}
      <aside className="hidden md:flex md:flex-col md:w-60 md:shrink-0 border-r border-ds-border shadow-sm h-screen sticky top-0 z-10">
        {content}
      </aside>

      {/* Mobile overlay */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 md:hidden">
          <div
            className="absolute inset-0 bg-black/50"
            onClick={onMobileClose}
            aria-hidden="true"
          />
          <aside className="relative flex flex-col w-60 h-full shadow-xl">
            {content}
          </aside>
        </div>
      )}
    </>
  )
}
