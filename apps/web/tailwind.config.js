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
          50:  '#E6F6FF',
          100: '#BAE9FF',
          200: '#7DD4FF',
          300: '#40BEFF',
          400: '#17B3FF',
          500: '#02A9F7',
          600: '#0089CC',
          700: '#0069A0',
          800: '#004C74',
          900: '#002F47',
          DEFAULT: '#02A9F7',
        },
        secondary: {
          50:  '#F2FBEA',
          100: '#DFF5C8',
          200: '#C3ED9A',
          300: '#A6E46C',
          400: '#8EDD65',
          500: '#72D13A',
          600: '#56AA28',
          700: '#3D7D1C',
          800: '#265110',
          900: '#112606',
          DEFAULT: '#8EDD65',
        },
        'gray-brand': {
          mid:   '#939598',
          light: '#B7B9BC',
        },
      },
      fontFamily: {
        sans: ['"Source Sans Pro"', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
