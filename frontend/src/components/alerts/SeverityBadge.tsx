import type { AlertSeverity } from '../../types/api'

const DOT: Record<AlertSeverity, string> = {
  CRITICAL: '#dc2626',
  HIGH: '#ea580c',
  MEDIUM: '#d97706',
  LOW: '#16a34a',
}

const TEXT: Record<AlertSeverity, string> = {
  CRITICAL: '#dc2626',
  HIGH: '#ea580c',
  MEDIUM: '#d97706',
  LOW: '#16a34a',
}

interface Props {
  severity: AlertSeverity
}

export function SeverityBadge({ severity }: Props) {
  return (
    <span
      className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[11px] font-bold uppercase tracking-[0.08em]"
      style={{ background: '#1e1e1e', border: '1px solid #3a3a3a' }}
    >
      <span
        className="w-1.5 h-1.5 rounded-full flex-shrink-0"
        style={{ background: DOT[severity] }}
        aria-hidden="true"
      />
      <span style={{ color: TEXT[severity] }}>{severity}</span>
    </span>
  )
}
