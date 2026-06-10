import { FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, EyeOff, ShieldCheck, Activity, Building2 } from 'lucide-react'
import { Button } from '@pinsaude/ui'
import { Input }  from '@pinsaude/ui'
import { Alert }  from '@pinsaude/ui'
import { useAuth }            from '../auth/useAuth'
import { KC_RESET_PASSWORD_URL } from '../auth/keycloak'

const features = [
  { icon: ShieldCheck, text: 'Autenticação segura com MFA' },
  { icon: Activity,    text: 'Gestão integrada de produção médica' },
  { icon: Building2,   text: 'Multi-empresa com controle por CNPJ' },
]

export function LoginPage() {
  const { login } = useAuth()
  const navigate  = useNavigate()

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
    <div className="min-h-screen flex">

      {/* ── Painel esquerdo — branding ────────────────────────────────── */}
      <div className="hidden lg:flex lg:w-1/2 bg-primary-700 items-center justify-center p-12">
        <div className="space-y-8 max-w-sm">
          {/* Logo */}
          <img
            src="/logo-pinsaude.png"
            alt="Pin Saúde"
            className="h-16 w-auto brightness-0 invert"
          />

          {/* Tagline */}
          <div className="space-y-3">
            <h1 className="text-4xl font-black text-white leading-tight">
              Gestão de saúde<br />corporativa
            </h1>
            <p className="text-primary-200 text-lg leading-relaxed">
              Emissão de notas, repasses médicos e conciliação financeira em uma única plataforma.
            </p>
          </div>

          {/* Bullets */}
          <ul className="space-y-3">
            {features.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3">
                <div className="flex-shrink-0 h-8 w-8 rounded-lg bg-white/10 flex items-center justify-center">
                  <Icon size={16} className="text-secondary" />
                </div>
                <span className="text-white/80 text-sm">{text}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>

      {/* ── Painel direito — formulário ───────────────────────────────── */}
      <div className="flex-1 flex flex-col items-center justify-center bg-white px-8 py-12">
        {/* Logo mobile (só aparece em telas < lg) */}
        <div className="lg:hidden mb-8">
          <img
            src="/logo-pinsaude.png"
            alt="Pin Saúde"
            className="h-10 w-auto"
          />
        </div>

        <div className="w-full max-w-sm">
          <div className="mb-8">
            <h2 className="text-2xl font-bold text-gray-900">Bem-vindo de volta</h2>
            <p className="text-sm text-gray-500 mt-1">Acesse sua conta para continuar</p>
          </div>

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
      </div>
    </div>
  )
}
