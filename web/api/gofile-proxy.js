/**
 * Vercel Serverless Function — GoFile API proxy.
 *
 * Proxies lightweight JSON requests to api.gofile.io so the browser-side
 * code is never blocked by CORS.  Only the small metadata endpoints are
 * proxied (servers list, content info).  Actual file uploads go directly
 * to GoFile, and file downloads use native browser downloads.
 *
 * Usage (from the browser):
 *   GET /api/gofile-proxy?path=servers
 *   GET /api/gofile-proxy?path=contents/FILE_CODE
 */

const GOFILE_API = 'https://api.gofile.io';
const GOFILE_GUEST_TOKEN = '4fd6sg89d7s6';

export default async function handler(req, res) {
  // ---- CORS headers (every response) ----
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  if (req.method !== 'GET') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  // ---- Resolve target URL ----
  const apiPath = req.query.path;
  if (!apiPath) {
    return res.status(400).json({ error: 'Missing "path" query parameter' });
  }

  let targetUrl;
  const headers = {};

  if (apiPath === 'servers') {
    targetUrl = `${GOFILE_API}/servers`;
  } else if (apiPath.startsWith('contents/')) {
    targetUrl = `${GOFILE_API}/${apiPath}?wt=${GOFILE_GUEST_TOKEN}&cache=true`;
    headers['Authorization'] = `Bearer ${GOFILE_GUEST_TOKEN}`;
  } else {
    return res.status(400).json({ error: `Unknown API path: ${apiPath}` });
  }

  // ---- Proxy the request ----
  try {
    const upstream = await fetch(targetUrl, { headers });
    const data = await upstream.json();

    res.setHeader('Cache-Control', 'no-store');
    return res.status(upstream.status).json(data);
  } catch (err) {
    console.error('[gofile-proxy] Upstream error:', err);
    return res.status(502).json({ error: 'GoFile API request failed' });
  }
}
