import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchAlertDetail, reviewAlert } from '../../api/alerts'
import { RiskGauge } from '../risk/RiskGauge'
import { SeverityBadge } from './SeverityBadge'
import type { AlertStatus } from '../../types/api'

const AMOUNT_FMT = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

const TX_TYPE_LABEL: Record<string, string> = {
  DEPOSIT: 'Deposit',
  WITHDRAWAL: 'Withdrawal',
  TRANSFER_DEBIT: 'Transfer (Out)',
  TRANSFER_CREDIT: 'Transfer (In)',
}

interface Props {
  alertId: string | null
  onClose: () => void
}

export function AlertDetailPanel({ alertId, onClose }: Props) {
  const qc = useQueryClient()
  const [notes, setNotes] = useState('')

  const { data: alert, isPending } = useQuery({
    queryKey: ['alert-detail', alertId],
    queryFn: () => fetchAlertDetail(alertId!),
    enabled: !!alertId,
    staleTime: 15_000,
  })

  const { mutate: review, isPending: reviewing } = useMutation({
    mutationFn: ({ status, note }: { status: AlertStatus; note?: string }) =>
      reviewAlert(alertId!, { status, notes: note }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['alerts'] })
      qc.invalidateQueries({ queryKey: ['alert-detail', alertId] })
      setNotes('')
    },
  })

  const isOpen = !!alertId

  return (
    <>
      {/* Backdrop */}
      <div
        className={`fixed inset-0 bg-black/40 z-30 transition-opacity duration-300 ${
          isOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'
        }`}
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Panel */}
      <aside
        role="complementary"
        aria-label="Alert detail"
        aria-hidden={!isOpen}
        className="fixed top-0 right-0 h-full w-[380px] bg-bg border-l border-border z-40 flex flex-col overflow-hidden transition-transform duration-300 ease-out"
        style={{ transform: isOpen ? 'translateX(0)' : 'translateX(380px)' }}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border flex-shrink-0">
          <h2 className="text-sm font-semibold text-text">Alert Detail</h2>
          <button
            onClick={onClose}
            aria-label="Close panel"
            className="text-muted hover:text-text transition-colors text-xl leading-none"
          >
            ×
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto">
          {isPending && (
            <div className="flex items-center justify-center h-40">
              <div className="w-6 h-6 border-2 border-border border-t-accent-light rounded-full animate-spin" />
            </div>
          )}

          {alert && (
            <div className="animate-fade-in">
              {/* Alert meta */}
              <div className="px-5 pt-4 pb-3 border-b border-border">
                <div className="flex items-center justify-between mb-1">
                  <span className="font-mono text-[12px] text-muted">{alert.alertNumber}</span>
                  <SeverityBadge severity={alert.severity} />
                </div>
                <p className="text-sm text-text">
                  {TX_TYPE_LABEL[alert.transactionType] ?? alert.transactionType}
                  {alert.merchantCategory ? ` · ${alert.merchantCategory}` : ''}
                </p>
              </div>

              {/* Risk gauge */}
              <div className="px-5 py-5 border-b border-border">
                <RiskGauge
                  score={alert.riskScore}
                  severity={alert.severity}
                  ruleDetails={alert.ruleDetails}
                />
              </div>

              {/* Transaction details */}
              <div className="px-5 py-4 border-b border-border space-y-2">
                <h3 className="text-[11px] font-medium uppercase tracking-[0.05em] text-muted mb-3">
                  Transaction
                </h3>
                <Row label="Amount">
                  <span className="font-mono text-sm text-text">
                    {AMOUNT_FMT.format(parseFloat(alert.amount))} {alert.currency}
                  </span>
                </Row>
                <Row label="Account">
                  <span className="font-mono text-[12px] text-muted">
                    {alert.accountNumber ?? alert.accountId.slice(0, 8) + '…'}
                  </span>
                </Row>
                {alert.counterpartyAccountId && (
                  <Row label="Counterparty">
                    <span className="font-mono text-[12px] text-muted">
                      {alert.counterpartyAccountId.slice(0, 8)}…
                    </span>
                  </Row>
                )}
                {alert.description && (
                  <Row label="Memo">
                    <span className="text-sm text-text truncate max-w-[200px]">
                      {alert.description}
                    </span>
                  </Row>
                )}
                <Row label="Initiated">
                  <span className="font-mono text-[12px] text-muted">
                    {new Date(alert.transactionInitiatedAt).toLocaleString()}
                  </span>
                </Row>
              </div>

              {/* Review status */}
              {(alert.status !== 'OPEN') && (
                <div className="px-5 py-4 border-b border-border">
                  <h3 className="text-[11px] font-medium uppercase tracking-[0.05em] text-muted mb-2">
                    Review
                  </h3>
                  <p className="text-sm text-text mb-1">
                    Status: <strong>{alert.status.replace('_', ' ')}</strong>
                  </p>
                  {alert.adminNotes && (
                    <p className="text-sm text-muted italic">"{alert.adminNotes}"</p>
                  )}
                  {alert.reviewedAt && (
                    <p className="font-mono text-[11px] text-muted mt-1">
                      {new Date(alert.reviewedAt).toLocaleString()}
                    </p>
                  )}
                </div>
              )}

              {/* Actions */}
              {(alert.status === 'OPEN' || alert.status === 'UNDER_REVIEW') && (
                <div className="px-5 py-4 space-y-3">
                  <h3 className="text-[11px] font-medium uppercase tracking-[0.05em] text-muted">
                    Actions
                  </h3>
                  <textarea
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    placeholder="Add review notes (optional)…"
                    rows={2}
                    className="w-full bg-surface border border-border rounded px-3 py-2 text-sm text-text placeholder-muted resize-none focus:outline-none focus:ring-1 focus:ring-accent-light"
                  />
                  <div className="flex gap-2">
                    {alert.status === 'OPEN' && (
                      <ActionBtn
                        onClick={() => review({ status: 'UNDER_REVIEW', note: notes })}
                        disabled={reviewing}
                        className="border-border text-muted hover:text-text"
                      >
                        Mark Under Review
                      </ActionBtn>
                    )}
                    <ActionBtn
                      onClick={() => review({ status: 'DISMISSED', note: notes })}
                      disabled={reviewing}
                      className="border-border text-muted hover:text-text"
                    >
                      Dismiss
                    </ActionBtn>
                    <ActionBtn
                      onClick={() => review({ status: 'RESOLVED', note: notes })}
                      disabled={reviewing}
                      className="border-[#16a34a] text-[#16a34a] hover:bg-[#16a34a]/10"
                    >
                      Resolve
                    </ActionBtn>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </aside>
    </>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-[12px] text-muted">{label}</span>
      {children}
    </div>
  )
}

function ActionBtn({
  onClick,
  disabled,
  className,
  children,
}: {
  onClick: () => void
  disabled?: boolean
  className: string
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`flex-1 px-3 py-1.5 rounded border text-[12px] font-medium transition-colors disabled:opacity-50 ${className}`}
    >
      {children}
    </button>
  )
}
