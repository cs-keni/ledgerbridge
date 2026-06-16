import { useSpring } from '../../hooks/useSpring'
import type { AlertSeverity } from '../../types/api'

// ── Geometry ─────────────────────────────────────────────────────────────────
const W = 200, H = 160
const CX = 100, CY = 108
const R = 76            // arc centerline radius
const STROKE = 14       // arc stroke width
const START = 135       // SVG start angle (degrees)  — lower-left
const SWEEP = 270       // total arc sweep

function deg2rad(d: number) { return (d * Math.PI) / 180 }

function polarPt(r: number, angleDeg: number) {
  const a = deg2rad(angleDeg)
  return { x: CX + r * Math.cos(a), y: CY + r * Math.sin(a) }
}

function arcPath(fromScore: number, toScore: number, r = R) {
  if (toScore - fromScore < 0.001) return ''
  const start = polarPt(r, START + fromScore * SWEEP)
  const end = polarPt(r, START + toScore * SWEEP)
  const large = (toScore - fromScore) * SWEEP > 180 ? 1 : 0
  return (
    `M ${start.x.toFixed(2)} ${start.y.toFixed(2)} ` +
    `A ${r} ${r} 0 ${large} 1 ${end.x.toFixed(2)} ${end.y.toFixed(2)}`
  )
}

// ── Colour helpers ────────────────────────────────────────────────────────────
const SEG_COLORS = [
  { from: 0.0, to: 0.3, color: '#34d399' },
  { from: 0.3, to: 0.6, color: '#d97706' },
  { from: 0.6, to: 1.0, color: '#dc2626' },
]

function scoreColor(score: number) {
  if (score < 0.3) return '#34d399'
  if (score < 0.6) return '#d97706'
  return '#dc2626'
}

// ── Threshold marker (score 0.4) ─────────────────────────────────────────────
const THRESH = 0.4
const THRESH_ANGLE = START + THRESH * SWEEP
const THRESH_OUTER = polarPt(R + STROKE / 2 + 4, THRESH_ANGLE)
const THRESH_INNER = polarPt(R - STROKE / 2 - 4, THRESH_ANGLE)
const THRESH_LABEL = polarPt(R + STROKE / 2 + 14, THRESH_ANGLE)

// ── Rule definitions ──────────────────────────────────────────────────────────
const RULES = [
  { key: 'amountAnomaly', label: 'Amount Anomaly', weight: 0.25 },
  { key: 'velocity', label: 'Velocity', weight: 0.30 },
  { key: 'behavioral', label: 'Behavioral', weight: 0.20 },
  { key: 'graphPattern', label: 'Graph Pattern', weight: 0.25 },
]

function getRuleScore(ruleDetails: Record<string, unknown>, key: string): number {
  const entry = ruleDetails[key]
  if (typeof entry === 'number') {
    return Math.max(0, Math.min(1, entry))
  }
  if (entry && typeof entry === 'object' && 'score' in entry) {
    return Math.max(0, Math.min(1, Number((entry as { score: unknown }).score) || 0))
  }
  return 0
}

// ── Component ────────────────────────────────────────────────────────────────
interface Props {
  score: number
  severity: AlertSeverity
  ruleDetails: Record<string, unknown>
}

export function RiskGauge({ score, severity, ruleDetails }: Props) {
  const animated = useSpring(Math.max(0, Math.min(1, score)))
  const color = scoreColor(animated)

  // Filled arc segments: split at 0.3 and 0.6 boundaries
  const filledSegs = SEG_COLORS.map((seg) => ({
    ...seg,
    to: Math.min(animated, seg.to),
  })).filter((seg) => seg.to > seg.from)

  return (
    <div className="flex flex-col items-center gap-4">
      {/* ── SVG gauge ── */}
      <svg
        width={W}
        height={H}
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={`Risk score ${score.toFixed(2)}, ${severity}`}
      >
        {/* Track (full arc background) */}
        <path
          d={arcPath(0, 1)}
          stroke="#2a2a2a"
          strokeWidth={STROKE}
          fill="none"
          strokeLinecap="round"
        />

        {/* Filled arc (animated, gradient via multiple paths) */}
        {filledSegs.map((seg, i) => (
          <path
            key={i}
            d={arcPath(seg.from, seg.to)}
            stroke={seg.color}
            strokeWidth={STROKE}
            fill="none"
            strokeLinecap={i === filledSegs.length - 1 ? 'round' : 'butt'}
          />
        ))}

        {/* Threshold marker at 0.4 */}
        <line
          x1={THRESH_OUTER.x.toFixed(2)}
          y1={THRESH_OUTER.y.toFixed(2)}
          x2={THRESH_INNER.x.toFixed(2)}
          y2={THRESH_INNER.y.toFixed(2)}
          stroke="#888888"
          strokeWidth={2}
          strokeDasharray="2 2"
        />
        <text
          x={THRESH_LABEL.x.toFixed(2)}
          y={THRESH_LABEL.y.toFixed(2)}
          fill="#888888"
          fontSize={9}
          textAnchor={THRESH_LABEL.x < CX ? 'end' : 'start'}
          dominantBaseline="middle"
          fontFamily="Inter, sans-serif"
        >
          Alert threshold
        </text>

        {/* Center: score value */}
        <text
          x={CX}
          y={CY - 6}
          fill={color}
          fontSize={28}
          fontWeight={700}
          fontFamily="'Geist Mono', 'JetBrains Mono', monospace"
          textAnchor="middle"
          dominantBaseline="middle"
        >
          {animated.toFixed(2)}
        </text>

        {/* Center: severity label */}
        <text
          x={CX}
          y={CY + 18}
          fill={color}
          fontSize={11}
          fontWeight={700}
          fontFamily="Inter, sans-serif"
          textAnchor="middle"
          dominantBaseline="middle"
          letterSpacing="0.08em"
        >
          {severity}
        </text>
      </svg>

      {/* ── Rule contribution bars ── */}
      <div className="w-full px-2 space-y-2">
        {RULES.map((rule) => {
          const ruleScore = getRuleScore(ruleDetails, rule.key)
          const contribution = ruleScore * rule.weight
          const barColor = scoreColor(ruleScore)
          return (
            <div key={rule.key}>
              <div className="flex justify-between items-center mb-0.5">
                <span className="text-[11px] text-muted">{rule.label}</span>
                <span
                  className="text-[11px] font-mono"
                  style={{ color: ruleScore > 0.3 ? barColor : '#888888' }}
                  title={`Raw score: ${ruleScore.toFixed(2)} × weight ${rule.weight}`}
                >
                  +{contribution.toFixed(2)}
                </span>
              </div>
              <div className="h-1.5 bg-border rounded-full overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-300"
                  style={{
                    width: `${ruleScore * 100}%`,
                    background: barColor,
                    opacity: ruleScore > 0.01 ? 1 : 0.3,
                  }}
                />
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
