import { Card, CardBody, CardHeader, Badge } from '@pinsaude/ui'
import { Users, FileText, Banknote, TrendingUp } from 'lucide-react'

const stats = [
  { label: 'Médicos Ativos',   value: '—',  icon: Users,     color: 'text-primary',    bg: 'bg-primary-50'  },
  { label: 'Notas Emitidas',   value: '—',  icon: FileText,  color: 'text-brand-blue', bg: 'bg-blue-50'     },
  { label: 'Repasses Pendentes',value: '—', icon: Banknote,  color: 'text-yellow-600', bg: 'bg-yellow-50'   },
  { label: 'Faturamento/mês',  value: '—',  icon: TrendingUp,color: 'text-green-600',  bg: 'bg-green-50'    },
]

export function Dashboard() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">Dashboard</h1>
        <p className="text-sm text-gray-500 mt-0.5">Visão geral da plataforma</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {stats.map(({ label, value, icon: Icon, color, bg }) => (
          <Card key={label}>
            <CardBody className="flex items-center gap-4">
              <div className={['p-3 rounded-xl', bg].join(' ')}>
                <Icon className={color} size={22} />
              </div>
              <div>
                <p className="text-2xl font-bold text-gray-900">{value}</p>
                <p className="text-xs text-gray-500 mt-0.5">{label}</p>
              </div>
            </CardBody>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <span className="text-sm font-semibold text-gray-800">Últimas Notas Fiscais</span>
            <Badge variant="info">Em breve</Badge>
          </CardHeader>
          <CardBody>
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <span className="text-4xl mb-2">🚧</span>
              <p className="text-sm font-medium text-gray-600">Em construção</p>
              <p className="text-xs text-gray-400 mt-1">Disponível a partir do EPIC-05</p>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader>
            <span className="text-sm font-semibold text-gray-800">Repasses Pendentes</span>
            <Badge variant="warning">Em breve</Badge>
          </CardHeader>
          <CardBody>
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <span className="text-4xl mb-2">🚧</span>
              <p className="text-sm font-medium text-gray-600">Em construção</p>
              <p className="text-xs text-gray-400 mt-1">Disponível a partir do EPIC-09</p>
            </div>
          </CardBody>
        </Card>
      </div>
    </div>
  )
}
