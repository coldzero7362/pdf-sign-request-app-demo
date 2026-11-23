import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발 중에는 /api 등 백엔드 경로를 Spring Boot(:8080)로 넘긴다.
const backend = process.env.BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    proxy: {
      '/api': backend,
      '/oauth2': backend,
      '/login': backend,
      '/logout': backend,
      '/sign/api': backend,
    },
  },
})
