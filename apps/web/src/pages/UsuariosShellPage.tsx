import { useState } from 'react'
import { Users, Shield } from 'lucide-react'
import { UsersPage } from './UsersPage'
import { PerfisTab } from '../components/PerfisTab'

type Tab = 'usuarios' | 'perfis'

const TABS: { key: Tab; label: string; Icon: React.ElementType }[] = [
  { key: 'usuarios', label: 'Usuários', Icon: Users },
  { key: 'perfis',   label: 'Perfis',   Icon: Shield },
]

export function UsuariosShellPage() {
  const [tab, setTab] = useState<Tab>('usuarios')

  return (
    <div className="flex flex-col gap-5">
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
        <div className="flex border-b border-ds-border overflow-x-auto">
          {TABS.map(({ key, label, Icon }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={[
                'flex items-center gap-2 px-5 py-3 text-sm font-semibold border-b-2 transition-colors whitespace-nowrap',
                tab === key
                  ? 'border-primary text-primary bg-primary-50/30'
                  : 'border-transparent text-ds-mid hover:text-primary hover:bg-primary-50/20',
              ].join(' ')}
            >
              <Icon size={15} />
              {label}
            </button>
          ))}
        </div>
      </div>

      {tab === 'usuarios' ? <UsersPage /> : <PerfisTab />}
    </div>
  )
}
