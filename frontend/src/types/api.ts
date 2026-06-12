export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AlertStatus = 'OPEN' | 'UNDER_REVIEW' | 'DISMISSED' | 'ESCALATED' | 'RESOLVED'
export type AlertType = 'AMOUNT_ANOMALY' | 'VELOCITY_ANOMALY' | 'BEHAVIORAL_ANOMALY' | 'GRAPH_PATTERN'
export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER_DEBIT' | 'TRANSFER_CREDIT'

export interface RiskAlertResponse {
  id: string
  alertNumber: string
  transactionId: string
  userId: string
  alertType: AlertType
  severity: AlertSeverity
  status: AlertStatus
  riskScore: number
  ruleDetails: Record<string, unknown>
  createdAt: string
  reviewedByAdminId: string | null
  adminNotes: string | null
  reviewedAt: string | null
}

export interface AlertDetailResponse extends RiskAlertResponse {
  amount: string
  currency: string
  transactionType: TransactionType
  accountId: string
  accountNumber: string | null
  counterpartyAccountId: string | null
  description: string | null
  merchantCategory: string | null
  transactionInitiatedAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  last: boolean
  first: boolean
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  email: string
  role: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
}

export interface AuditLogResponse {
  id: string
  entityType: string
  entityId: string | null
  action: string
  userId: string | null
  ipAddress: string | null
  correlationId: string | null
  outcome: string | null
  occurredAt: string
}
