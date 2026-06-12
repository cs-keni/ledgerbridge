import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchAuditLog } from '../../api/audit'

export default function AuditLogPage() {
  const [page, setPage] = useState(0)

  const { data, isPending } = useQuery({
    queryKey: ['audit-log', page],
    queryFn: () => fetchAuditLog({ page, size: 25 }),
    staleTime: 15_000,
  })

  const entries = data?.content ?? []
  const totalPages = data?.totalPages ?? 1

  return (
    <div className="p-6 animate-fade-in">
      <div className="mb-6">
        <h1 className="text-[20px] font-bold text-text">Audit Log</h1>
        <p className="text-sm text-muted mt-0.5">Immutable record of all system events</p>
      </div>

      <div className="rounded-lg border border-border overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="border-b border-border">
              {['Time', 'Entity', 'Action', 'Outcome', 'User / IP'].map((h) => (
                <th
                  key={h}
                  scope="col"
                  className="px-4 py-2 text-left text-[11px] font-medium uppercase tracking-[0.05em] text-muted"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isPending &&
              [0, 1, 2, 3, 4].map((i) => (
                <tr key={i} className="border-b border-border h-[40px]">
                  {[0, 1, 2, 3, 4].map((j) => (
                    <td key={j} className="px-4 py-2">
                      <div
                        className="h-2.5 rounded animate-shimmer bg-gradient-to-r from-surface via-border to-surface bg-[length:200%_100%]"
                        style={{ animationDelay: `${(i * 5 + j) * 40}ms`, width: `${50 + j * 10}%` }}
                      />
                    </td>
                  ))}
                </tr>
              ))}

            {!isPending && entries.length === 0 && (
              <tr>
                <td colSpan={5} className="text-center py-16 text-muted text-sm">
                  No audit events yet. Trigger actions in the Risk Alerts queue to see entries.
                </td>
              </tr>
            )}

            {entries.map((entry) => (
              <tr
                key={entry.id}
                className="border-b border-border h-[40px] hover:bg-surface transition-colors"
              >
                <td className="px-4 font-mono text-[12px] text-muted whitespace-nowrap">
                  {new Date(entry.occurredAt).toLocaleString()}
                </td>
                <td className="px-4 text-sm text-text">
                  <span className="font-semibold">{entry.entityType}</span>
                  {entry.entityId && (
                    <span className="ml-1.5 font-mono text-[11px] text-muted">
                      {entry.entityId.slice(0, 8)}…
                    </span>
                  )}
                </td>
                <td className="px-4 text-sm text-text">{entry.action}</td>
                <td className="px-4">
                  <OutcomeBadge outcome={entry.outcome} />
                </td>
                <td className="px-4 font-mono text-[12px] text-muted">
                  {entry.userId ? entry.userId.slice(0, 8) + '…' : '—'}
                  {entry.ipAddress && (
                    <span className="ml-2 opacity-60">{entry.ipAddress}</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <span className="text-sm text-muted">
            {data && `${data.totalElements.toLocaleString()} total events · `}
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
    </div>
  )
}

function OutcomeBadge({ outcome }: { outcome: string | null }) {
  if (!outcome) return <span className="text-muted text-sm">—</span>
  const ok = outcome === 'SUCCESS' || outcome === 'OK'
  return (
    <span
      className="inline-flex items-center gap-1 text-[11px] font-medium"
      style={{ color: ok ? '#34d399' : '#dc2626' }}
    >
      <span className="w-1.5 h-1.5 rounded-full" style={{ background: ok ? '#34d399' : '#dc2626' }} />
      {outcome}
    </span>
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
