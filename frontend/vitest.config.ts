import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    // Without an explicit URL, jsdom runs on an opaque origin and the whole
    // Storage API is simply absent: `typeof localStorage` is "undefined", not
    // a throwing stub. The client's try/catch around storage is written for
    // exactly that situation, and this is the situation.
    environmentOptions: {
      jsdom: { url: 'http://localhost:5173' },
    },
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Kept separate from the Vite build config so a test-only setting can
    // never change what ships.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
})
