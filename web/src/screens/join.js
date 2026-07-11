/**
 * Join Room Screen
 */

import { ensureSignedIn, getDisplayName } from '../auth.js';
import { joinRoom } from '../room-repository.js';
import { navigate } from '../router.js';

// ===== Incremental SHA-256 (matches Android FileHasher.kt) =====

class SHA256 {
  constructor() {
    this._h = new Uint32Array([
      0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
      0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    ]);
    this._block = new Uint8Array(64);
    this._blockOffset = 0;
    this._length = 0;
    this._finalized = false;
  }

  update(data) {
    if (this._finalized) throw new Error('Digest already finalized');
    let bytes;
    if (data instanceof ArrayBuffer) {
      bytes = new Uint8Array(data);
    } else if (data instanceof Uint8Array) {
      bytes = data;
    } else if (typeof data === 'string') {
      bytes = new TextEncoder().encode(data);
    } else {
      throw new Error('Unsupported data type');
    }

    for (let i = 0; i < bytes.length; i++) {
      this._block[this._blockOffset++] = bytes[i];
      this._length += 8;
      if (this._blockOffset === 64) {
        this._compress();
        this._blockOffset = 0;
      }
    }
    return this;
  }

  hex() {
    if (!this._finalized) {
      this._finalized = true;
      this._block[this._blockOffset++] = 0x80;
      if (this._blockOffset > 56) {
        while (this._blockOffset < 64) this._block[this._blockOffset++] = 0;
        this._compress();
        this._blockOffset = 0;
      }
      while (this._blockOffset < 56) this._block[this._blockOffset++] = 0;
      const lenHi = Math.floor(this._length / 0x100000000);
      const lenLo = this._length >>> 0;
      this._block[56] = (lenHi >>> 24) & 0xff;
      this._block[57] = (lenHi >>> 16) & 0xff;
      this._block[58] = (lenHi >>> 8) & 0xff;
      this._block[59] = lenHi & 0xff;
      this._block[60] = (lenLo >>> 24) & 0xff;
      this._block[61] = (lenLo >>> 16) & 0xff;
      this._block[62] = (lenLo >>> 8) & 0xff;
      this._block[63] = lenLo & 0xff;
      this._compress();
    }
    let hex = '';
    for (let i = 0; i < 8; i++) {
      hex += ('00000000' + (this._h[i] >>> 0).toString(16)).slice(-8);
    }
    return hex;
  }

  _compress() {
    const K = SHA256._K;
    const w = new Uint32Array(64);
    const block = this._block;

    for (let i = 0; i < 16; i++) {
      w[i] = (block[i * 4] << 24) | (block[i * 4 + 1] << 16) |
             (block[i * 4 + 2] << 8) | block[i * 4 + 3];
    }
    for (let i = 16; i < 64; i++) {
      const s0 = _rotr(w[i - 15], 7) ^ _rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
      const s1 = _rotr(w[i - 2], 17) ^ _rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0;
    }

    let [a, b, c, d, e, f, g, h] = this._h;
    for (let i = 0; i < 64; i++) {
      const S1 = _rotr(e, 6) ^ _rotr(e, 11) ^ _rotr(e, 25);
      const ch = (e & f) ^ (~e & g);
      const temp1 = (h + S1 + ch + K[i] + w[i]) | 0;
      const S0 = _rotr(a, 2) ^ _rotr(a, 13) ^ _rotr(a, 22);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const temp2 = (S0 + maj) | 0;
      h = g; g = f; f = e; e = (d + temp1) | 0;
      d = c; c = b; b = a; a = (temp1 + temp2) | 0;
    }

    this._h[0] = (this._h[0] + a) | 0;
    this._h[1] = (this._h[1] + b) | 0;
    this._h[2] = (this._h[2] + c) | 0;
    this._h[3] = (this._h[3] + d) | 0;
    this._h[4] = (this._h[4] + e) | 0;
    this._h[5] = (this._h[5] + f) | 0;
    this._h[6] = (this._h[6] + g) | 0;
    this._h[7] = (this._h[7] + h) | 0;
  }
}

function _rotr(x, n) {
  return ((x >>> n) | (x << (32 - n))) >>> 0;
}

SHA256._K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
  0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
  0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
  0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
  0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
  0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
  0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
  0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
]);

const CHUNK_SIZE = 4 * 1024 * 1024;

async function computeQuickFingerprint(file, onProgress) {
  const fileSize = file.size;
  if (fileSize <= 0) throw new Error('Cannot determine file size');

  const digest = new SHA256();
  let totalRead = 0;
  const totalToRead = _computeTotalBytesToRead(fileSize);

  const headEnd = Math.min(CHUNK_SIZE, fileSize);
  const headData = await readSlice(file, 0, headEnd);
  digest.update(headData);
  totalRead += headData.byteLength;
  if (onProgress) onProgress(totalRead / totalToRead);

  if (fileSize > CHUNK_SIZE * 2) {
    const middle = Math.floor(fileSize / 2) - Math.floor(CHUNK_SIZE / 2);
    const middleData = await readSlice(file, middle, middle + CHUNK_SIZE);
    digest.update(middleData);
    totalRead += middleData.byteLength;
    if (onProgress) onProgress(totalRead / totalToRead);
  }

  if (fileSize > CHUNK_SIZE) {
    const tailStart = fileSize - CHUNK_SIZE;
    const tailData = await readSlice(file, tailStart, fileSize);
    digest.update(tailData);
    totalRead += tailData.byteLength;
    if (onProgress) onProgress(totalRead / totalToRead);
  }

  digest.update(fileSize.toString());

  if (onProgress) onProgress(1);
  return digest.hex();
}

