import { Menu, Bell, ChevronDown, LogOut } from 'lucide-react'
import { useState } from 'react'

interface HeaderProps {
  onMenuClick: () => void
}

export function Header({ onMenuClick }: HeaderProps) {
  const [userMenuOpen, setUserMenuOpen] = useState(false)

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
            JS
          </div>
          <span className="hidden sm:block text-sm font-medium text-gray-700">Dr. João Silva</span>
          <ChevronDown size={16} className="text-gray-400" />
        </button>

        {userMenuOpen && (
          <>
            <div
              className="fixed inset-0 z-10"
              onClick={() => setUserMenuOpen(false)}
              aria-hidden="true"
            />
            <div className="absolute right-0 top-full mt-1 w-48 bg-white rounded-xl border border-gray-200 shadow-lg z-20 py-1">
              <div className="px-4 py-2 border-b border-gray-100">
                <p className="text-sm font-medium text-gray-900">Dr. João Silva</p>
                <p className="text-xs text-gray-500">joao.silva@email.com</p>
              </div>
              <button
                className="w-full flex items-center gap-2 px-4 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors"
                onClick={() => setUserMenuOpen(false)}
              >
                <LogOut size={16} />
                Sair
              </button>
            </div>
          </>
        )}
      </div>
    </header>
  )
}
