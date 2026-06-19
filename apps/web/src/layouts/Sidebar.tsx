import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard,
  Stethoscope,
  Building2,
  Hospital,
  ClipboardList,
  FileText,
  Banknote,
  ArrowLeftRight,
  BarChart3,
  Users,
  Settings,
  X,
  ClipboardCheck,
} from 'lucide-react'
import { useAuth } from '../auth/AuthContext'

const navItems = [
  { to: '/',            label: 'Dashboard',   icon: LayoutDashboard, roles: null       },
  { to: '/medicos',           label: 'Médicos',    icon: Stethoscope,     roles: null                    },
  { to: '/medicos/aprovacao', label: 'Aprovação',  icon: ClipboardCheck,  roles: ['gestao', 'operacao']  },
  { to: '/empresas',          label: 'Empresas',   icon: Building2,       roles: ['gestao']              },
  { to: '/tomadores',         label: 'Tomadores',  icon: Hospital,        roles: ['operacao', 'gestao', 'financeiro', 'contabil'] },
  { to: '/producao',          label: 'Produção',   icon: ClipboardList,   roles: ['operacao', 'gestao', 'financeiro', 'contabil'] },
  { to: '/notas',       label: 'Notas',        icon: FileText,        roles: null       },
  { to: '/repasses',    label: 'Repasses',     icon: Banknote,        roles: null       },
  { to: '/conciliacao', label: 'Conciliação',  icon: ArrowLeftRight,  roles: null       },
  { to: '/gestao',      label: 'Gestão',       icon: BarChart3,       roles: null       },
  { to: '/usuarios',    label: 'Usuários',     icon: Users,           roles: ['gestao'] },
]

interface SidebarProps {
  mobileOpen: boolean
  onMobileClose: () => void
}

export function Sidebar({ mobileOpen, onMobileClose }: SidebarProps) {
  const { user } = useAuth()
  const userRoles = user?.realm_access?.roles ?? []

  const visibleItems = navItems.filter(
    item => item.roles === null || item.roles.some(r => userRoles.includes(r))
  )

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
        {visibleItems.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            onClick={onMobileClose}
            className={({ isActive }) =>
              [
                'relative flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors',
                isActive
                  ? 'bg-primary-50 text-primary font-bold'
                  : 'text-ds-nav font-medium hover:bg-ds-input hover:text-ds-mid',
              ].join(' ')
            }
          >
            {({ isActive }) => (
              <>
                {isActive && (
                  <span className="absolute left-0 top-[20%] bottom-[20%] w-0.5 bg-primary rounded-r" />
                )}
                <Icon size={17} />
                {label}
              </>
            )}
          </NavLink>
        ))}
      </div>

      {/* Bottom */}
      <div className="px-2 py-3 border-t border-ds-border shrink-0">
        <button className="flex items-center gap-3 px-3 py-2.5 w-full rounded-lg text-sm font-medium text-ds-nav hover:bg-ds-input hover:text-ds-mid transition-colors">
          <Settings size={17} />
          Configurações
        </button>
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
