import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { decodeJwt, type JwtPayload, KC_CLIENT, KC_TOKEN_ENDPOINT } from './keycloak'

const STORAGE_KEY = 'pinsaude_tokens'

interface StoredTokens {
  accessToken:  string
  refreshToken: string
  expiresAt:    number
}

export interface AuthUser extends JwtPayload {
  email: string
}

interface AuthContextValue {
  user:            AuthUser | null
  isAuthenticated: boolean
  isLoading:       boolean
  login:           (email: string, password: string) => Promise<void>
  logout:          () => void
}

const AuthContext = createContext<AuthContextValue>({
  user:            null,
  isAuthenticated: false,
  isLoading:       true,
  login:           async () => {},
  logout:          () => {},
})

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user,      setUser]      = useState<AuthUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const timerRef   = useRef<ReturnType<typeof setTimeout> | null>(null)
  // Use a ref to break the applyTokens ↔ performRefresh circular dependency
  const refreshRef = useRef<(token: string) => void>(() => {})

  const clearTimer = () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }

  const logout = useCallback(() => {
    clearTimer()
    setUser(null)
    sessionStorage.removeItem(STORAGE_KEY)
  }, [])

  const applyTokens = useCallback((accessToken: string, refreshToken: string, expiresIn: number) => {
    const payload  = decodeJwt(accessToken)
    const authUser: AuthUser = { ...payload, email: payload.email ?? '' }
    setUser(authUser)
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
      accessToken,
      refreshToken,
      expiresAt: Date.now() + expiresIn * 1_000,
    } satisfies StoredTokens))
    clearTimer()
    const ms = Math.max((expiresIn - 60) * 1_000, 5_000)
    timerRef.current = setTimeout(() => refreshRef.current(refreshToken), ms)
  }, [])

  const performRefresh = useCallback(async (refreshToken: string) => {
    try {
      const res = await fetch(KC_TOKEN_ENDPOINT, {
        method:  'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body:    new URLSearchParams({
          grant_type:    'refresh_token',
          client_id:     KC_CLIENT,
          refresh_token: refreshToken,
        }),
      })
      if (!res.ok) { logout(); return }
      const data = await res.json()
      applyTokens(data.access_token, data.refresh_token, data.expires_in)
    } catch { logout() }
  }, [applyTokens, logout])

  useEffect(() => { refreshRef.current = performRefresh }, [performRefresh])

  // Restore session on mount
  useEffect(() => {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (raw) {
      try {
        const stored: StoredTokens = JSON.parse(raw)
        if (stored.expiresAt > Date.now() + 5_000) {
          const payload = decodeJwt(stored.accessToken)
          setUser({ ...payload, email: payload.email ?? '' })
          const ms = Math.max(stored.expiresAt - Date.now() - 60_000, 5_000)
          timerRef.current = setTimeout(() => refreshRef.current(stored.refreshToken), ms)
        } else {
          sessionStorage.removeItem(STORAGE_KEY)
        }
      } catch { sessionStorage.removeItem(STORAGE_KEY) }
    }
    setIsLoading(false)
    return clearTimer
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const res = await fetch(KC_TOKEN_ENDPOINT, {
      method:  'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body:    new URLSearchParams({
        grant_type: 'password',
        client_id:  KC_CLIENT,
        username:   email,
        password,
        scope:      'openid',
      }),
    })
    const data = await res.json()
    if (!res.ok) {
      const desc = String(data?.error_description ?? '').toLowerCase()
      if (desc.includes('not fully set up') || desc.includes('setup') || desc.includes('actions')) {
        throw new Error('Sua conta requer configuração de MFA. Entre em contato com o administrador.')
      }
      if (data?.error === 'invalid_grant') {
        throw new Error('E-mail ou senha incorretos. Verifique os dados e tente novamente.')
      }
      throw new Error(String(data?.error_description || data?.error || `Erro ${res.status}`))
    }
    applyTokens(data.access_token, data.refresh_token, data.expires_in)
  }, [applyTokens])

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
