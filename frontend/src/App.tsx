import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ProtectedRoute } from './components/auth/ProtectedRoute'
import { AdminLayout } from './components/layout/AdminLayout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import AlertsPage from './pages/admin/AlertsPage'
import AuditLogPage from './pages/admin/AuditLogPage'
import DashboardPage from './pages/admin/DashboardPage'
import AccountsPage from './pages/admin/AccountsPage'

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
          <Route element={<AdminLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/alerts" element={<AlertsPage />} />
            <Route path="/audit" element={<AuditLogPage />} />
            <Route path="/accounts" element={<AccountsPage />} />
            <Route index element={<Navigate to="/alerts" replace />} />
            <Route path="/" element={<Navigate to="/alerts" replace />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
