import { api } from './client'
import type { AuditLogResponse, Page } from '../types/api'

export function fetchAuditLog(params: { page?: number; size?: number } = {}) {
  const qs = new URLSearchParams()
  if (params.page !== undefined) qs.set('page', String(params.page))
  qs.set('size', String(params.size ?? 20))
  return api.get<Page<AuditLogResponse>>(`/api/admin/audit-log?${qs}`)
}