function _computeTotalBytesToRead(fileSize) {
  let total = Math.min(CHUNK_SIZE, fileSize);
  if (fileSize > CHUNK_SIZE * 2) total += CHUNK_SIZE;
  if (fileSize > CHUNK_SIZE) total += CHUNK_SIZE;
  return total;
}

function readSlice(file, start, end) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(new Uint8Array(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(file.slice(start, end));
  });
}

export function renderJoin(container) {
  container.innerHTML = `
    <div class="screen-container">
      <div class="screen-header">
        <button class="btn-icon" id="backBtn" aria-label="Back">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        </button>
        <h2 class="screen-title">Join Room</h2>
      </div>

      <div class="glass-card join-card">
        <label class="input-label" for="roomCodeInput">Room Code</label>
        <div class="room-code-input-wrapper">
          <input
            type="text"
            id="roomCodeInput"
            class="input-field room-code-input"
            placeholder="Enter 6-char code"
            maxlength="6"
            autocomplete="off"
            spellcheck="false"
          />
        </div>

        <button class="btn btn-gradient join-btn" id="joinBtn" disabled>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>
          Join Room
        </button>

        <div id="joinProgress" style="display:none;">
          <div class="hashing-animation">
            <div class="hash-spinner"></div>
            <p class="hashing-text" id="joinProgressText">Joining room...</p>
            <div class="hash-progress-bar" id="joinProgressBar" style="display:none;">
              <div class="hash-progress-fill" id="joinProgressFill"></div>
            </div>
          </div>
        </div>

        <div id="joinError" style="display:none;">
          <div class="error-card">
            <p class="error-text" id="joinErrorText"></p>
          </div>
        </div>
      </div>
    </div>
    <input type="file" id="joinFileInput" accept="video/*" style="display:none" />
  `;

  const codeInput = container.querySelector('#roomCodeInput');
  const joinBtn = container.querySelector('#joinBtn');

  container.querySelector('#backBtn').addEventListener('click', () => navigate('#/'));

  codeInput.addEventListener('input', () => {
    codeInput.value = codeInput.value.replace(/[^a-zA-Z]/g, '').toUpperCase().slice(0, 6);
    
    container.querySelector('#joinError').style.display = 'none';
    const isJoining = container.querySelector('#joinProgress').style.display === 'block';
    
    if (!isJoining) {
      joinBtn.disabled = codeInput.value.length !== 6;
      if (codeInput.value.length === 6) {
        processJoin(container, codeInput.value);
      }
    }
  });

  joinBtn.addEventListener('click', async () => {
    if (codeInput.value.length !== 6) return;
    await processJoin(container, codeInput.value);
  });

  // Auto-focus input so keyboard opens on mobile / cursor is ready on desktop
  setTimeout(() => {
    codeInput.focus();
  }, 100);
}

async function processJoin(container, code) {
  const progress = container.querySelector('#joinProgress');
  const progressText = container.querySelector('#joinProgressText');
  const progressBar = container.querySelector('#joinProgressBar');
  const progressFill = container.querySelector('#joinProgressFill');
  const errorEl = container.querySelector('#joinError');
  const joinBtn = container.querySelector('#joinBtn');

  progress.style.display = 'block';
  errorEl.style.display = 'none';
  joinBtn.disabled = true;

  try {
    const file = await new Promise((resolve) => {
      const input = container.querySelector('#joinFileInput');
      input.value = '';
      input.onchange = () => resolve(input.files[0] || null);
      input.click();
    });

    if (!file) {
      progress.style.display = 'none';
      joinBtn.disabled = false;
      return;
    }

    progressText.textContent = 'Verifying file...';
    progressBar.style.display = 'block';

    const fingerprint = await computeQuickFingerprint(file, (p) => {
      progressFill.style.width = `${Math.round(p * 100)}%`;
    });

    progressText.textContent = 'Joining room...';

    const userId = await ensureSignedIn();
    const displayName = getDisplayName() || 'Movie Friend';
    const result = await joinRoom(userId, displayName, code, fingerprint);

    if (result.result === 'not_found') {
      progress.style.display = 'none';
      errorEl.style.display = 'block';
      container.querySelector('#joinErrorText').textContent = 'Invalid room code';
      joinBtn.disabled = false;
      return;
    }

    if (result.result === 'fingerprint_mismatch') {
      progress.style.display = 'none';
      errorEl.style.display = 'block';
      container.querySelector('#joinErrorText').textContent = 'The selected file does not match the room\'s movie. Please select the exact same file the host is using.';
      joinBtn.disabled = false;
      return;
    }

    if (window.__movsync_videoUrl) URL.revokeObjectURL(window.__movsync_videoUrl);
    window.__movsync_file = file;
    window.__movsync_videoUrl = URL.createObjectURL(file);

    navigate(`#/lobby/${result.room.code}/false`);
  } catch (err) {
    console.error('Join error:', err);
    progress.style.display = 'none';
    errorEl.style.display = 'block';
    container.querySelector('#joinErrorText').textContent = err.message || 'Could not join room';
    joinBtn.disabled = false;
  }
}
