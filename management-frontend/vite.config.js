import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The backend authenticates with a JSESSIONID session cookie and registers no CORS configuration
// at all (SecurityConfig has no .cors(...), no CorsConfigurationSource, no @CrossOrigin). A browser
// on :5173 calling :8080 directly would have its preflight rejected and its cookie dropped, so every
// request goes through this proxy instead and stays same-origin from the browser's point of view.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      // Spring Security's default logout endpoint, which lives outside /api/v1 (§4.3).
      '/logout': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
