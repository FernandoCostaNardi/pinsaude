import { FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, EyeOff, MapPin } from 'lucide-react'
import { Button }  from '@pinsaude/ui'
import { Input }   from '@pinsaude/ui'
import { Alert }   from '@pinsaude/ui'
import { useAuth } from '../auth/useAuth'
import { KC_RESET_PASSWORD_URL } from '../auth/keycloak'

export function LoginPage() {
  const { login }  = useAuth()
  const navigate   = useNavigate()

  const [email,        setEmail]        = useState('')
  const [password,     setPassword]     = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error,        setError]        = useState<string | null>(null)
  const [loading,      setLoading]      = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!email.trim() || !password) return
    setError(null)
    setLoading(true)
    try {
      await login(email.trim(), password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro inesperado. Tente novamente.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-50 via-white to-secondary-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Brand */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-primary shadow-lg shadow-primary/25 mb-4">
            <MapPin size={28} className="text-white" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900">Pin Saúde</h1>
          <p className="text-sm text-gray-500 mt-1">Gestão de saúde corporativa</p>
        </div>

        {/* Card */}
        <div className="bg-white rounded-2xl shadow-xl shadow-gray-200/60 border border-gray-100 p-8">
          <h2 className="text-lg font-semibold text-gray-900 mb-1">Bem-vindo de volta</h2>
          <p className="text-sm text-gray-500 mb-6">Acesse sua conta para continuar</p>

          {error && (
            <Alert variant="error" onClose={() => setError(null)} className="mb-5">
              {error}
            </Alert>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <Input
              label="E-mail"
              type="email"
              placeholder="seu@email.com.br"
              value={email}
              onChange={e => setEmail(e.target.value)}
              autoComplete="email"
              autoFocus
              required
              disabled={loading}
            />

            <div className="relative">
              <Input
                label="Senha"
                type={showPassword ? 'text' : 'password'}
                placeholder="••••••••"
                value={password}
                onChange={e => setPassword(e.target.value)}
                autoComplete="current-password"
                required
                disabled={loading}
                className="pr-10"
              />
              <button
                type="button"
                tabIndex={-1}
                aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                onClick={() => setShowPassword(v => !v)}
                className="absolute right-3 top-[30px] text-gray-400 hover:text-gray-600 transition-colors"
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>

            <div className="flex justify-end">
              <a
                href={KC_RESET_PASSWORD_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="text-sm text-primary hover:text-primary-700 transition-colors font-medium"
              >
                Esqueceu a senha?
              </a>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="lg"
              loading={loading}
              disabled={!email.trim() || !password}
              className="w-full mt-2"
            >
              {loading ? 'Entrando…' : 'Entrar'}
            </Button>
          </form>
        </div>

        <p className="text-center text-xs text-gray-400 mt-6">
          © {new Date().getFullYear()} Pin Saúde · Todos os direitos reservados
        </p>
      </div>
    </div>
  )
}
