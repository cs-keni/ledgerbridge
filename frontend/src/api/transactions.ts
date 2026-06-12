import { api } from './client'
import type { TransactionResponse } from '../types/api'

export interface DepositRequest {
  accountId: string
  amount: string
  merchantCategory?: string
  description?: string
}

export interface TransferRequest {
  fromAccountId: string
  toAccountId: string
  amount: string
  description?: string
}

function idempotencyKey() {
  return crypto.randomUUID()
}

export function deposit(body: DepositRequest) {
  return api.post<TransactionResponse>('/api/transactions/deposit', body, {
    'Idempotency-Key': idempotencyKey(),
  })
}

export function withdraw(body: DepositRequest) {
  return api.post<TransactionResponse>('/api/transactions/withdraw', body, {
    'Idempotency-Key': idempotencyKey(),
  })
}

export function transfer(body: TransferRequest) {
  return api.post<TransactionResponse>('/api/transactions/transfer', body, {
    'Idempotency-Key': idempotencyKey(),
  })
}
