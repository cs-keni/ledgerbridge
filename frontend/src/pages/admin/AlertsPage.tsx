import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchAlerts, fetchAlertStats } from '../../api/alerts'
import { AlertTable } from '../../components/alerts/AlertTable'
import { AlertDetailPanel } from '../../components/alerts/AlertDetailPanel'
import { useAlertStream } from '../../hooks/useAlertStream'
import { useSseStore } from '../../stores/sseStore'
import type { AlertStatus } from '../../types/api'

const STATUS_FILTERS: { label: string; value: AlertStatus | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Open', value: 'OPEN' },
  { label: 'Under Review', value: 'UNDER_REVIEW' },
  { label: 'Resolved', value: 'RESOLVED' },
  { label: 'Dismissed', value: 'DISMISSED' },
]

export default function AlertsPage() {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<AlertStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(0)

  // Invalidate alert list and stats on each SSE event so counts stay live
  const qc = useQueryClient()
  useAlertStream(() => {
    qc.invalidateQueries({ queryKey: ['alerts'] })
    qc.invalidateQueries({ queryKey: ['alert-stats'] })
  })
  const sseStatus = useSseStore((s) => s.status)

  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ['alerts', statusFilter, page],
    queryFn: () =>
      fetchAlerts({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        page,
        size: 50,
      }),
    staleTime: 10_000,
  })

  // System-wide stats (not filtered by current page/tab)
  const { data: stats } = useQuery({
    queryKey: ['alert-stats'],
    queryFn: fetchAlertStats,
    staleTime: 30_000,
  })

  const alerts = data?.content ?? []
  const totalPages = data?.totalPages ?? 1

  const openCount = stats?.open ?? 0
  const criticalCount = stats?.critical ?? 0
  const reviewCount = stats?.underReview ?? 0

  return (
    <div className="p-6 animate-fade-in">
      {/* Page header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-[20px] font-bold text-text">Risk Alerts</h1>
          <p className="text-sm text-muted mt-0.5">
            Real-time fraud detection queue
            {sseStatus === 'connected' && (
              <span className="ml-2 text-[#34d399] font-mono text-[11px]">● live</span>
            )}
            {sseStatus === 'reconnecting' && (
              <span className="ml-2 text-[#d97706] font-mono text-[11px]">● reconnecting</span>
            )}
          </p>
        </div>
        <a
          href="/swagger-ui.html"
          target="_blank"
          rel="noopener noreferrer"
          className="text-sm px-4 py-2 border border-accent rounded text-accent hover:bg-accent/10 transition-colors font-medium"
        >
          Try a Demo Scenario →
        </a>
      </div>

      {/* Stat chips */}
      <div className="grid grid-cols-3 gap-3 mb-6">
        <StatChip label="Open" value={openCount} color="#dc2626" />
        <StatChip label="Critical" value={criticalCount} color="#ea580c" />
        <StatChip label="Under Review" value={reviewCount} color="#d97706" />
      </div>

      {/* Filter tabs */}
      <div className="flex gap-1 mb-4 border-b border-border">
        {STATUS_FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => {
              setStatusFilter(f.value)
              setPage(0)
            }}
            className={`px-4 py-2 text-sm border-b-2 -mb-px transition-colors ${
              statusFilter === f.value
                ? 'border-text text-text'
                : 'border-transparent text-muted hover:text-text'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* Table */}
      <div className="rounded-lg border border-border overflow-hidden">
        {isError ? (
          <div className="flex flex-col items-center justify-center py-12 animate-fade-in">
            <p className="text-sm text-muted mb-3">Failed to load alerts.</p>
            <button
              onClick={() => refetch()}
              className="text-sm text-accent-light hover:underline"
            >
              Retry
            </button>
          </div>
        ) : (
          <AlertTable
            alerts={alerts}
            selectedId={selectedId}
            onSelect={(id) => setSelectedId((prev) => (prev === id ? null : id))}
            loading={isPending}
          />
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <span className="text-sm text-muted">
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-2">
            <PagerBtn onClick={() => setPage((p) => p - 1)} disabled={page === 0}>
              ← Prev
            </PagerBtn>
            <PagerBtn onClick={() => setPage((p) => p + 1)} disabled={page >= totalPages - 1}>
              Next →
            </PagerBtn>
          </div>
        </div>
      )}

      {/* Detail panel */}
      <AlertDetailPanel alertId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  )
}

function StatChip({
  label,
  value,
  color,
}: {
  label: string
  value: number
  color: string
}) {
  return (
    <div className="bg-surface border border-border rounded-lg px-4 py-3">
      <p className="text-[11px] text-muted uppercase tracking-[0.05em] mb-1">{label}</p>
      <p className="font-mono text-2xl font-bold" style={{ color }}>
        {value}
      </p>
    </div>
  )
}

function PagerBtn({
  onClick,
  disabled,
  children,
}: {
  onClick: () => void
  disabled: boolean
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="px-3 py-1.5 text-sm border border-border rounded text-muted hover:text-text disabled:opacity-40 transition-colors"
    >
      {children}
    </button>
  )
}
