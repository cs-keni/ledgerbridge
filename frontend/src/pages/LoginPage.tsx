import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'

export default function LoginPage() {
  const navigate = useNavigate()
  const { setTokens, setRefreshToken } = useAuthStore()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [shake, setShake] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      })
      if (!res.ok) {
        setShake(true)
        setError(res.status >= 500 ? 'Unable to connect. Please try again.' : 'Invalid credentials')
        setTimeout(() => setShake(false), 500)
        return
      }
      const data = await res.json()
      setTokens(data.accessToken, data.email, data.role)
      setRefreshToken(data.refreshToken)
      navigate('/alerts', { replace: true })
    } catch {
      setError('Unable to connect. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center">
      <div className="w-full max-w-sm px-4">
        <div className="text-center mb-8">
          <h1 className="text-[20px] font-bold text-text tracking-tight">
            LedgerBridge
          </h1>
          <p className="text-[12px] text-muted mt-1.5 tracking-wide uppercase">
            Real-time transaction risk monitoring
          </p>
        </div>
        <form
          onSubmit={handleSubmit}
          className={`bg-surface border border-border rounded p-8 ${shake ? 'animate-shake' : ''}`}
          aria-label="Sign in"
        >
          <div className="mb-5">
            <label htmlFor="email" className="block text-text text-sm font-medium mb-1.5">
              Email
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full bg-bg border border-border rounded px-3 py-2 text-text text-sm focus:outline-none focus:ring-2 focus:ring-accent-light placeholder-muted"
            />
          </div>
          <div className="mb-6">
            <label htmlFor="password" className="block text-text text-sm font-medium mb-1.5">
              Password
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full bg-bg border border-border rounded px-3 py-2 text-text text-sm focus:outline-none focus:ring-2 focus:ring-accent-light"
            />
          </div>
          {error && (
            <p role="alert" className="text-critical text-sm mb-4">
              {error}
            </p>
          )}
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-accent hover:bg-accent-light disabled:opacity-50 disabled:cursor-not-allowed text-[#0f0e0d] text-sm font-semibold py-2.5 rounded transition-colors focus:outline-none focus:ring-2 focus:ring-accent-light focus:ring-offset-2 focus:ring-offset-surface"
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" aria-hidden="true" />
                Signing in…
              </span>
            ) : (
              'Sign in'
            )}
          </button>
        </form>
        <p className="text-center text-[#999999] text-sm mt-4">
          Don&apos;t have an account?{' '}
          <Link to="/register" className="text-accent-light hover:underline">
            Register
          </Link>
        </p>
      </div>
    </div>
  )
}
