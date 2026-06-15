import { Menu, Bell, ChevronDown, LogOut } from 'lucide-react'
import { useState } from 'react'
import { useAuth, type AuthUser } from '../auth/useAuth'

interface HeaderProps {
  onMenuClick: () => void
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
  const [userMenuOpen, setUserMenuOpen] = useState(false)

  const initials = user ? userInitials(user) : '?'
  const name     = user ? displayName(user)  : ''
  const email    = user?.email ?? ''
  const role     = user ? primaryRole(user)  : ''

  return (
    <header className="h-16 bg-white border-b border-gray-200 flex items-center px-4 gap-4 shrink-0">
      <button
        onClick={onMenuClick}
        className="md:hidden p-2 rounded-lg text-gray-500 hover:bg-gray-100 transition-colors"
        aria-label="Abrir menu"
      >
        <Menu size={20} />
      </button>

      <div className="flex-1" />

      <button className="relative p-2 rounded-lg text-gray-500 hover:bg-gray-100 transition-colors">
        <Bell size={20} />
        <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-primary" />
      </button>

      <div className="relative">
        <button
          onClick={() => setUserMenuOpen(v => !v)}
          className="flex items-center gap-2 px-3 py-1.5 rounded-lg hover:bg-gray-100 transition-colors"
        >
          <div className="h-7 w-7 rounded-full bg-primary flex items-center justify-center text-white text-xs font-semibold">
            {initials}
          </div>
          <div className="hidden sm:flex flex-col items-start">
            <span className="text-sm font-medium text-gray-700 leading-tight">{name}</span>
            {role && <span className="text-xs text-gray-400 leading-tight">{role}</span>}
          </div>
          <ChevronDown size={16} className="text-gray-400" />
        </button>

        {userMenuOpen && (
          <>
            <div
              className="fixed inset-0 z-10"
              onClick={() => setUserMenuOpen(false)}
              aria-hidden="true"
            />
            <div className="absolute right-0 top-full mt-1 w-56 bg-white rounded-xl border border-gray-200 shadow-lg z-20 py-1">
              <div className="px-4 py-3 border-b border-gray-200">
                <p className="text-sm font-medium text-gray-900">{name}</p>
                <p className="text-xs text-gray-500">{email}</p>
                {role && <p className="text-xs text-primary font-medium mt-0.5">{role}</p>}
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
