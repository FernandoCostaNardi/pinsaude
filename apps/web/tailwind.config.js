import { fileURLToPath } from 'url'
import path from 'path'

const dir = path.dirname(fileURLToPath(import.meta.url)).replace(/\\/g, '/')

/** @type {import('tailwindcss').Config} */
export default {
  content: [
    `${dir}/index.html`,
    `${dir}/src/**/*.{js,ts,jsx,tsx}`,
    `${dir}/../../libs/frontend/src/**/*.{js,ts,jsx,tsx}`,
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50:  '#E0F6FF',
          100: '#B3E9FF',
          200: '#80D8FF',
          300: '#4DC7FF',
          400: '#1AB7FF',
          500: '#00AEEF',
          600: '#008EC5',
          700: '#006E9B',
          800: '#004F70',
          900: '#002F45',
          DEFAULT: '#00AEEF',
        },
      },
      fontFamily: {
        sans: ['Poppins', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
