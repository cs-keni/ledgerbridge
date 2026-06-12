import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: '#111111',
        surface: '#1a1a1a',
        sidebar: '#161616',
        border: '#2a2a2a',
        muted: '#888888',
        text: '#f0f0f0',
        accent: '#6366f1',
        'accent-light': '#818cf8',
        critical: '#dc2626',
        high: '#ea580c',
        medium: '#d97706',
        low: '#16a34a',
        positive: '#34d399',
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
        mono: ['Geist Mono', 'JetBrains Mono', 'monospace'],
      },
      spacing: {
        sidebar: '160px',
      },
      keyframes: {
        shake: {
          '0%, 100%': { transform: 'translateX(0)' },
          '15%': { transform: 'translateX(-6px)' },
          '30%': { transform: 'translateX(6px)' },
          '45%': { transform: 'translateX(-4px)' },
          '60%': { transform: 'translateX(4px)' },
          '75%': { transform: 'translateX(-2px)' },
          '90%': { transform: 'translateX(2px)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        'fade-in-up': {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        'slide-in-right': {
          from: { transform: 'translateX(380px)' },
          to: { transform: 'translateX(0)' },
        },
        'alert-arrive': {
          from: { opacity: '0', transform: 'translateY(-4px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        shake: 'shake 0.4s ease-in-out',
        shimmer: 'shimmer 1.5s linear infinite',
        'fade-in-up': 'fade-in-up 300ms ease-out',
        'fade-in': 'fade-in 150ms ease-out',
        'slide-in-right': 'slide-in-right 300ms ease-out',
        'alert-arrive': 'alert-arrive 250ms ease-out',
      },
    },
  },
  plugins: [],
} satisfies Config
