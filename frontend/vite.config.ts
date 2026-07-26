import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // `e2e/` belongs to Playwright, which brings its own `test`/`expect`. Left in, Vitest picks
    // the specs up and fails on imports that only make sense inside a browser runner.
    exclude: ['node_modules/**', 'dist/**', 'e2e/**'],
  },
});
