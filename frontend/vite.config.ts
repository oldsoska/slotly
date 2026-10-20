import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  // Served by Spring Boot under /app, next to the API, so production needs no CORS
  base: '/app/',
  plugins: [react()],
  server: {
    port: 5173,
    // In development the API runs separately; proxying keeps requests same-origin
    proxy: {
      '/api': 'http://localhost:8080',
      // the public booking page the dashboard links to is served by the backend too
      '/b/': 'http://localhost:8080',
      '/booking/': 'http://localhost:8080',
    },
  },
  build: {
    // Lands in the backend's static resources; the Docker build overrides it with --outDir
    outDir: '../backend/src/main/resources/static/app',
    emptyOutDir: true,
  },
  test: {
    environment: 'node',
  },
});
