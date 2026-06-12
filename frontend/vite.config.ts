import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { execSync } from 'child_process'

// In WSL2, the Windows host (where Spring Boot runs) is the default gateway.
// Fall back to localhost when running natively on Windows or Linux.
function resolveBackendHost(): string {
  if (process.env.VITE_BACKEND_URL) return process.env.VITE_BACKEND_URL
  try {
    const gw = execSync("ip route show | grep '^default' | awk '{print $3}'", { encoding: 'utf8' }).trim()
    if (gw) return `http://${gw}:8080`
  } catch { /* not WSL */ }
  return 'http://localhost:8080'
}

const backendUrl = resolveBackendHost()

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': backendUrl,
    },
    watch: {
      usePolling: true,
      interval: 300,
    },
  },
})
