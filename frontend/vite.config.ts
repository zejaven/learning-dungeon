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
    // can reach the dev server at http://<pc-ip>:5173. The /api proxy below still
    // targets localhost:8080, so the backend stays bound to the PC only.
    host: true,
    port: 5173,
    fs: {
      // Allow importing topic files that live outside the frontend root.
      allow: [repoRoot],
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
