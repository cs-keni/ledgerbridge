import type { AlertSeverity } from '../../types/api'

const SIZE = 24
const CX = SIZE / 2
const CY = SIZE / 2
const R = 9
const STROKE = 3
const START = 135  // degrees
const SWEEP = 270

function deg2rad(d: number) {
  return (d * Math.PI) / 180
}

function pt(angleDeg: number) {
  const r = deg2rad(angleDeg)
  return { x: CX + R * Math.cos(r), y: CY + R * Math.sin(r) }
}

function arc(fromScore: number, toScore: number) {
  if (toScore - fromScore < 0.001) return ''
  const s = pt(START + fromScore * SWEEP)
  const e = pt(START + toScore * SWEEP)
  const la = (toScore - fromScore) * SWEEP > 180 ? 1 : 0
  return `M ${s.x.toFixed(2)} ${s.y.toFixed(2)} A ${R} ${R} 0 ${la} 1 ${e.x.toFixed(2)} ${e.y.toFixed(2)}`
}

const FILL_COLOR: Record<AlertSeverity, string> = {
  LOW: '#16a34a',
  MEDIUM: '#d97706',
  HIGH: '#ea580c',
  CRITICAL: '#dc2626',
}

interface Props {
  score: number
  severity: AlertSeverity
}

export function ScoreChip({ score, severity }: Props) {
  const clamped = Math.max(0, Math.min(1, score))
  const color = FILL_COLOR[severity]

  return (
    <svg
      width={SIZE}
      height={SIZE}
      viewBox={`0 0 ${SIZE} ${SIZE}`}
      aria-label={`Risk score ${score.toFixed(2)}`}
      role="img"
    >
      {/* Track */}
      <path
        d={arc(0, 1)}
        stroke="#2a2a2a"
        strokeWidth={STROKE}
        fill="none"
        strokeLinecap="round"
      />
      {/* Filled */}
      {clamped > 0 && (
        <path
          d={arc(0, clamped)}
          stroke={color}
          strokeWidth={STROKE}
          fill="none"
          strokeLinecap="round"
        />
      )}
    </svg>
  )
}
