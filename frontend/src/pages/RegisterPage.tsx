import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'

export default function RegisterPage() {
  const navigate = useNavigate()
  const { setTokens, setRefreshToken } = useAuthStore()
  const [form, setForm] = useState({ email: '', password: '', firstName: '', lastName: '' })
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }))

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const res = await fetch('/api/auth/register', {  // POST /api/auth/register
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      })
      if (!res.ok) {
        const data = await res.json().catch(() => ({}))
        setError(data.message ?? 'Registration failed. Please try again.')
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
        <h1 className="text-[20px] font-bold text-text mb-8 text-center tracking-tight">
          LedgerBridge
        </h1>
        <form
          onSubmit={handleSubmit}
          className="bg-surface border border-border rounded p-8"
          aria-label="Create account"
        >
          <div className="grid grid-cols-2 gap-4 mb-5">
            <div>
              <label htmlFor="firstName" className="block text-text text-sm font-medium mb-1.5">
                First name
              </label>
              <input
                id="firstName"
                type="text"
                autoComplete="given-name"
                required
                value={form.firstName}
                onChange={set('firstName')}
                className="w-full bg-bg border border-border rounded px-3 py-2 text-text text-sm focus:outline-none focus:ring-2 focus:ring-accent-light"
              />
            </div>
            <div>
              <label htmlFor="lastName" className="block text-text text-sm font-medium mb-1.5">
                Last name
              </label>
              <input
                id="lastName"
                type="text"
                autoComplete="family-name"
                required
                value={form.lastName}
                onChange={set('lastName')}
                className="w-full bg-bg border border-border rounded px-3 py-2 text-text text-sm focus:outline-none focus:ring-2 focus:ring-accent-light"
              />
            </div>
          </div>
          <div className="mb-5">
            <label htmlFor="email" className="block text-text text-sm font-medium mb-1.5">
              Email
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={form.email}
              onChange={set('email')}
              className="w-full bg-bg border border-border rounded px-3 py-2 text-text text-sm focus:outline-none focus:ring-2 focus:ring-accent-light"
            />
          </div>
          <div className="mb-6">
            <label htmlFor="password" className="block text-text text-sm font-medium mb-1.5">
              Password
            </label>
            <input
              id="password"
              type="password"
              autoComplete="new-password"
              required
              value={form.password}
              onChange={set('password')}
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
            className="w-full bg-accent hover:bg-accent-light disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium py-2.5 rounded transition-colors focus:outline-none focus:ring-2 focus:ring-accent-light focus:ring-offset-2 focus:ring-offset-surface"
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" aria-hidden="true" />
                Creating account…
              </span>
            ) : (
              'Create account'
            )}
          </button>
        </form>
        <p className="text-center text-[#999999] text-sm mt-4">
          Already have an account?{' '}
          <Link to="/login" className="text-accent-light hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
