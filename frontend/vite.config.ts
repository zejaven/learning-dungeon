import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

const repoRoot = path.resolve(__dirname, '..');

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // Topic visualizers (which live in ../topics) and app code both import
      // primitives/engine via this stable alias.
      '@app': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    // Listen on 0.0.0.0 so other devices on the LAN (e.g. a VR headset browser)
    // can reach the dev server at http://<pc-ip>:15173. The /api proxy below still
    // targets localhost:18080, so the backend stays bound to the PC only.
    host: true,
    // Non-default ports so other local projects on 8080/5173 don't collide.
    // strictPort: fail loudly instead of drifting to the next free port, which
    // would silently fall outside the backend's CORS allowlist (WebConfig).
    port: 15173,
    strictPort: true,
    fs: {
      // Allow importing topic files that live outside the frontend root.
      allow: [repoRoot],
    },
    proxy: {
      '/api': {
        target: 'http://localhost:18080',
        changeOrigin: true,
      },
    },
  },
});
