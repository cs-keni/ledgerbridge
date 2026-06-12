import { api } from './client'
import type {
  AlertDetailResponse,
  AlertReviewRequest,
  AlertStatsResponse,
  Page,
  RiskAlertResponse,
} from '../types/api'

export function fetchAlerts(params: { status?: string; page?: number; size?: number } = {}) {
  const qs = new URLSearchParams()
  if (params.status) qs.set('status', params.status)
  if (params.page !== undefined) qs.set('page', String(params.page))
  qs.set('size', String(params.size ?? 50))
  qs.set('sort', 'createdAt,desc')
  return api.get<Page<RiskAlertResponse>>(`/api/admin/alerts?${qs}`)
}

export function fetchAlertDetail(id: string) {
  return api.get<AlertDetailResponse>(`/api/admin/alerts/${id}`)
}

export function reviewAlert(id: string, body: AlertReviewRequest) {
  return api.patch<RiskAlertResponse>(`/api/admin/alerts/${id}/review`, body)
}

export function fetchAlertStats() {
  return api.get<AlertStatsResponse>('/api/admin/alerts/stats')
}
