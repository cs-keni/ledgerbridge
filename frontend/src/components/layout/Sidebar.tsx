import { NavLink, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchAlertStats } from '../../api/alerts'
import { useAuthStore } from '../../stores/authStore'
import { useSseStore } from '../../stores/sseStore'

const NAV_ALL = [
  { to: '/alerts', label: 'Risk Alerts', hasSseBadge: true },
  { to: '/audit', label: 'Audit Log' },
  { to: '/transfer', label: 'Transfer' },
  { to: '/accounts', label: 'Accounts' },
  { to: '/dashboard', label: 'Dashboard', adminOnly: true },
]

export function Sidebar() {
  const { email, role, clearAuth } = useAuthStore()
  const sseStatus = useSseStore((s) => s.status)
  const NAV = NAV_ALL.filter((item) => !item.adminOnly || role === 'ADMIN')
  const navigate = useNavigate()
  const { data: stats } = useQuery({
    queryKey: ['alert-stats'],
    queryFn: fetchAlertStats,
    staleTime: 30_000,
  })
  const openCount = stats?.open ?? 0

  const handleLogout = async () => {
    const refreshToken = localStorage.getItem('lb_refresh_token')
    if (refreshToken) {
      fetch('/api/auth/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      }).catch(() => { /* best-effort */ })
    }
    clearAuth()
    navigate('/login', { replace: true })
  }

  return (
    <aside
      className="flex-shrink-0 flex flex-col border-r border-border"
      style={{ width: '160px', background: '#161616' }}
      role="navigation"
      aria-label="Main navigation"
    >
      <div className="px-4 py-5 border-b border-border">
        <span className="text-text font-bold text-sm tracking-tight">LedgerBridge</span>
      </div>

      <nav className="flex-1 p-2 mt-2">
        {NAV.map(({ to, label, hasSseBadge }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-2 px-3 py-2.5 min-h-[40px] rounded text-[11px] font-medium uppercase tracking-[0.05em] mb-0.5 transition-colors ${
                isActive
                  ? 'bg-accent/10 text-accent'
                  : 'text-muted hover:text-text hover:bg-surface'
              }`
            }
          >
            {hasSseBadge && (
              <span
                title={
                  sseStatus === 'connected'
                    ? 'Live feed connected'
                    : sseStatus === 'reconnecting'
                    ? 'Reconnecting to live feed…'
                    : 'Live feed disconnected'
                }
                className={`w-2 h-2 rounded-full flex-shrink-0 ${
                  sseStatus === 'connected'
                    ? 'bg-positive animate-pulse'
                    : sseStatus === 'reconnecting'
                    ? 'bg-medium animate-pulse'
                    : 'bg-muted'
                }`}
              />
            )}
            <span className="flex-1 truncate">{label}</span>
            {hasSseBadge && openCount > 0 && (
              <span className="ml-auto text-[10px] font-bold px-1.5 py-0.5 rounded bg-accent/20 text-accent-light leading-none flex-shrink-0">
                {openCount > 99 ? '99+' : openCount}
              </span>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="p-4 border-t border-border">
        <p className="text-[11px] text-[#999999] mb-2 truncate" title={email ?? ''}>
          {email}
        </p>
        <button
          onClick={handleLogout}
          className="text-[11px] text-muted hover:text-text transition-colors py-1.5 -ml-1 px-1"
        >
          Logout
        </button>
      </div>
    </aside>
  )
}
