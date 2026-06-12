import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ProtectedRoute } from './components/auth/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'

export default function App() {
  return (
    <BrowserRouter>
      <a
        href="#main"
        className="fixed top-2 left-2 z-50 bg-surface text-text px-4 py-2 rounded border border-border focus:outline-none focus:ring-2 focus:ring-accent-light sr-only focus:not-sr-only"
      >
        Skip to main content
      </a>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route element={<ProtectedRoute />}>
          {/* Phase 6 admin pages added in T9–T14 */}
        </Route>
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
