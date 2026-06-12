export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AlertStatus = 'OPEN' | 'UNDER_REVIEW' | 'DISMISSED' | 'ESCALATED' | 'RESOLVED'
export type AlertType = 'AMOUNT_ANOMALY' | 'VELOCITY_ANOMALY' | 'BEHAVIORAL_ANOMALY' | 'GRAPH_PATTERN'
export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER_DEBIT' | 'TRANSFER_CREDIT'
export type AccountType = 'CHECKING' | 'SAVINGS' | 'BUSINESS'
export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

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
  tokenType: string
  expiresIn: number
  userId: string
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

export interface AlertReviewRequest {
  status: AlertStatus
  notes?: string
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

export interface AccountResponse {
  id: string
  accountNumber: string
  type: AccountType
  status: AccountStatus
  balance: string
  currency: string
  openedAt: string
  closedAt: string | null
}

export interface TransactionResponse {
  id: string
  transactionNumber: string
  accountId: string
  counterpartyAccountId: string | null
  type: TransactionType
  amount: string
  currency: string
  status: TransactionStatus
  merchantCategory: string | null
  description: string | null
  correlationId: string | null
  initiatedAt: string
  completedAt: string | null
}
