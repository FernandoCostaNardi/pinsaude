import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider }      from './auth/AuthContext'
import { ProtectedRoute }    from './components/ProtectedRoute'
import { Shell }             from './layouts/Shell'
import { LoginPage }         from './pages/LoginPage'
import { Dashboard }         from './pages/Dashboard'
import { UsersPage }         from './pages/UsersPage'
import { EmpresasPage }      from './pages/EmpresasPage'
import { TomadoresPage }     from './pages/TomadoresPage'
import { MedicosPage }              from './pages/MedicosPage'
import { MedicoPerfilPage }         from './pages/MedicoPerfilPage'
import { AprovacaoOnboardingPage }  from './pages/AprovacaoOnboardingPage'
import { ProducoesPage }    from './pages/ProducoesPage'
import { ProducaoNovaPage } from './pages/ProducaoNovaPage'
import { ConfiguracaoFiscalPage }   from './pages/ConfiguracaoFiscalPage'
import { NotasPage }               from './pages/NotasPage'
import { NfseEmissaoPage }         from './pages/NfseEmissaoPage'
import { LotePage }                from './pages/LotePage'
import { UnderConstruction }        from './pages/UnderConstruction'
import { DashboardMedicoPage }      from './pages/DashboardMedicoPage'
import { MinhasNotasPage }           from './pages/MinhasNotasPage'
import { ExtratoPage }               from './pages/ExtratoPage'
import { PortalProducaoNovaPage }    from './pages/PortalProducaoNovaPage'
import { ConciliacaoUploadPage }      from './pages/ConciliacaoUploadPage'
import { ConciliacaoAssistidaPage }  from './pages/ConciliacaoAssistidaPage'
import { PosicaoCaixaPage }          from './pages/PosicaoCaixaPage'
import { LedgerExtratoPage }         from './pages/LedgerExtratoPage'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            element={
              <ProtectedRoute>
                <Shell />
              </ProtectedRoute>
            }
          >
            <Route path="/"            element={<Dashboard />} />
            <Route path="/usuarios"    element={<UsersPage />} />
            <Route path="/medicos"             element={<MedicosPage />} />
            <Route path="/medicos/aprovacao"  element={<AprovacaoOnboardingPage />} />
            <Route path="/medicos/:id"        element={<MedicoPerfilPage />} />
            <Route path="/empresas"    element={<EmpresasPage />} />
            <Route path="/tomadores"   element={<TomadoresPage />} />
            <Route path="/producao"          element={<ProducoesPage />} />
            <Route path="/producao/nova"     element={<ProducaoNovaPage />} />
            <Route path="/parametros-fiscais" element={<Navigate replace to="/fiscal/config" />} />
            <Route path="/fiscal/config"      element={<ConfiguracaoFiscalPage />} />
            <Route path="/notas"                      element={<NotasPage />} />
            <Route path="/notas/emitir/:producaoId"   element={<NfseEmissaoPage />} />
            <Route path="/notas/lote"                 element={<LotePage />} />
            <Route path="/portal/dashboard" element={<DashboardMedicoPage />} />
            <Route path="/portal/notas"     element={<MinhasNotasPage />} />
            <Route path="/portal/extrato"        element={<ExtratoPage />} />
            <Route path="/portal/producao/nova"  element={<PortalProducaoNovaPage />} />
            <Route path="/repasses"           element={<UnderConstruction title="Repasses" epic="EPIC-09" />} />
            <Route path="/conciliacao"           element={<ConciliacaoAssistidaPage />} />
            <Route path="/conciliacao/assistida" element={<ConciliacaoAssistidaPage />} />
            <Route path="/conciliacao/upload"    element={<ConciliacaoUploadPage />} />
            <Route path="/conciliacao/caixa"     element={<PosicaoCaixaPage />} />
            <Route path="/financeiro/ledger"     element={<LedgerExtratoPage />} />
            <Route path="/gestao"      element={<UnderConstruction title="Gestão"      epic="EPIC-10" />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
