import { useQuery } from '@tanstack/react-query'
import { fetchAlerts } from '../../api/alerts'
import { fetchAccounts } from '../../api/accounts'
import { SeverityBadge } from '../../components/alerts/SeverityBadge'
import { ScoreChip } from '../../components/alerts/ScoreChip'
import { Link } from 'react-router-dom'

function formatRelativeTime(iso: string) {
  const ms = Date.now() - new Date(iso).getTime()
  if (ms < 60_000) return 'just now'
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m ago`
  if (ms < 86_400_000) return `${Math.floor(ms / 3_600_000)}h ago`
  return `${Math.floor(ms / 86_400_000)}d ago`
}

const AMOUNT_FMT = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export default function DashboardPage() {
  const { data: alertsData, isPending: alertsLoading } = useQuery({
    queryKey: ['alerts', 'ALL', 0],
    queryFn: () => fetchAlerts({ size: 50 }),
    staleTime: 30_000,
  })

  const { data: accountsData, isPending: accountsLoading } = useQuery({
    queryKey: ['accounts'],
    queryFn: () => fetchAccounts(),
    staleTime: 60_000,
  })

  const alerts = alertsData?.content ?? []
  const accounts = accountsData ?? []

  const openAlerts = alerts.filter((a) => a.status === 'OPEN')
  const criticalAlerts = alerts.filter((a) => a.severity === 'CRITICAL')
  const avgScore =
    alerts.length > 0
      ? (alerts.reduce((s, a) => s + a.riskScore, 0) / alerts.length).toFixed(2)
      : '—'

  const totalBalance = accounts.reduce(
    (sum, acc) => sum + parseFloat(acc.balance || '0'),
    0,
  )

  function scoreColor(s: number | string): string {
    const n = typeof s === 'string' ? parseFloat(s) : s
    if (isNaN(n)) return '#888888'
    if (n < 0.3) return '#34d399'
    if (n < 0.6) return '#d97706'
    return '#dc2626'
  }

  return (
    <div className="p-6 animate-fade-in">
      <div className="mb-6">
        <h1 className="text-[20px] font-bold text-text">Dashboard</h1>
        <p className="text-sm text-muted mt-0.5">System overview</p>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-8">
        <KpiCard label="Open Alerts" value={openAlerts.length} loading={alertsLoading} color="#dc2626" />
        <KpiCard label="Critical" value={criticalAlerts.length} loading={alertsLoading} color="#ea580c" />
        <KpiCard label="Avg Risk Score" value={avgScore} loading={alertsLoading} color={scoreColor(avgScore)} />
        <KpiCard
          label="Total Balance"
          value={accountsLoading ? '—' : AMOUNT_FMT.format(totalBalance)}
          loading={accountsLoading}
          color="#34d399"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent alerts */}
        <section className="bg-surface border border-border rounded-lg overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 border-b border-border">
            <h2 className="text-sm font-semibold text-text">Recent Alerts</h2>
            <Link to="/alerts" className="text-[12px] text-muted hover:text-text transition-colors">
              View all →
            </Link>
          </div>
          {alertsLoading ? (
            <div className="p-4 space-y-3">
              {[0, 1, 2].map((i) => (
                <div
                  key={i}
                  className="h-8 rounded animate-shimmer bg-gradient-to-r from-bg via-border to-bg bg-[length:200%_100%]"
                  style={{ animationDelay: `${i * 80}ms` }}
                />
              ))}
            </div>
          ) : alerts.length === 0 ? (
            <p className="text-center text-muted text-sm py-8">No alerts yet.</p>
          ) : (
            <ul>
              {alerts.slice(0, 8).map((alert) => (
                <li
                  key={alert.id}
                  className="flex items-center gap-3 px-4 py-2.5 border-b border-border last:border-0"
                >
                  <ScoreChip score={alert.riskScore} severity={alert.severity} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-text truncate">{alert.alertType.replace(/_/g, ' ')}</p>
                    <p className="font-mono text-[11px] text-muted">{formatRelativeTime(alert.createdAt)}</p>
                  </div>
                  <SeverityBadge severity={alert.severity} />
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Accounts summary */}
        <section className="bg-surface border border-border rounded-lg overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 border-b border-border">
            <h2 className="text-sm font-semibold text-text">Accounts</h2>
            <Link to="/accounts" className="text-[12px] text-muted hover:text-text transition-colors">
              View all →
            </Link>
          </div>
          {accountsLoading ? (
            <div className="p-4 space-y-3">
              {[0, 1, 2].map((i) => (
                <div
                  key={i}
                  className="h-8 rounded animate-shimmer bg-gradient-to-r from-bg via-border to-bg bg-[length:200%_100%]"
                  style={{ animationDelay: `${i * 80}ms` }}
                />
              ))}
            </div>
          ) : accounts.length === 0 ? (
            <div className="text-center py-8">
              <p className="text-muted text-sm mb-2">No accounts visible.</p>
              <p className="text-[12px] text-muted">
                Log in as an admin to see all accounts, or trigger a demo scenario to create one.
              </p>
            </div>
          ) : (
            <ul>
              {accounts.slice(0, 8).map((acc) => (
                <li
                  key={acc.id}
                  className="flex items-center justify-between px-4 py-2.5 border-b border-border last:border-0"
                >
                  <div>
                    <p className="font-mono text-[12px] text-text">{acc.accountNumber}</p>
                    <p className="text-[11px] text-muted">{acc.type} · {acc.status}</p>
                  </div>
                  <span className="font-mono text-sm text-text">
                    {AMOUNT_FMT.format(parseFloat(acc.balance))}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}

function KpiCard({
  label,
  value,
  loading,
  color,
}: {
  label: string
  value: string | number
  loading: boolean
  color: string
}) {
  return (
    <div className="bg-surface border border-border rounded-lg px-4 py-3">
      <p className="text-[11px] text-muted uppercase tracking-[0.05em] mb-1">{label}</p>
      {loading ? (
        <div className="h-8 w-16 rounded animate-shimmer bg-gradient-to-r from-bg via-border to-bg bg-[length:200%_100%]" />
      ) : (
        <p className="font-mono text-2xl font-bold" style={{ color }}>
          {value}
        </p>
      )}
    </div>
  )
}
