import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard,
  Stethoscope,
  Building2,
  FileText,
  Banknote,
  ArrowLeftRight,
  BarChart3,
  X,
} from 'lucide-react'

const navItems = [
  { to: '/',            label: 'Dashboard',   icon: LayoutDashboard },
  { to: '/medicos',     label: 'Médicos',      icon: Stethoscope     },
  { to: '/empresas',    label: 'Empresas',     icon: Building2       },
  { to: '/notas',       label: 'Notas',        icon: FileText        },
  { to: '/repasses',    label: 'Repasses',     icon: Banknote        },
  { to: '/conciliacao', label: 'Conciliação',  icon: ArrowLeftRight  },
  { to: '/gestao',      label: 'Gestão',       icon: BarChart3       },
]

interface SidebarProps {
  mobileOpen: boolean
  onMobileClose: () => void
}

export function Sidebar({ mobileOpen, onMobileClose }: SidebarProps) {
  const content = (
    <nav className="flex flex-col h-full">
      <div className="flex items-center justify-between px-4 h-16 border-b border-white/10 shrink-0">
        <div className="flex items-center gap-2">
          <span className="text-2xl">🏥</span>
          <span className="text-white font-bold text-lg tracking-tight">Pin Saúde</span>
        </div>
        <button
          onClick={onMobileClose}
          className="md:hidden p-1 rounded-lg text-white/70 hover:text-white hover:bg-white/10"
        >
          <X size={20} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto py-4 px-3 space-y-1">
        {navItems.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            onClick={onMobileClose}
            className={({ isActive }) =>
              [
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-white/20 text-white'
                  : 'text-white/70 hover:bg-white/10 hover:text-white',
              ].join(' ')
            }
          >
            <Icon size={18} />
            {label}
          </NavLink>
        ))}
      </div>

      <div className="px-3 pb-4 shrink-0">
        <div className="rounded-lg bg-white/10 px-3 py-2 text-xs text-white/50">
          v0.1.0 — MVP
        </div>
      </div>
    </nav>
  )

  return (
    <>
      {/* Desktop sidebar */}
      <aside className="hidden md:flex md:flex-col md:w-60 md:shrink-0 bg-primary-700 h-screen sticky top-0">
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
          <aside className="relative flex flex-col w-60 h-full bg-primary-700 shadow-xl">
            {content}
          </aside>
        </div>
      )}
    </>
  )
}
