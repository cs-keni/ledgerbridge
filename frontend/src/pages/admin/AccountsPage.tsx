import { useQuery } from '@tanstack/react-query'
import { fetchAccounts } from '../../api/accounts'
import type { AccountResponse, AccountStatus } from '../../types/api'

const AMOUNT_FMT = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

const STATUS_COLOR: Record<AccountStatus, string> = {
  ACTIVE: '#34d399',
  SUSPENDED: '#d97706',
  CLOSED: '#888888',
}

export default function AccountsPage() {
  const { data: accounts, isPending } = useQuery<AccountResponse[]>({
    queryKey: ['accounts'],
    queryFn: () => fetchAccounts(),
    staleTime: 30_000,
  })

  const list = accounts ?? []

  return (
    <div className="p-6 max-w-5xl mx-auto animate-fade-in">
      <div className="mb-6">
        <h1 className="text-lg font-semibold text-text">Accounts</h1>
        <p className="text-sm text-muted mt-0.5">
          All ledger accounts for your user
          {list.length > 0 && (
            <span className="ml-2 font-mono text-[12px]">({list.length})</span>
          )}
        </p>
      </div>

      <div className="rounded-lg border border-border overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="border-b border-border">
              {['Account #', 'Type', 'Status', 'Balance', 'Currency', 'Opened'].map((h) => (
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
                <tr key={i} className="border-b border-border h-[44px]">
                  {[0, 1, 2, 3, 4, 5].map((j) => (
                    <td key={j} className="px-4">
                      <div
                        className="h-2.5 rounded animate-shimmer bg-gradient-to-r from-surface via-border to-surface bg-[length:200%_100%]"
                        style={{
                          animationDelay: `${(i * 6 + j) * 40}ms`,
                          width: j === 3 ? '80%' : '60%',
                        }}
                      />
                    </td>
                  ))}
                </tr>
              ))}

            {!isPending && list.length === 0 && (
              <tr>
                <td colSpan={6} className="py-16 text-center">
                  <p className="text-muted text-sm mb-2">No accounts yet.</p>
                  <p className="text-[12px] text-muted">
                    Open an account or trigger a demo scenario via Swagger UI.
                  </p>
                </td>
              </tr>
            )}

            {list.map((acc) => (
              <tr
                key={acc.id}
                className="border-b border-border h-[44px] hover:bg-surface transition-colors"
              >
                <td className="px-4 font-mono text-[12px] text-text">{acc.accountNumber}</td>
                <td className="px-4 text-sm text-text">{acc.type}</td>
                <td className="px-4">
                  <span
                    className="inline-flex items-center gap-1.5 text-[12px] font-medium"
                    style={{ color: STATUS_COLOR[acc.status] }}
                  >
                    <span
                      className="w-1.5 h-1.5 rounded-full"
                      style={{ background: STATUS_COLOR[acc.status] }}
                    />
                    {acc.status}
                  </span>
                </td>
                <td className="px-4 font-mono text-sm text-text">
                  {AMOUNT_FMT.format(parseFloat(acc.balance))}
                </td>
                <td className="px-4 text-sm text-muted">{acc.currency}</td>
                <td className="px-4 font-mono text-[12px] text-muted">
                  {new Date(acc.openedAt).toLocaleDateString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
