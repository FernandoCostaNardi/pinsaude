import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  root: path.resolve(__dirname, '.'),
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@pinsaude/ui': path.resolve(__dirname, '../../libs/frontend/src'),
      // libs/frontend importa lucide-react — aponta para o node_modules de apps/web
      'lucide-react': path.resolve(__dirname, './node_modules/lucide-react'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
