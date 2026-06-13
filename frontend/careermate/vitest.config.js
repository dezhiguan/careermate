import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'node',
    include: ['tests/unit/**/*.test.js'],
  },
  define: {
    'import.meta.env.VITE_API_BASE_URL': '"/api"',
    'import.meta.env.DEV': 'false',
    'import.meta.env.VITE_AGENT_STREAM_TIMEOUT_MS': '300000',
  },
})
