import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Shell } from './layouts/Shell'
import { Dashboard } from './pages/Dashboard'
import { UnderConstruction } from './pages/UnderConstruction'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Shell />}>
          <Route path="/"            element={<Dashboard />} />
          <Route path="/medicos"     element={<UnderConstruction title="Médicos"     epic="EPIC-03" />} />
          <Route path="/empresas"    element={<UnderConstruction title="Empresas"    epic="EPIC-02" />} />
          <Route path="/notas"       element={<UnderConstruction title="Notas"       epic="EPIC-05" />} />
          <Route path="/repasses"    element={<UnderConstruction title="Repasses"    epic="EPIC-09" />} />
          <Route path="/conciliacao" element={<UnderConstruction title="Conciliação" epic="EPIC-07" />} />
          <Route path="/gestao"      element={<UnderConstruction title="Gestão"      epic="EPIC-10" />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
