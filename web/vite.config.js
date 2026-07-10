import { defineConfig } from 'vite';

export default defineConfig({
  assetsInclude: ['**/*.wasm'],
  server: {
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin',
      'Cross-Origin-Embedder-Policy': 'credentialless',
    },
  },
  plugins: [
    {
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
    ],
});
