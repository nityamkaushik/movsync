/**
 * GoFile API client for web.
 * Handles upload, download, and URL resolution via the GoFile anonymous API.
 *
 * GoFile is a free, anonymous file hosting service — no API key or account required.
 */

/**
 * GoFile guest/anonymous website token.
 * Centralised here so it's easy to update if GoFile rotates it.
 */
const GOFILE_GUEST_TOKEN = '4fd6sg89d7s6';

/** All metadata API calls are routed through our own proxy to bypass CORS. */
const GOFILE_API_PROXY = '/api/gofile-proxy';

/**
 * Format byte counts into human-readable strings.
 * Moved here from webrtc-file-transfer.js so it survives cleanup.
 *
 * @param {number} bytes
 * @returns {string} e.g. "1.2 GB", "340 KB", "0 B"
 */
export function formatBytes(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value >= 10 || unit === 0 ? 0 : 1)} ${units[unit]}`;
}

/**
 * Get the best GoFile upload server.
 *
 * GET https://api.gofile.io/servers
 * Response: {"status":"ok","data":{"servers":[{"name":"store1","zone":"eu"}, ...]}}
 *
 * @returns {Promise<string>} Server name, e.g. "store1"
 */
export async function getGoFileServer() {
  const response = await fetch(`${GOFILE_API_PROXY}?path=servers`);
  if (!response.ok) {
    throw new Error(`GoFile servers request failed: ${response.status}`);
  }

  const json = await response.json();
  if (json.status !== 'ok' || !json.data?.servers?.length) {
    throw new Error('GoFile returned no available servers');
  }

  return json.data.servers[0].name;
}

/**
 * Upload a file to GoFile.
 *
 * Uses XMLHttpRequest instead of fetch because the Fetch API does not support
 * upload progress events. Returns a Promise that resolves with the upload result.
 *
 * @param {File} file - The file to upload
 * @param {function(number, number): void} onProgress - Progress callback (bytesUploaded, totalBytes)
 * @returns {Promise<{fileCode: string, downloadPage: string}>}
 */
export function uploadToGoFile(file, onProgress) {
  return new Promise(async (resolve, reject) => {
    let server;
    try {
      server = await getGoFileServer();
    } catch (err) {
      reject(err);
      return;
    }

    const formData = new FormData();
    formData.append('file', file);

    const xhr = new XMLHttpRequest();
    xhr.open('POST', `https://${server}.gofile.io/uploadFile`);

    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(event.loaded, event.total);
      }
    });

    xhr.addEventListener('load', () => {
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(new Error(`GoFile upload failed with status ${xhr.status}`));
        return;
      }

      try {
        const json = JSON.parse(xhr.responseText);
        if (json.status !== 'ok' || !json.data) {
          reject(new Error('GoFile upload returned unexpected response'));
          return;
        }

        resolve({
          fileCode: json.data.code || json.data.fileCode || '',
          downloadPage: json.data.downloadPage || `https://gofile.io/d/${json.data.code || json.data.fileCode}`,
        });
      } catch (err) {
        reject(new Error('Failed to parse GoFile upload response'));
      }
    });

    xhr.addEventListener('error', () => {
      reject(new Error('Network error during GoFile upload'));
    });

    xhr.addEventListener('abort', () => {
      reject(new Error('GoFile upload was cancelled'));
    });

    xhr.send(formData);
  });
}

/**
 * Resolve a GoFile content code to a direct download URL.
 *
 * GET https://api.gofile.io/contents/{code}?wt=4fd6sg89d7s6&cache=true
 * The "wt" token is GoFile's guest/anonymous website token.
 *
 * @param {string} fileCode - The file code from uploadToGoFile(), e.g. "abc123"
 * @returns {Promise<string>} Direct HTTPS download URL
 */
export async function getDirectDownloadUrl(fileCode) {
  // Auth header and wt token are injected by the proxy
  const response = await fetch(`${GOFILE_API_PROXY}?path=contents/${fileCode}`);
  if (!response.ok) {
    throw new Error(`GoFile content fetch failed: ${response.status}`);
  }

  const json = await response.json();
  if (json.status !== 'ok' || !json.data) {
    throw new Error('GoFile content returned unexpected response');
  }

  const children = json.data.children || json.data.contents;
  if (!children || typeof children !== 'object') {
    throw new Error('No files found in GoFile content');
  }

  const firstKey = Object.keys(children)[0];
  if (!firstKey) throw new Error('GoFile content has no files');

  return children[firstKey].link || children[firstKey].directLink;
}

/**
 * Download a file from a URL with progress tracking.
 *
 * Uses fetch + TransformStream to track progress while letting the browser
 * handle blob accumulation internally (disk-backed for large files).
 * This avoids the previous approach of accumulating chunks in a JS array
 * which would OOM-crash on files > 500 MB.
 *
 * @param {string} url - Direct download URL
 * @param {string} fileName - Desired file name for the resulting File object
 * @param {number} fileSize - Expected file size in bytes (used for progress if Content-Length is missing)
 * @param {function(number, number): void} onProgress - Progress callback (bytesDownloaded, totalBytes)
 * @returns {Promise<File>} The downloaded file
 */
export async function downloadFromGoFile(url, fileName, fileSize, onProgress) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Download failed with status ${response.status}`);
  }

  const contentLength = parseInt(response.headers.get('Content-Length') || '0', 10);
  const totalBytes = contentLength || fileSize || 0;
  let downloaded = 0;

  // Pipe through a TransformStream that tracks progress without
  // accumulating chunks in JS heap — the browser's internal Blob
  // storage handles buffering (disk-backed for large payloads).
  const trackingStream = new TransformStream({
    transform(chunk, controller) {
      downloaded += chunk.byteLength;
      if (onProgress) onProgress(downloaded, totalBytes);
      controller.enqueue(chunk);
    },
  });

  const trackedResponse = new Response(response.body.pipeThrough(trackingStream));
  const blob = await trackedResponse.blob();
  return new File([blob], fileName || 'movsync-video', {
    type: blob.type || response.headers.get('Content-Type') || 'video/*',
  });
}

/**
 * Trigger a native browser download for a URL.
 *
 * This bypasses CORS entirely (navigations are not subject to CORS) and
 * avoids holding the file in JS memory.  The user should then use
 * "Select File" to pick the downloaded file for verification.
 *
 * @param {string} url  - Direct download URL
 * @param {string} [fileName] - Suggested filename
 */
export function triggerNativeDownload(url, fileName) {
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName || '';
  a.target = '_blank';
  a.rel = 'noopener noreferrer';
  document.body.appendChild(a);
  a.click();
  setTimeout(() => document.body.removeChild(a), 200);
}
