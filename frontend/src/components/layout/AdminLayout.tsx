import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'

export function AdminLayout() {
  return (
    <div className="flex min-h-screen bg-bg">
      <Sidebar />
      <main id="main" className="flex-1 overflow-auto min-w-0">
        <Outlet />
      </main>
    </div>
  )
}
