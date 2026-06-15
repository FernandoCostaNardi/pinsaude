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
          50:  '#E5F7FD',
          100: '#BAE9F9',
          200: '#80D5F5',
          300: '#40BFEF',
          400: '#17B2F0',
          500: '#00AEEF',
          600: '#0090C8',
          700: '#0072A0',
          800: '#00527A',
          900: '#003552',
          DEFAULT: '#00AEEF',
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
        // Design system tokens (from Corvi Design / Empresas.html)
        ds: {
          bg:     '#F0F3F8',
          border: '#EDF0F5',
          card:   '#FFFFFF',
          input:  '#F7F9FB',
          hover:  '#F5FAFD',
          text:   '#172033',
          mid:    '#4B5E78',
          light:  '#8A9BB0',
          nav:    '#7A8A9E',
        },
      },
      fontFamily: {
        sans: ['"Source Sans 3"', '"Source Sans Pro"', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
