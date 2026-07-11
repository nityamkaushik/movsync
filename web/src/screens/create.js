/**
 * Create Room Screen — Port of CreateRoomScreen.kt + CreateRoomViewModel.kt
 */

import { ensureSignedIn, getDisplayName } from '../auth.js';
import { createRoom } from '../room-repository.js';
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

let selectedFile = null;

export function renderCreate(container) {
  container.innerHTML = `
    <div class="screen-container">
      <div class="screen-header">
        <button class="btn-icon" id="backBtn">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        </button>
        <h2 class="screen-title">Create Room</h2>
      </div>

      <div class="create-content" id="createContent">
        <div class="glass-card file-picker-card">
          <div class="file-picker-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="12" y1="18" x2="12" y2="12"/>
              <line x1="9" y1="15" x2="15" y2="15"/>
            </svg>
          </div>
          <h3 class="file-picker-title">Select Your Movie File</h3>
          <p class="file-picker-desc">Choose the video file you want to watch together</p>
          <input type="file" id="fileInput" accept="video/*" hidden />
          <button class="btn btn-gradient" id="selectFileBtn">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            Choose File
          </button>
          <div class="selected-file-info" id="selectedFileInfo" style="display:none;">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#22C55E" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            <span id="selectedFileName"></span>
          </div>
        </div>

        <!-- Progress state -->
        <div id="hashingState" style="display:none;">
          <div class="glass-card">
            <div class="hashing-animation">
              <div class="hash-spinner"></div>
              <p class="hashing-text">Generating fingerprint...</p>
              <div class="hash-progress-bar">
                <div class="hash-progress-fill" id="hashProgressFill"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- Error state -->
        <div id="errorState" style="display:none;">
          <div class="glass-card error-card">
            <p class="error-text" id="errorText"></p>
            <button class="btn btn-outline" id="retryBtn">Try Again</button>
          </div>
        </div>
      </div>
    </div>
  `;

  const fileInput = container.querySelector('#fileInput');
  const selectFileBtn = container.querySelector('#selectFileBtn');

  container.querySelector('#backBtn').addEventListener('click', () => navigate('#/'));

  selectFileBtn.addEventListener('click', () => fileInput.click());

  fileInput.addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    selectedFile = file;

    // Show file name
    container.querySelector('#selectedFileInfo').style.display = 'flex';
    container.querySelector('#selectedFileName').textContent = file.name;

    // Start hashing
    await processFile(container, file);
  });

  container.querySelector('#retryBtn')?.addEventListener('click', () => {
    container.querySelector('#errorState').style.display = 'none';
    container.querySelector('#hashingState').style.display = 'none';
    fileInput.value = '';
    selectedFile = null;
    container.querySelector('#selectedFileInfo').style.display = 'none';
  });

  // Handle immediate file if passed from home screen
  if (window.__movsync_pendingCreateFile) {
    const file = window.__movsync_pendingCreateFile;
    window.__movsync_pendingCreateFile = null; // consume it
    selectedFile = file;
    
    // Show file name
    container.querySelector('#selectedFileInfo').style.display = 'flex';
    container.querySelector('#selectedFileName').textContent = file.name;
    
    // Start hashing immediately
    processFile(container, file);
  }
}

async function processFile(container, file) {
  const hashingState = container.querySelector('#hashingState');
  const errorState = container.querySelector('#errorState');
  const progressFill = container.querySelector('#hashProgressFill');

  hashingState.style.display = 'block';
  errorState.style.display = 'none';

  try {
    // Compute fingerprint
    const fingerprint = await computeQuickFingerprint(file, (progress) => {
      progressFill.style.width = `${Math.round(progress * 100)}%`;
    });

    // Get duration from video metadata
    const durationMs = await getVideoDuration(file);

    // Authenticate and create room
    container.querySelector('.hashing-text').textContent = 'Creating room...';
    const userId = await ensureSignedIn();
    const displayName = getDisplayName() || 'Movie Friend';

    const room = await createRoom(
      userId,
      displayName,
      fingerprint,
      file.name,
      durationMs
    );

    // Store file for later use in watch screen
    window.__movsync_file = file;
    window.__movsync_file = file;
    window.__movsync_videoUrl = URL.createObjectURL(file);

    // Navigate to lobby
    navigate(`#/lobby/${room.code}/true`);
  } catch (err) {
    console.error('Create room error:', err);
    hashingState.style.display = 'none';
    errorState.style.display = 'block';
    container.querySelector('#errorText').textContent = err.message || 'Could not create room';
  }
}

function getVideoDuration(file) {
  return new Promise((resolve) => {
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.onloadedmetadata = () => {
      const durationMs = Math.round(video.duration * 1000);
      URL.revokeObjectURL(video.src);
      resolve(durationMs || null);
    };
    video.onerror = () => resolve(null);
    video.src = URL.createObjectURL(file);
  });
}
