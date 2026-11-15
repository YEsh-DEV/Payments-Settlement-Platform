import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // All backend calls go through /api so the SPA can own client-side
      // routes (/tickets, /projects, /users, /audit-logs, ...). The /api
      // prefix is stripped before forwarding to the Spring backend.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // Perf: split large, rarely-changing vendor libs into their own chunks so
        // they cache independently of app code and stay out of the entry bundle.
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('recharts') || id.includes('/d3-') || id.includes('victory-vendor'))
            return 'charts'
          if (id.includes('@dnd-kit')) return 'dnd'
          if (id.includes('react-router')) return 'react-vendor'
          if (id.includes('/react-dom/') || id.includes('/react/') || id.includes('/scheduler/'))
            return 'react-vendor'
        },
      },
    },
  },
})
