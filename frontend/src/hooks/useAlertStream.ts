import { useEffect, useRef } from 'react'
import { useAuthStore } from '../stores/authStore'
import { useSseStore } from '../stores/sseStore'
import type { RiskAlertResponse } from '../types/api'

export function useAlertStream(onAlert: (alert: RiskAlertResponse) => void) {
  const accessToken = useAuthStore((s) => s.accessToken)
  const onAlertRef = useRef(onAlert)
  onAlertRef.current = onAlert

  useEffect(() => {
    if (!accessToken) {
      useSseStore.getState().setStatus('disconnected')
      return
    }

    let cancelled = false
    let backoff = 1000
    let reader: ReadableStreamDefaultReader<Uint8Array> | null = null

    async function connect() {
      if (cancelled) return
      useSseStore.getState().setStatus('reconnecting')

      const token = useAuthStore.getState().accessToken
      if (!token) {
        useSseStore.getState().setStatus('disconnected')
        return
      }

      try {
        const res = await fetch('/api/admin/alerts/stream', {
          headers: { Authorization: `Bearer ${token}` },
        })

        if (res.status === 401) {
          if (!cancelled) {
            const refreshed = await useAuthStore.getState().silentRefresh()
            if (refreshed && !cancelled) {
              backoff = 1000
              connect()
            } else if (!cancelled) {
              useSseStore.getState().setStatus('disconnected')
            }
          }
          return
        }

        if (!res.ok || !res.body) {
          scheduleReconnect()
          return
        }

        useSseStore.getState().setStatus('connected')
        backoff = 1000
        reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let currentEvent = ''
        let currentData = ''

        while (!cancelled) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() ?? ''

          for (const line of lines) {
            const trimmed = line.trimEnd()
            if (trimmed.startsWith('event:')) {
              currentEvent = trimmed.slice(6).trim()
            } else if (trimmed.startsWith('data:')) {
              currentData = trimmed.slice(5).trim()
            } else if (trimmed === '') {
              if (currentEvent === 'alert' && currentData) {
                try {
                  const alert = JSON.parse(currentData) as RiskAlertResponse
                  if (!cancelled) onAlertRef.current(alert)
                } catch { /* malformed frame */ }
              }
              currentEvent = ''
              currentData = ''
            }
            // Lines starting with ':' are heartbeat comments — skip
          }
        }
      } catch {
        // Network error
      }

      reader = null
      if (!cancelled) scheduleReconnect()
    }

    function scheduleReconnect() {
      if (cancelled) return
      useSseStore.getState().setStatus('reconnecting')
      const delay = backoff
      backoff = Math.min(delay * 2, 30_000)
      setTimeout(() => { if (!cancelled) connect() }, delay)
    }

    connect()

    return () => {
      cancelled = true
      reader?.cancel()
      useSseStore.getState().setStatus('disconnected')
    }
  }, [accessToken])
}
