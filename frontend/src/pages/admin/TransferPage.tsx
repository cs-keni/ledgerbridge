import { useState, useId } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchAccounts } from '../../api/accounts'
import { deposit, withdraw, transfer } from '../../api/transactions'
import type { TransactionResponse } from '../../types/api'
import { ApiError } from '../../api/client'

type Tab = 'deposit' | 'withdraw' | 'transfer'

const TAB_LABELS: { key: Tab; label: string }[] = [
  { key: 'deposit', label: 'Deposit' },
  { key: 'withdraw', label: 'Withdraw' },
  { key: 'transfer', label: 'Transfer' },
]

const AMOUNT_FMT = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

function txTypeLabel(type: string) {
  return { DEPOSIT: 'Deposit', WITHDRAWAL: 'Withdrawal', TRANSFER_DEBIT: 'Transfer', TRANSFER_CREDIT: 'Transfer' }[type] ?? type
}

export default function TransferPage() {
  const [tab, setTab] = useState<Tab>('deposit')
  const [success, setSuccess] = useState<TransactionResponse | null>(null)
  const qc = useQueryClient()

  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts'],
    queryFn: () => fetchAccounts(),
    staleTime: 30_000,
  })

  const activeAccounts = accounts.filter((a) => a.status === 'ACTIVE')

  const { mutate, isPending, error, reset } = useMutation({
    mutationFn: (vars: Parameters<typeof deposit>[0] | Parameters<typeof transfer>[0]) => {
      if (tab === 'deposit') return deposit(vars as Parameters<typeof deposit>[0])
      if (tab === 'withdraw') return withdraw(vars as Parameters<typeof deposit>[0])
      return transfer(vars as Parameters<typeof transfer>[0])
    },
    onSuccess: (data) => {
      setSuccess(data)
      qc.invalidateQueries({ queryKey: ['accounts'] })
    },
  })

  const apiErr = error instanceof ApiError ? error.message : error ? String(error) : null

  return (
    <div className="p-6 max-w-lg mx-auto animate-fade-in">
      <div className="mb-6">
        <h1 className="text-lg font-semibold text-text">Transactions</h1>
        <p className="text-sm text-muted mt-0.5">Deposit, withdraw, or transfer funds</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-border mb-6">
        {TAB_LABELS.map(({ key, label }) => (
          <button
            key={key}
            onClick={() => {
              setTab(key)
              setSuccess(null)
              reset()
            }}
            className={`px-5 py-2 text-sm border-b-2 -mb-px transition-colors ${
              tab === key
                ? 'border-text text-text'
                : 'border-transparent text-muted hover:text-text'
            }`}
            aria-selected={tab === key}
            role="tab"
          >
            {label}
          </button>
        ))}
      </div>

      {success ? (
        <SuccessCard tx={success} onNew={() => { setSuccess(null); reset() }} />
      ) : (
        <>
          {tab === 'deposit' && (
            <SingleAccountForm
              label="Deposit"
              accounts={activeAccounts}
              isPending={isPending}
              error={apiErr}
              onSubmit={(accountId, amount, desc) =>
                mutate({ accountId, amount, description: desc || undefined })
              }
            />
          )}
          {tab === 'withdraw' && (
            <SingleAccountForm
              label="Withdraw"
              accounts={activeAccounts}
              isPending={isPending}
              error={apiErr}
              onSubmit={(accountId, amount, desc) =>
                mutate({ accountId, amount, description: desc || undefined })
              }
            />
          )}
          {tab === 'transfer' && (
            <TransferForm
              accounts={activeAccounts}
              isPending={isPending}
              error={apiErr}
              onSubmit={(fromAccountId, toAccountId, amount, desc) =>
                mutate({ fromAccountId, toAccountId, amount, description: desc || undefined })
              }
            />
          )}
        </>
      )}
    </div>
  )
}

// ── Single-account form (deposit / withdraw) ──────────────────────────────────

