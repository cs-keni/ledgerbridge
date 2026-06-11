# DESIGN.md — LedgerBridge Design System

> Locked via /plan-design-review 2026-06-10. All Phase 6 implementation decisions calibrate against this file.
> Update this file (not just the code) when any design decision changes.

---

## Classifier: APP UI

LedgerBridge is a calm, dense admin dashboard: utility language, minimal chrome, strong
table/split-pane hierarchy. No decorative imagery, no marketing sections, no card grid.
The first impression must be: **LedgerBridge detects transaction risk in real time.**

---

## Color System (CSS variables)

Define in `frontend/src/index.css` as CSS custom properties:

| Token | Hex | Usage |
|---|---|---|
| `--color-bg` | `#111111` | Page background |
| `--color-surface` | `#1a1a1a` | Card/panel/table-row-hover surfaces |
| `--color-sidebar` | `#161616` | Sidebar background |
| `--color-border` | `#2a2a2a` | All borders |
| `--color-muted` | `#888888` | Secondary text — **only at ≥14px or uppercase**; use `#999999` for small secondary text (see A11y) |
| `--color-text` | `#f0f0f0` | Primary text |
| `--color-accent` | `#6366f1` | Interactive affordances (hover, active states only — never as fill) |
| `--color-accent-light` | `#818cf8` | Chart lines, focus rings |

### Severity Tokens

| Token | Hex | Mapping |
|---|---|---|
| `--color-critical` | `#dc2626` | CRITICAL alerts — dot + gauge fill (0.6–1.0) |
| `--color-high` | `#ea580c` | HIGH alerts |
| `--color-medium` | `#d97706` | MEDIUM alerts + gauge fill (0.3–0.6) |
| `--color-low` | `#16a34a` | LOW alerts + gauge fill (0.0–0.3) |
| `--color-positive` | `#34d399` | Safe range gauge fill |

### Chart Palette

- Chart line: `#818cf8`
- Benchmark: `#6b7280`
- Positive: `#34d399`
- Percentile bands: `#818cf8` at 10–30% opacity

---

## Typography

**No system-ui or -apple-system as primary font.** Import Inter from Fontsource.

```
npm install @fontsource/inter @fontsource/geist-mono
```

| Use | Font | Size | Weight | Notes |
|---|---|---|---|---|
| Body / base | Inter | 14px | 400 | |
| Section headings | Inter | 16px | 600 | |
| Page headings (h1) | Inter | 20px | 700 | |
| Sidebar nav labels | Inter | 12px | 500 | `text-transform: uppercase; letter-spacing: 0.05em` |
| Numeric values (amounts, scores, IDs, timestamps) | Geist Mono | 13px | 400 | `font-variant-numeric: tabular-nums` |
| Severity badge labels | Inter | 11px | 700 | `text-transform: uppercase; letter-spacing: 0.08em` |

Tailwind `fontFamily` config:
```js
fontFamily: {
  sans: ['Inter', 'sans-serif'],
  mono: ['Geist Mono', 'JetBrains Mono', 'monospace'],
}
```

---

## Spacing & Layout

- Table row height: **44px**
- Sidebar width: **160px**
- Main content padding: **24px**
- Stat chip gap: **12px**
- Border radius (chips): **4px** — not bubbly

---

## Animation System

| Animation | Type | Duration | Easing |
|---|---|---|---|
| Route transitions | fade only (opacity) | 150ms | ease-out |
| Card/row stagger entrance | Y-8px translate + opacity | 300ms | ease-out; 40ms gap between items |
| Risk gauge mount | spring from 0 to score | 600ms settle | spring physics (`stiffness: 100, damping: 20`) |
| Risk gauge SSE update | spring from current to new value | 600ms | spring physics |
| Alert row arrival (SSE push) | fade-in + translateY(-4px) | 250ms | ease-out |
| SSE badge count-up | counter animation | 400ms | ease-out |
| Alert detail panel slide-in | translateX(+380px → 0) | 300ms | ease-out |
| Empty state fade-in | fade + translateY(4px → 0) | 150ms | ease-out |
| Skeleton shimmer | gradient sweep | 1500ms | linear, infinite |

**Motion rule:** Motion ONLY for live alert arrival, badge count change, row insertion, connection state change, and gauge update. No ambient or decorative animation.

---

## Global Layout

```
┌──────────────────────────────────────────────────────────────┐
│ Sidebar (160px, #161616)   │  Main Content (#111111)         │
│                            │                                  │
│  LedgerBridge              │  [h1] Risk Alerts   [Demo CTA]  │
│                            │                                  │
│  ● Risk Alerts  ← active   │  [Stat] [Stat] [Stat] [Stat]   │
│    Audit Log               │                                  │
│    Transactions            │  ┌──────────────────────────┐   │
│    Accounts                │  │ Alert Queue Table        │   │
│    Dashboard               │  │ (sortable, 44px rows)    │   │
│                            │  └──────────────────────────┘   │
│  ────────────              │                                  │
│  demo@ledgerbridge.io      │                                  │
│  Logout                    │                                  │
└──────────────────────────────────────────────────────────────┘
```

**DEMO_ACTOR default route:** `/admin/alerts` (post-login redirect)

**Sidebar nav order:** Risk Alerts → Audit Log → Transactions → Accounts → Dashboard

---

## Alert Queue Table

