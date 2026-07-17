import { execSync } from 'node:child_process'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Commit-Stand fuer den Footer: im Docker-/CI-Build kommt er als VITE_GIT_SHA
 * (dort gibt es kein .git), lokal aus dem Repository selbst.
 */
function resolveGitSha(): string {
  if (process.env.VITE_GIT_SHA) {
    return process.env.VITE_GIT_SHA
  }
  try {
    return execSync('git rev-parse --short=10 HEAD', { stdio: ['ignore', 'pipe', 'ignore'] })
      .toString()
      .trim()
  } catch {
    return 'dev'
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    __GIT_SHA__: JSON.stringify(resolveGitSha()),
  },
  server: {
    // Das Backend erlaubt (noch) kein CORS: im Dev-Modus werden alle /api-Aufrufe
    // auf das lokal laufende Backend proxied. Der API-Client nutzt relative Pfade,
    // VITE_API_URL greift nur fuer Produktions-Builds.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
