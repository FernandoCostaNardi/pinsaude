import { Menu, Bell, ChevronDown, LogOut } from 'lucide-react'
import { useState } from 'react'
import { useLocation } from 'react-router-dom'
import { useAuth, type AuthUser } from '../auth/useAuth'

interface HeaderProps {
  onMenuClick: () => void
}

const PAGE_NAMES: Record<string, string> = {
  '/':            'Dashboard',
  '/medicos':     'Médicos',
  '/empresas':    'Empresas',
  '/notas':       'Notas Fiscais',
  '/repasses':    'Repasses',
  '/conciliacao': 'Conciliação',
  '/gestao':      'Gestão',
  '/usuarios':    'Usuários',
}

function userInitials(user: AuthUser): string {
  if (user.given_name && user.family_name) {
    return (user.given_name[0] + user.family_name[0]).toUpperCase()
  }
  if (user.name) {
    const parts = user.name.trim().split(/\s+/)
    return parts.length >= 2
      ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
      : parts[0].slice(0, 2).toUpperCase()
  }
  return user.email.slice(0, 2).toUpperCase()
}

function displayName(user: AuthUser): string {
  return user.name || user.given_name || user.email
}

const roleLabels: Record<string, string> = {
  medico:     'Médico',
  operacao:   'Operação',
  financeiro: 'Financeiro',
  contabil:   'Contábil',
  gestao:     'Gestão',
}

function primaryRole(user: AuthUser): string {
  const roles = user.realm_access?.roles ?? []
  for (const r of ['gestao', 'financeiro', 'contabil', 'operacao', 'medico']) {
    if (roles.includes(r)) return roleLabels[r] ?? r
  }
  return ''
}

export function Header({ onMenuClick }: HeaderProps) {
  const { user, logout }    = useAuth()
  const { pathname }        = useLocation()
  const [userMenuOpen, setUserMenuOpen] = useState(false)

  const initials  = user ? userInitials(user) : '?'
  const name      = user ? displayName(user)  : ''
  const email     = user?.email ?? ''
  const role      = user ? primaryRole(user)  : ''
  const pageName  = PAGE_NAMES[pathname] ?? 'Pin Saúde'

  return (
    <header className="h-16 bg-white border-b border-ds-border flex items-center px-4 gap-4 shrink-0">
      <button
        onClick={onMenuClick}
        className="md:hidden p-2 rounded-lg text-ds-light hover:bg-ds-input transition-colors"
        aria-label="Abrir menu"
      >
        <Menu size={20} />
      </button>

      {/* Breadcrumb */}
      <div className="flex-1">
        <p className="text-[10px] font-medium text-ds-light tracking-widest uppercase leading-none">Pin Saúde</p>
        <p className="text-base font-bold text-ds-text leading-tight">{pageName}</p>
      </div>

      {/* Bell */}
      <button className="relative p-2 rounded-lg text-ds-light hover:bg-ds-input transition-colors">
        <Bell size={20} />
        <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-primary" />
      </button>

      {/* User chip */}
      <div className="relative">
        <button
          onClick={() => setUserMenuOpen(v => !v)}
          className="flex items-center gap-2 px-2.5 py-1.5 rounded-xl border border-ds-border bg-white hover:bg-ds-input transition-colors"
        >
          <div className="h-[30px] w-[30px] rounded-full bg-gradient-to-br from-primary to-[#0082BE] flex items-center justify-center text-white text-xs font-bold shrink-0">
            {initials}
          </div>
          <div className="hidden sm:block">
            <p className="text-xs font-bold text-ds-text leading-tight">{name}</p>
            {role && <p className="text-[10px] text-ds-light leading-tight">{role}</p>}
          </div>
          <ChevronDown size={13} className="text-ds-light" />
        </button>

        {userMenuOpen && (
          <>
            <div
              className="fixed inset-0 z-10"
              onClick={() => setUserMenuOpen(false)}
              aria-hidden="true"
            />
            <div className="absolute right-0 top-full mt-1 w-56 bg-white rounded-xl border border-ds-border shadow-lg z-20 py-1">
              <div className="px-4 py-3 border-b border-ds-border">
                <p className="text-sm font-semibold text-ds-text">{name}</p>
                <p className="text-xs text-ds-light">{email}</p>
                {role && <p className="text-xs text-primary font-semibold mt-0.5">{role}</p>}
              </div>
              <button
                className="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors"
                onClick={() => { setUserMenuOpen(false); logout() }}
              >
                <LogOut size={16} />
                Sair da conta
              </button>
            </div>
          </>
        )}
      </div>
    </header>
  )
}
