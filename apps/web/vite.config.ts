import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import fs from 'fs'

// With pnpm node-linker=hoisted on Linux (CI), lucide-react is hoisted to the
// workspace root. On Windows it lives in apps/web/node_modules.
const lucideReact = (() => {
  const local = path.resolve(__dirname, './node_modules/lucide-react')
  return fs.existsSync(local) ? local : path.resolve(__dirname, '../../node_modules/lucide-react')
})()

export default defineConfig({
  root: path.resolve(__dirname, '.'),
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@pinsaude/ui': path.resolve(__dirname, '../../libs/frontend/src'),
      'lucide-react': lucideReact,
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true
      }
    }
  }
})
