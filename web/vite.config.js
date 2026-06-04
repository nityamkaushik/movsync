import { defineConfig } from 'vite';

export default defineConfig({
  // Exclude wasm files from being processed/bundled by Vite
  assetsInclude: ['**/*.wasm'],
  plugins: [
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