interface SingleFormProps {
  label: string
  accounts: { id: string; accountNumber: string; balance: string }[]
  isPending: boolean
  error: string | null
  onSubmit: (accountId: string, amount: string, description: string) => void
}

function SingleAccountForm({ label, accounts, isPending, error, onSubmit }: SingleFormProps) {
  const [accountId, setAccountId] = useState(accounts[0]?.id ?? '')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const accountLabelId = useId()
  const amountLabelId = useId()
  const descLabelId = useId()

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!accountId || !amount) return
    onSubmit(accountId, amount, description)
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="space-y-5">
        <div>
          <label id={accountLabelId} htmlFor={`account-${accountLabelId}`} className={LABEL_CLASS}>
            Account
          </label>
          {accounts.length === 0 ? (
            <p className="text-sm text-muted mt-1">
              No active accounts. Open one first via{' '}
              <a href="/swagger-ui.html" target="_blank" rel="noopener noreferrer" className="underline">
                Swagger UI
              </a>
              .
            </p>
          ) : (
            <select
              id={`account-${accountLabelId}`}
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
              required
              className={SELECT_CLASS}
            >
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.accountNumber} — bal {AMOUNT_FMT.format(parseFloat(a.balance))}
                </option>
              ))}
            </select>
          )}
        </div>

        <div>
          <label id={amountLabelId} htmlFor={`amount-${amountLabelId}`} className={LABEL_CLASS}>
            Amount (USD)
          </label>
          <input
            id={`amount-${amountLabelId}`}
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="0.00"
            required
            className={INPUT_CLASS}
            aria-describedby={error ? 'form-error' : undefined}
          />
        </div>

        <div>
          <label id={descLabelId} htmlFor={`desc-${descLabelId}`} className={LABEL_CLASS}>
            Description{' '}
            <span className="text-muted font-normal normal-case">(optional)</span>
          </label>
          <input
            id={`desc-${descLabelId}`}
            type="text"
            maxLength={200}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. Payroll deposit"
            className={INPUT_CLASS}
          />
        </div>

        <ErrorMsg id="form-error" message={error} />

        <SubmitBtn isPending={isPending} label={label} disabled={accounts.length === 0} />
      </div>
    </form>
  )
}

// ── Transfer form ─────────────────────────────────────────────────────────────

interface TransferFormProps {
  accounts: { id: string; accountNumber: string; balance: string }[]
  isPending: boolean
  error: string | null
  onSubmit: (fromAccountId: string, toAccountId: string, amount: string, description: string) => void
}

function TransferForm({ accounts, isPending, error, onSubmit }: TransferFormProps) {
  const [fromId, setFromId] = useState(accounts[0]?.id ?? '')
  const [toId, setToId] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const fromLabelId = useId()
  const toLabelId = useId()
  const amountLabelId = useId()
  const descLabelId = useId()

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!fromId || !toId || !amount) return
    onSubmit(fromId, toId, amount, description)
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="space-y-5">
        <div>
          <label id={fromLabelId} htmlFor={`from-${fromLabelId}`} className={LABEL_CLASS}>
            From Account
          </label>
          {accounts.length === 0 ? (
            <p className="text-sm text-muted mt-1">No active accounts.</p>
          ) : (
            <select
              id={`from-${fromLabelId}`}
              value={fromId}
              onChange={(e) => setFromId(e.target.value)}
              required
              className={SELECT_CLASS}
            >
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.accountNumber} — bal {AMOUNT_FMT.format(parseFloat(a.balance))}
                </option>
              ))}
            </select>
          )}
        </div>

        <div>
          <label id={toLabelId} htmlFor={`to-${toLabelId}`} className={LABEL_CLASS}>
            To Account ID
          </label>
          <input
            id={`to-${toLabelId}`}
            type="text"
            value={toId}
            onChange={(e) => setToId(e.target.value)}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            required
            pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
            className={INPUT_CLASS}
            aria-describedby="to-account-hint"
          />
          <p id="to-account-hint" className="mt-1 text-[11px] text-muted">
            Use a demo account UUID from the seed data or create one via{' '}
            <a href="/swagger-ui.html" target="_blank" rel="noopener noreferrer" className="underline">
              Swagger UI
            </a>
            .
          </p>
        </div>

        <div>
          <label id={amountLabelId} htmlFor={`amount-${amountLabelId}`} className={LABEL_CLASS}>
            Amount (USD)
          </label>
          <input
            id={`amount-${amountLabelId}`}
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="0.00"
            required
            className={INPUT_CLASS}
            aria-describedby={error ? 'form-error' : undefined}
          />
        </div>

        <div>
          <label id={descLabelId} htmlFor={`desc-${descLabelId}`} className={LABEL_CLASS}>
            Description{' '}
            <span className="text-muted font-normal normal-case">(optional)</span>
          </label>
          <input
            id={`desc-${descLabelId}`}
            type="text"
            maxLength={200}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. Rent payment"
            className={INPUT_CLASS}
          />
        </div>

        <ErrorMsg id="form-error" message={error} />

        <SubmitBtn isPending={isPending} label="Send Transfer" disabled={accounts.length === 0} />
      </div>
    </form>
  )
}

