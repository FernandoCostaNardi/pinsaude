import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider }      from './auth/AuthContext'
import { ProtectedRoute }    from './components/ProtectedRoute'
import { Shell }             from './layouts/Shell'
import { LoginPage }         from './pages/LoginPage'
import { Dashboard }         from './pages/Dashboard'
import { UnderConstruction } from './pages/UnderConstruction'

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
            <Route path="/medicos"     element={<UnderConstruction title="Médicos"     epic="EPIC-03" />} />
            <Route path="/empresas"    element={<UnderConstruction title="Empresas"    epic="EPIC-02" />} />
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
