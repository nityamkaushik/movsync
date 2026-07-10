const API_BASE = 'https://storage.to/api';

function visitorToken() {
  let token = sessionStorage.getItem('storageto_visitor_token');
  if (!token) {
    token = crypto.randomUUID();
    sessionStorage.setItem('storageto_visitor_token', token);
  }
  return token;
}

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

export async function uploadToStorageTo(file, onProgress) {
  const token = visitorToken();

  const initResponse = await fetch(`${API_BASE}/upload/init`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Visitor-Token': token,
    },
    body: JSON.stringify({
      filename: file.name,
      content_type: file.type || 'application/octet-stream',
      size: file.size,
    }),
  });

  if (!initResponse.ok) {
    const err = await initResponse.json().catch(() => ({}));
    throw new Error(err.error || `Init failed (${initResponse.status})`);
  }

  const initJson = await initResponse.json();
  if (!initJson.success) throw new Error(initJson.error || 'Init failed');

  const uploadType = initJson.type;
  const r2Key = initJson.r2_key;

  if (uploadType === 'single') {
    await uploadSingle(initJson.upload_url, file, onProgress);
  } else if (uploadType === 'multipart') {
    await uploadMultipart(file, initJson, onProgress, token);
  } else {
    throw new Error(`Unknown upload type: ${uploadType}`);
  }

  const confirmResponse = await fetch(`${API_BASE}/upload/confirm`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Visitor-Token': token,
    },
    body: JSON.stringify({
      filename: file.name,
      size: file.size,
      content_type: file.type || 'application/octet-stream',
      r2_key: r2Key,
    }),
  });

  if (!confirmResponse.ok) {
    const err = await confirmResponse.json().catch(() => ({}));
    throw new Error(err.error || `Confirm failed (${confirmResponse.status})`);
  }

  const confirmJson = await confirmResponse.json();
  if (!confirmJson.success) throw new Error(confirmJson.error || 'Confirm failed');

  const f = confirmJson.file;
  return {
    fileId: f.id,
    shareUrl: f.url,
    rawUrl: f.raw_url,
  };
}

function uploadSingle(uploadUrl, file, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', uploadUrl);
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');

    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(event.loaded, event.total);
      }
    });

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`R2 upload failed (${xhr.status})`));
      }
    });

    xhr.addEventListener('error', () => reject(new Error('Network error during upload')));
    xhr.addEventListener('abort', () => reject(new Error('Upload cancelled')));
    xhr.send(file);
  });
}

async function uploadMultipart(file, initJson, onProgress, token) {
  const CONCURRENCY = 4;
  const uploadId = initJson.upload_id;
  const partSize = initJson.part_size;
  const totalParts = initJson.total_parts;
  const initialUrls = initJson.initial_urls || {};
  const ownerToken = initJson.owner_token || '';

  const partUrls = {};
  for (let i = 1; i <= totalParts; i++) {
    if (initialUrls[i]) {
      partUrls[i] = initialUrls[i];
    } else {
      partUrls[i] = await getPartUrl(uploadId, i, ownerToken, token);
    }
  }

  let completedBytes = 0;
  const parts = [];

  for (let batchStart = 1; batchStart <= totalParts; batchStart += CONCURRENCY) {
    const batchEnd = Math.min(batchStart + CONCURRENCY - 1, totalParts);
    const batch = [];

    for (let partNumber = batchStart; partNumber <= batchEnd; partNumber++) {
      const start = (partNumber - 1) * partSize;
      const end = Math.min(start + partSize, file.size);
      const blob = file.slice(start, end);
      const url = partUrls[partNumber];

      batch.push(
        uploadPart(url, blob, partNumber, start, file.size, onProgress)
          .then(etag => {
            completedBytes += (end - start);
            onProgress(completedBytes, file.size);
            return { partNumber, etag };
          })
      );
    }

    const results = await Promise.all(batch);
    parts.push(...results);
  }

  const completeResponse = await fetch(`${API_BASE}/upload/complete-multipart`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Owner ${ownerToken}`,
    },
    body: JSON.stringify({ upload_id: uploadId, parts }),
  });

  if (!completeResponse.ok) {
    const err = await completeResponse.json().catch(() => ({}));
    throw new Error(err.error || `Complete multipart failed (${completeResponse.status})`);
  }

  const completeJson = await completeResponse.json();
  if (!completeJson.success) throw new Error(completeJson.error || 'Complete multipart failed');
}

function getPartUrl(uploadId, partNumber, ownerToken, visitorToken) {
  return fetch(`${API_BASE}/upload/parts`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Owner ${ownerToken}`,
    },
    body: JSON.stringify({ upload_id: uploadId, part_numbers: [partNumber] }),
  })
    .then(r => r.json())
    .then(json => {
      if (!json.success) throw new Error(json.error || 'Get part URL failed');
      return json.part_urls[0].url;
    });
}

function uploadPart(url, blob, partNumber, startOffset, totalSize, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    xhr.setRequestHeader('Content-Type', 'application/octet-stream');

    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(startOffset + event.loaded, totalSize);
      }
    });

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        const etag = xhr.getResponseHeader('ETag') || '';
        resolve(etag);
      } else {
        reject(new Error(`R2 part ${partNumber} upload failed (${xhr.status})`));
      }
    });

    xhr.addEventListener('error', () => reject(new Error(`Network error uploading part ${partNumber}`)));
    xhr.addEventListener('abort', () => reject(new Error(`Part ${partNumber} upload cancelled`)));
    xhr.send(blob);
  });
}

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
