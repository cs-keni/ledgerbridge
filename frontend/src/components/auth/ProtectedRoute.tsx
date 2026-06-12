import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '../../stores/authStore'

export function ProtectedRoute() {
  const { accessToken, isInitialized, silentRefresh } = useAuthStore()

  useEffect(() => {
    if (!isInitialized) {
      silentRefresh()
    }
  }, [isInitialized, silentRefresh])

  if (!isInitialized) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center">
        <div className="w-8 h-8 border-2 border-accent border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  return accessToken ? <Outlet /> : <Navigate to="/login" replace />
}
