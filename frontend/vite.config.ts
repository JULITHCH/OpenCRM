import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
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
