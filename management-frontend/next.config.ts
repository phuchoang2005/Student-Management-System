import type { NextConfig } from 'next';

/**
 * The backend authenticates with a `JSESSIONID` session cookie and registers no CORS configuration
 * at all — `SecurityConfig` has no `.cors(...)`, no `CorsConfigurationSource`, no `@CrossOrigin`.
 * A browser on :3000 calling :8080 directly would have its preflight rejected and its cookie
 * dropped, so every request is rewritten through this dev server and stays same-origin from the
 * browser's point of view. This replaces the Vite proxy the previous frontend used, for the same
 * reason and with the same effect.
 */
const nextConfig: NextConfig = {
  async rewrites() {
    const backend = process.env.BACKEND_ORIGIN ?? 'http://localhost:8080';
    return [
      { source: '/api/:path*', destination: `${backend}/api/:path*` },
      // Spring Security's default logout endpoint, which lives outside /api/v1.
      { source: '/logout', destination: `${backend}/logout` },
    ];
  },
};

export default nextConfig;
