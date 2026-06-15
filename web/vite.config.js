import { defineConfig } from 'vite';

export default defineConfig({
  // Exclude wasm files from being processed/bundled by Vite
  assetsInclude: ['**/*.wasm'],
  server: {
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin',
      // Changed from 'require-corp' to 'credentialless' so that cross-origin
      // fetches (GoFile API, CDN) aren't blocked.  SharedArrayBuffer still
      // works under 'credentialless' (Chrome 96+, Firefox 119+).
      'Cross-Origin-Embedder-Policy': 'credentialless',
    },
  },
  plugins: [
    {
      // Dev-only proxy: forwards /api/gofile-proxy requests to GoFile API
      // so the browser never hits CORS.  In production, the Vercel
      // serverless function at api/gofile-proxy.js handles this.
      name: 'gofile-api-proxy',
      configureServer(server) {
        const GOFILE_API = 'https://api.gofile.io';
        const TOKEN = '4fd6sg89d7s6';

        server.middlewares.use(async (req, res, next) => {
          if (!req.url || !req.url.startsWith('/api/gofile-proxy')) return next();

          try {
            const url = new URL(req.url, 'http://localhost');
            const apiPath = url.searchParams.get('path');
            if (!apiPath) {
              res.writeHead(400, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ error: 'Missing path' }));
              return;
            }

            let targetUrl;
            const headers = {};

            if (apiPath === 'servers') {
              targetUrl = `${GOFILE_API}/servers`;
            } else if (apiPath.startsWith('contents/')) {
              targetUrl = `${GOFILE_API}/${apiPath}?wt=${TOKEN}&cache=true`;
              headers['Authorization'] = `Bearer ${TOKEN}`;
            } else {
              res.writeHead(400, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ error: 'Unknown path' }));
              return;
            }

            const upstream = await fetch(targetUrl, { headers });
            const data = await upstream.text();
            res.writeHead(upstream.status, { 'Content-Type': 'application/json' });
            res.end(data);
          } catch (err) {
            res.writeHead(502, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Proxy failed' }));
          }
        });
      },
    },
    {
      // Custom plugin: ensure /libav/* wasm files get correct MIME type
      name: 'libav-static-serve',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url && req.url.startsWith('/libav/') && req.url.endsWith('.wasm')) {
            res.setHeader('Content-Type', 'application/wasm');
          }
          next();
        });
      },
    },
  ],
});