- Row height: 44px
- Columns: Score (mini arc chip) | Account | Amount (`font-mono`) | Triggered Rules | Severity (badge) | Time | —
- Row hover: bg `#1a1a1a`
- Row click: slide in alert detail panel from right (300ms ease-out) — NOT a new route
- Header click: sort by that column (toggle asc/desc)
- Score column: mini 20px arc chip, color-coded by severity

### Alert Queue Header Row

```
Risk Alerts                              [Try a Demo Scenario →]
```

The `Try a Demo Scenario →` button is right-aligned in the header row:
- bg: `#2a2a2a`, hover: `#333`
- text: `#f0f0f0`, 13px
- Opens `/swagger-ui.html` in a new tab

---

## Alert Detail Panel

- Width: 380px, fixed right edge of main content area
- Slide-in animation: 300ms ease-out
- Contains: RiskGauge component, rule breakdown bars, transaction details (monospace amounts/IDs), Review/Dismiss action buttons

---

## RiskGauge Component

**Type:** Radial arc, 270° sweep (like a speedometer)
**Scale:** 0.0 to 1.0
**Score display:** Decimal only (e.g., `0.73`) — never percentage (no `73%`)

**Color gradient (arc fill):**
- 0.0–0.3: `#34d399` (safe)
- 0.3–0.6: `#d97706` (elevated)
- 0.6–1.0: `#dc2626` (critical)

**Threshold marker:** Dotted line on the arc at 0.4, labeled `Alert threshold` in `--color-muted` at 11px

**Center text:** Score value (`font-mono`, 28px, bold) + severity label below (12px uppercase, severity color)

**Below gauge:** Mini horizontal bar chart — 4 bars, one per risk rule:
- AmountAnomaly (weight 0.25)
- Velocity (weight 0.30)
- BehavioralBaseline (weight 0.20)
- GraphPattern (weight 0.25)

Each bar filled proportional to that rule's contribution. Color matches arc gradient by contribution level.

**Animation:**
- Mount: spring animation, 600ms settle from 0 to score
- SSE update: re-animate from current to new value, 600ms spring
- Loading state: skeleton arc (low-opacity shimmer, no fill animation)
- Null score (insufficient history): arc stays at 0, center shows `—` in muted, tooltip `Insufficient transaction history for baseline`

---

## Severity Badge

Pattern: filled dark chip (Datadog / PagerDuty / Grafana style — NOT Bootstrap pastel, NOT colored left-border)

```css
.severity-badge {
  background: #1e1e1e;
  border: 1px solid #3a3a3a;
  border-radius: 4px;
  padding: 2px 8px;
  font: 700 11px/1.4 Inter, sans-serif;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
```

Dot (6px circle, left of label):
- CRITICAL: `#dc2626`
- HIGH: `#ea580c`
- MEDIUM: `#d97706`
- LOW: `#16a34a`

**Never:** colored left-border cards (`border-left: 3px solid ...`), pastel chip backgrounds, full-color chip fills.

---

## Interaction States

| Feature | Loading | Empty | Error | SSE Disconnected |
|---|---|---|---|---|
| Alert queue | 3-row skeleton (shimmer) | "No risk alerts yet. Trigger a fraud scenario in Swagger UI →" [button → /swagger-ui.html] | Toast: "Failed to load alerts. Retry?" | Orange pulsing dot badge + tooltip "Reconnecting to live feed..." |
| Risk gauge | Skeleton arc shimmer | — | "Score unavailable" in center | — |
| Null score | — | `—` center + tooltip "Insufficient transaction history for baseline" | — | — |
| Login form | Spinner on submit button (disabled) | — | Shake animation + "Invalid credentials" below field | — |
| Alert detail panel | Skeleton (gauge + bars shimmer) | — | "Failed to load alert details" inline | — |
| Alert badge (sidebar) | — | Hidden | — | Orange dot, amber bg, "Reconnecting..." tooltip |

---

## SSE Connection State

**Connected:** Green 8px pulsing dot in sidebar nav beside "Risk Alerts" label + `indigo` badge on alert count

**Reconnecting:** Orange 8px pulsing dot + tooltip on hover: "Reconnecting to live feed..."

**Auto-reconnect:** Browser `EventSource` auto-reconnects. UI reflects state without manual refresh.

---

## Accessibility (WCAG 2.1 AA)

- All body text: ≥ 4.5:1 contrast ratio
- **Muted token note:** `#888888` on `#111111` ≈ 4.4:1 — use only for uppercase labels or text ≥ 14px. For small secondary text, use `#999999` (≈ 5.0:1).
- Keyboard navigation: `Tab` through alert table rows, `Enter` to open detail panel, `Escape` to close
- ARIA landmarks: `<nav role="navigation">` (sidebar), `<main>` (content), `role="table"` + `role="row"` on alert queue
- Focus rings: 2px `#818cf8` outline, visible on all interactive elements
- Skip link: `<a href="#main" class="sr-only focus:not-sr-only">Skip to main content</a>` as first DOM element
- Form labels: always visible (never placeholder-as-label)

---

## NOT in scope (design)

- Mobile layout — explicitly out of scope per CEO plan; dashboard is desktop-only
- Tablet-specific breakpoints
- Dark/light mode toggle — dark-only
- Right-to-left layout
- Decorative imagery, hero sections, marketing copy
