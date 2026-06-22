import { BrowserRouter, Routes, Route } from 'react-router-dom'
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
import { ParametrosFiscaisPage }   from './pages/ParametrosFiscaisPage'
import { ConfiguracaoFiscalPage }   from './pages/ConfiguracaoFiscalPage'
import { UnderConstruction }        from './pages/UnderConstruction'

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
            <Route path="/parametros-fiscais" element={<ParametrosFiscaisPage />} />
            <Route path="/fiscal/config"      element={<ConfiguracaoFiscalPage />} />
            <Route path="/notas"       element={<UnderConstruction title="Notas"       epic="EPIC-05" />} />
            <Route path="/repasses"    element={<UnderConstruction title="Repasses"    epic="EPIC-09" />} />
            <Route path="/conciliacao" element={<UnderConstruction title="Conciliação" epic="EPIC-07" />} />
            <Route path="/gestao"      element={<UnderConstruction title="Gestão"      epic="EPIC-10" />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
