import { create } from 'zustand'

interface AuthState {
  accessToken: string | null
  email: string | null
  role: string | null
  isInitialized: boolean
  setTokens: (accessToken: string, email: string, role: string) => void
  setRefreshToken: (refreshToken: string) => void
  clearAuth: () => void
  silentRefresh: () => Promise<boolean>
}

const REFRESH_TOKEN_KEY = 'lb_refresh_token'

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  email: null,
  role: null,
  isInitialized: false,

  setTokens: (accessToken, email, role) => {
    set({ accessToken, email, role })
  },

  setRefreshToken: (refreshToken) => {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  },

  clearAuth: () => {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    set({ accessToken: null, email: null, role: null })
  },

  silentRefresh: async () => {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (!refreshToken) {
      set({ isInitialized: true })
      return false
    }
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!res.ok) {
        get().clearAuth()
        set({ isInitialized: true })
        return false
      }
      const data = await res.json()
      localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken)
      set({
        accessToken: data.accessToken,
        email: data.email,
        role: data.role,
        isInitialized: true,
      })
      return true
    } catch {
      get().clearAuth()
      set({ isInitialized: true })
      return false
    }
  },
}))
