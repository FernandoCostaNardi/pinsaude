import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { AlertTriangle, X } from 'lucide-react'
import { Sidebar } from './Sidebar'
import { Header } from './Header'
import { useAlertasVencimento } from '../hooks/useAlertasVencimento'
import { AlertaVencimentoDocumento } from '../api/empresasApi'

const TIPO_LABEL: Record<string, string> = {
  CONTRATO_SOCIAL:                   'Contrato Social',
  CONSELHO:                          'Conselho',
  ENDERECO_FISCAL:                   'Endereço Fiscal',
  DIRECAO_TECNICA:                   'Direção Técnica',
  DOCUMENTACAO_SOCIO_ADMINISTRADOR:  'Doc. Sócio Administrador',
  NADA_CONSTA_ESTADUAL:              'Nada Consta Estadual',
  CND_FALENCIA:                      'CND Falência',
  CND_FEDERAL:                       'CND Federal',
  CND_FGTS:                          'CND do FGTS',
  CND_MUNICIPAL:                     'CND Municipal',
  CND_TRABALHISTA:                   'CND Trabalhista',
}

function descricaoAlerta(alertas: AlertaVencimentoDocumento[]): string {
  return alertas
    .slice(0, 3)
    .map(a => {
      const tipo = TIPO_LABEL[a.tipo] ?? a.tipo
      const dias = a.diasRestantes
      const prazo = dias < 0
        ? `vencido há ${Math.abs(dias)} dia${Math.abs(dias) !== 1 ? 's' : ''}`
        : dias === 0
        ? 'vence hoje'
        : `vence em ${dias} dia${dias !== 1 ? 's' : ''}`
      return `${a.empresaNome}: ${tipo} (${prazo})`
    })
    .join(' · ')
}

export function Shell() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const [bannerFechado, setBannerFechado] = useState(false)
  const alertas = useAlertasVencimento()
  const mostrarBanner = !bannerFechado && alertas.length > 0

  return (
    <div className="flex h-screen bg-ds-bg overflow-hidden">
      <Sidebar
        mobileOpen={mobileOpen}
        onMobileClose={() => setMobileOpen(false)}
      />

      <div className="flex flex-col flex-1 min-w-0 overflow-hidden">
        <Header onMenuClick={() => setMobileOpen(true)} />

        {mostrarBanner && (
          <div className="flex items-start gap-2.5 px-4 py-2.5 bg-amber-50 border-b border-amber-200 text-amber-800">
            <AlertTriangle size={15} className="shrink-0 mt-0.5 text-amber-600" />
            <div className="flex-1 min-w-0">
              <span className="text-xs font-semibold">
                {alertas.length} documento{alertas.length !== 1 ? 's' : ''} com vencimento próximo ou vencido —{' '}
              </span>
              <span className="text-xs">{descricaoAlerta(alertas)}</span>
              {alertas.length > 3 && (
                <span className="text-xs text-amber-600"> e mais {alertas.length - 3}...</span>
              )}
            </div>
            <button
              onClick={() => setBannerFechado(true)}
              className="p-0.5 rounded hover:bg-amber-100 transition-colors shrink-0"
              title="Fechar alerta (reaparece no próximo login)"
            >
              <X size={14} />
            </button>
          </div>
        )}

        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