// ── Success card ──────────────────────────────────────────────────────────────

function SuccessCard({ tx, onNew }: { tx: TransactionResponse; onNew: () => void }) {
  return (
    <div
      className="rounded-lg border border-[#16a34a]/40 bg-[#16a34a]/5 p-6 text-center animate-fade-in"
      role="status"
      aria-live="polite"
    >
      <div className="w-10 h-10 rounded-full bg-[#16a34a]/20 flex items-center justify-center mx-auto mb-4">
        <span className="text-[#34d399] text-xl">✓</span>
      </div>
      <p className="text-text font-semibold mb-1">{txTypeLabel(tx.type)} confirmed</p>
      <p className="font-mono text-2xl text-[#34d399] font-bold mb-1">
        {AMOUNT_FMT.format(parseFloat(tx.amount))}
      </p>
      <p className="font-mono text-[12px] text-muted mb-4">{tx.transactionNumber}</p>
      <p className="text-[12px] text-muted mb-6">
        Status: <span className="text-text">{tx.status}</span>
        {' · '}
        <span>{new Date(tx.initiatedAt).toLocaleTimeString()}</span>
      </p>
      <button
        onClick={onNew}
        className="px-5 py-2 rounded border border-border text-sm text-muted hover:text-text transition-colors"
      >
        New transaction
      </button>
    </div>
  )
}

// ── Shared primitives ─────────────────────────────────────────────────────────

const LABEL_CLASS = 'block text-[12px] font-medium uppercase tracking-[0.05em] text-muted mb-1.5'
const INPUT_CLASS =
  'w-full bg-surface border border-border rounded px-3 py-2 text-sm text-text placeholder-muted focus:outline-none focus:ring-2 focus:ring-accent-light/60 transition-shadow'
const SELECT_CLASS =
  'w-full bg-surface border border-border rounded px-3 py-2 text-sm text-text focus:outline-none focus:ring-2 focus:ring-accent-light/60 transition-shadow'

function ErrorMsg({ id, message }: { id: string; message: string | null }) {
  if (!message) return null
  return (
    <p id={id} role="alert" className="text-sm text-[#dc2626] animate-shake">
      {message}
    </p>
  )
}

function SubmitBtn({ isPending, label, disabled }: { isPending: boolean; label: string; disabled?: boolean }) {
  return (
    <button
      type="submit"
      disabled={isPending || disabled}
      className="w-full py-2.5 rounded bg-accent text-white text-sm font-medium hover:bg-accent-light transition-colors disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-accent-light/60"
    >
      {isPending ? (
        <span className="flex items-center justify-center gap-2">
          <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          Processing…
        </span>
      ) : (
        label
      )}
    </button>
  )
}
