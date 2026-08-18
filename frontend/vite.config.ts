import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'node:path';

const repoRoot = path.resolve(__dirname, '..');

/**
 * Runtime cache the read-only API lands in. The app writes into the same cache
 * when you download a domain for offline use (see src/engine/offline/cache.ts),
 * so this name is shared — change it in both places or the prefetch silently
 * fills a cache nothing reads.
 */
const API_CACHE = 'jid-api';

/**
 * NOTE for the runtimeCaching matchers below: workbox-build inlines these
 * functions into the generated worker as source text. Anything they reference
 * from this module is NOT carried over, and the route then throws at match time
 * and silently stops serving from the cache — so each matcher must be entirely
 * self-contained (that is why the "never cache" pattern is a literal inside the
 * function instead of a shared constant).
 *
 * Never cached: streams (SSE) and live status whose stale value would be a lie
 * (usage meter, self-update capabilities, bulk-run progress).
 */

const apiProxy = {
  '/api': {
    // 127.0.0.1, not localhost: the backend listens on the IPv4 loopback
    // only, and Node resolves localhost to ::1 first.
    target: 'http://127.0.0.1:18080',
    changeOrigin: true,
    // Pass the real client on: the proxy itself connects over loopback, so
    // without this every phone hitting the dev server would look local to
    // RemoteAccessFilter. Only honoured when app.remote.mode=proxied.
    xfwd: true,
  },
};

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icons/apple-touch-icon.png'],
      manifest: {
        name: 'Java Interview Dungeon',
        short_name: 'Dungeon',
        description: 'Interview prep as micro-action lessons, offline-capable.',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#0d1117',
        theme_color: '#0d1117',
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          {
            src: 'icons/icon-maskable-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        // The app shell. Mermaid ships a few chunks over the 2 MiB default.
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
        navigateFallback: 'index.html',
        // `?token=...` is the one navigation that MUST reach the backend: it is
        // how RemoteAccessFilter hands out the cookie. Served from the precache
        // instead, the page would load and then 401 on every API call.
        // (NavigationRoute matches its denylist against pathname + search.)
        navigateFallbackDenylist: [/^\/api\//, /[?&]token=/],
        cleanupOutdatedCaches: true,
        runtimeCaching: [
          {
            // Explanation images (ndm topics): bytes that never change per URL.
            urlPattern: ({ url }) =>
              url.pathname.startsWith('/api/topics/') && url.pathname.includes('/assets/'),
            handler: 'CacheFirst',
            options: {
              cacheName: 'jid-topic-assets',
              expiration: { maxEntries: 400, maxAgeSeconds: 60 * 60 * 24 * 90 },
              cacheableResponse: { statuses: [200] },
            },
          },
          {
            // Everything else the lesson/review/theory screens read.
            urlPattern: ({ url, request }) =>
              request.method === 'GET'
              && url.pathname.startsWith('/api/')
              && !/^\/api\/(assistant|usage|system|bulk|ai)\/|^\/api\/topics\/generate\//.test(
                url.pathname,
              ),
            handler: 'NetworkFirst',
            options: {
              cacheName: API_CACHE,
              // Fall back to the stored copy rather than hang on a dead link.
              networkTimeoutSeconds: 5,
              // Never store a 401/403 from RemoteAccessFilter as if it were data.
              cacheableResponse: { statuses: [200] },
            },
          },
        ],
      },
    }),
  ],
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
    // targets 127.0.0.1:18080, so the backend stays bound to the PC only.
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
    proxy: apiProxy,
  },
  // `npm run preview` serves the built bundle WITH the generated service
  // worker, which is the only way to exercise the offline behaviour locally.
  preview: {
    host: true,
    port: 4173,
    strictPort: true,
    proxy: apiProxy,
  },
});
