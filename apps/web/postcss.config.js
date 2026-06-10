import { fileURLToPath } from 'url'
import path from 'path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const configPath = path.join(__dirname, 'tailwind.config.js').replace(/\\/g, '/')

export default {
  plugins: {
    tailwindcss: { config: configPath },
    autoprefixer: {},
  },
}
