import { api } from './client'
import type { AccountResponse, Page, TransactionResponse } from '../types/api'

export function fetchAccounts(_params?: { page?: number; size?: number }) {
  return api.get<AccountResponse[]>('/api/accounts')
}

export function fetchTransactions(params: { page?: number; size?: number } = {}) {
  const qs = new URLSearchParams()
  if (params.page !== undefined) qs.set('page', String(params.page))
  qs.set('size', String(params.size ?? 20))
  qs.set('sort', 'initiatedAt,desc')
  return api.get<Page<TransactionResponse>>(`/api/transactions?${qs}`)
}
