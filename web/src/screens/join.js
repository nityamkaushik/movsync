/**
 * Join Room Screen
 */

import { ensureSignedIn, getDisplayName } from '../auth.js';
import { computeQuickFingerprint } from '../file-hasher.js';
import { joinRoom } from '../room-repository.js';
import { navigate } from '../router.js';

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

        <label class="input-label" style="margin-top:16px;">Your Movie File</label>
        <div class="file-input-wrapper">
          <button class="btn btn-outline" id="chooseFileBtn" type="button">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            Choose File
          </button>
          <span id="fileNameDisplay" class="file-name-display">No file selected</span>
          <input type="file" id="fileInput" accept="video/*" hidden />
        </div>

        <button class="btn btn-gradient join-btn" id="joinBtn" disabled>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>
          Join & Verify
        </button>

        <div id="joinProgress" style="display:none;">
          <div class="hashing-animation">
            <div class="hash-spinner"></div>
            <p class="hashing-text" id="joinProgressText">Verifying file...</p>
          </div>
        </div>

        <div id="joinError" style="display:none;">
          <div class="error-card">
            <p class="error-text" id="joinErrorText"></p>
          </div>
        </div>
      </div>
    </div>
  `;

  const codeInput = container.querySelector('#roomCodeInput');
  const joinBtn = container.querySelector('#joinBtn');
  const fileInput = container.querySelector('#fileInput');
  const fileNameDisplay = container.querySelector('#fileNameDisplay');
  let selectedFile = null;

  container.querySelector('#backBtn').addEventListener('click', () => navigate('#/'));
  container.querySelector('#chooseFileBtn').addEventListener('click', () => fileInput.click());

  fileInput.addEventListener('change', (e) => {
    selectedFile = e.target.files[0] || null;
    fileNameDisplay.textContent = selectedFile ? selectedFile.name : 'No file selected';
    joinBtn.disabled = !(codeInput.value.length === 6 && selectedFile);
  });

  codeInput.addEventListener('input', () => {
    codeInput.value = codeInput.value.replace(/[^a-zA-Z]/g, '').toUpperCase().slice(0, 6);
    joinBtn.disabled = !(codeInput.value.length === 6 && selectedFile);
    if (codeInput.value.length === 6) {
      fileInput.click();
    }
  });

  joinBtn.addEventListener('click', async () => {
    if (codeInput.value.length !== 6 || !selectedFile) return;
    await processJoin(container, codeInput.value, selectedFile);
  });

  setTimeout(() => {
    codeInput.focus();
  }, 100);
}

async function processJoin(container, code, file) {
  const progress = container.querySelector('#joinProgress');
  const errorEl = container.querySelector('#joinError');
  const joinBtn = container.querySelector('#joinBtn');

  progress.style.display = 'block';
  errorEl.style.display = 'none';
  joinBtn.disabled = true;

  try {
    const userId = await ensureSignedIn();
    const displayName = getDisplayName() || 'Movie Friend';
    const fingerprint = await computeQuickFingerprint(file);
    const result = await joinRoom(userId, displayName, code, fingerprint);

    if (result.result === 'not_found') {
      progress.style.display = 'none';
      errorEl.style.display = 'block';
      container.querySelector('#joinErrorText').textContent = 'Room not found';
      joinBtn.disabled = false;
      return;
    }

    if (result.result === 'fingerprint_mismatch') {
      progress.style.display = 'none';
      errorEl.style.display = 'block';
      container.querySelector('#joinErrorText').textContent = 'File does not match. Please select the exact movie file the host is using.';
      joinBtn.disabled = false;
      return;
    }

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
