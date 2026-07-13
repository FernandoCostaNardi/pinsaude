import { useEffect, useState } from 'react'
import { empresasApi, AlertaVencimentoDocumento } from '../api/empresasApi'
import { useAuth } from '../auth/useAuth'

export function useAlertasVencimento() {
  const { user } = useAuth()
  const isGestao = user?.realm_access?.roles.includes('gestao') ?? false
  const [alertas, setAlertas] = useState<AlertaVencimentoDocumento[]>([])

  useEffect(() => {
    if (!isGestao) return
    empresasApi.buscarAlertasVencimento()
      .then(setAlertas)
      .catch(() => { /* silencioso — banner não é crítico */ })
  }, [isGestao])

  return alertas
}
