import { useMemo, useState } from 'react'
import type { RiskAlertResponse } from '../../types/api'
import { SeverityBadge } from './SeverityBadge'
import { ScoreChip } from './ScoreChip'

type SortKey = 'riskScore' | 'severity' | 'createdAt' | 'alertType' | 'status'
type SortDir = 'asc' | 'desc'

const SEVERITY_ORDER = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }
const TYPE_LABEL: Record<string, string> = {
  AMOUNT_ANOMALY: 'Amount Anomaly',
  VELOCITY_ANOMALY: 'Velocity Spike',
  BEHAVIORAL_ANOMALY: 'Behavioral',
  GRAPH_PATTERN: 'Graph Pattern',
}
const STATUS_LABEL: Record<string, string> = {
  OPEN: 'Open',
  UNDER_REVIEW: 'Under Review',
  DISMISSED: 'Dismissed',
  ESCALATED: 'Escalated',
  RESOLVED: 'Resolved',
}

function formatRelativeTime(iso: string) {
  const ms = Date.now() - new Date(iso).getTime()
  if (ms < 60_000) return 'just now'
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m ago`
  if (ms < 86_400_000) return `${Math.floor(ms / 3_600_000)}h ago`
  return `${Math.floor(ms / 86_400_000)}d ago`
}

interface Props {
  alerts: RiskAlertResponse[]
  selectedId: string | null
  onSelect: (id: string) => void
  loading?: boolean
}

export function AlertTable({ alerts, selectedId, onSelect, loading }: Props) {
  const [sort, setSort] = useState<{ key: SortKey; dir: SortDir }>({
    key: 'createdAt',
    dir: 'desc',
  })

  const sorted = useMemo(() => {
    return [...alerts].sort((a, b) => {
      let cmp = 0
      if (sort.key === 'riskScore') cmp = a.riskScore - b.riskScore
      else if (sort.key === 'severity')
        cmp =
          (SEVERITY_ORDER[a.severity] ?? 0) - (SEVERITY_ORDER[b.severity] ?? 0)
      else if (sort.key === 'createdAt')
        cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      else if (sort.key === 'alertType') cmp = a.alertType.localeCompare(b.alertType)
      else if (sort.key === 'status') cmp = a.status.localeCompare(b.status)
      return sort.dir === 'asc' ? cmp : -cmp
    })
  }, [alerts, sort])

  const toggle = (key: SortKey) =>
    setSort((prev) =>
      prev.key === key ? { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'desc' },
    )

  const Th = ({ col, children }: { col: SortKey; children: React.ReactNode }) => (
    <th
      scope="col"
      onClick={() => toggle(col)}
      className="px-3 py-2 text-left text-[11px] font-medium uppercase tracking-[0.05em] text-muted select-none cursor-pointer hover:text-text transition-colors"
    >
      {children}
      {sort.key === col && (
        <span className="ml-1 opacity-60">{sort.dir === 'asc' ? '↑' : '↓'}</span>
      )}
    </th>
  )

  if (loading) {
    return (
      <div role="status" aria-label="Loading alerts">
        {[0, 1, 2].map((i) => (
          <div
            key={i}
            className="h-[44px] border-b border-border"
            style={{ animationDelay: `${i * 120}ms` }}
          >
            <div className="h-3 w-full rounded animate-shimmer bg-gradient-to-r from-surface via-border to-surface bg-[length:200%_100%] m-3" />
          </div>
        ))}
      </div>
    )
  }

  if (alerts.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 animate-fade-in">
        <p className="text-muted text-sm mb-3">No risk alerts yet.</p>
        <a
          href="/swagger-ui.html"
          target="_blank"
          rel="noopener noreferrer"
          className="text-sm text-accent-light hover:underline"
        >
          Trigger a fraud scenario in Swagger UI →
        </a>
      </div>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full" role="table">
        <thead>
          <tr className="border-b border-border">
            <Th col="riskScore">Score</Th>
            <th scope="col" className="px-3 py-2 text-left text-[11px] font-medium uppercase tracking-[0.05em] text-muted">
              Alert #
            </th>
            <Th col="alertType">Type</Th>
            <Th col="severity">Severity</Th>
            <Th col="status">Status</Th>
            <Th col="createdAt">Time</Th>
            <th scope="col" className="px-3 py-2 w-8" />
          </tr>
        </thead>
        <tbody>
          {sorted.map((alert, idx) => {
            const isNew = idx < 3 && alert.createdAt > new Date(Date.now() - 5000).toISOString()
            const isSelected = alert.id === selectedId
            return (
              <tr
                key={alert.id}
                role="row"
                tabIndex={0}
                onClick={() => onSelect(alert.id)}
                onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && onSelect(alert.id)}
                className={`h-[44px] border-b border-border cursor-pointer transition-colors focus:outline-none focus:bg-surface ${
                  isSelected ? 'bg-surface' : 'hover:bg-surface'
                } ${isNew ? 'animate-alert-arrive' : ''}`}
                aria-selected={isSelected}
              >
                <td className="px-3">
                  <div className="flex items-center gap-2">
                    <ScoreChip score={alert.riskScore / 100} severity={alert.severity} />
                    <span className="font-mono text-[12px] text-text">
                      {alert.riskScore.toFixed(2)}
                    </span>
                  </div>
                </td>
                <td className="px-3 font-mono text-[12px] text-muted">{alert.alertNumber}</td>
                <td className="px-3 text-sm text-text">
                  {TYPE_LABEL[alert.alertType] ?? alert.alertType}
                </td>
                <td className="px-3">
                  <SeverityBadge severity={alert.severity} />
                </td>
                <td className="px-3 text-sm text-muted">
                  {STATUS_LABEL[alert.status] ?? alert.status}
                </td>
                <td className="px-3 font-mono text-[12px] text-muted">
                  {formatRelativeTime(alert.createdAt)}
                </td>
                <td className="px-3 text-muted text-sm">›</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
