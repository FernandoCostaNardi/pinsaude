/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
    '../../libs/frontend/src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50:  '#E6F7F3',
          100: '#CCEFE8',
          200: '#99DFD1',
          300: '#66CFBA',
          400: '#33BFA3',
          500: '#00A878',
          600: '#008C64',
          700: '#007050',
          800: '#00543C',
          900: '#003828',
          DEFAULT: '#00A878',
        },
        brand: {
          blue:  '#1B4FCC',
          green: '#00A878',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
