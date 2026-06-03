/**
 * Join Room Screen — Port of JoinRoomScreen.kt + JoinRoomViewModel.kt
 */

import { ensureSignedIn, getDisplayName } from '../auth.js';
import { computeQuickFingerprint } from '../file-hasher.js';
import { joinRoom } from '../room-repository.js';
import { navigate } from '../router.js';

export function renderJoin(container) {
  container.innerHTML = `
    <div class="screen-container">
      <div class="screen-header">
        <button class="btn-icon" id="backBtn">
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

        <div class="join-file-section">
          <label class="input-label">Your Movie File</label>
          <p class="join-file-desc">Select the same movie file that the host is watching</p>
          <input type="file" id="joinFileInput" accept="video/*" hidden />
          <button class="btn btn-outline" id="joinSelectFileBtn">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            Choose File
          </button>
          <div class="selected-file-info" id="joinSelectedFileInfo" style="display:none;">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#22C55E" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            <span id="joinSelectedFileName"></span>
          </div>
        </div>

        <button class="btn btn-gradient join-btn" id="joinBtn" disabled>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>
          Join & Verify
        </button>

        <!-- Progress -->
        <div id="joinProgress" style="display:none;">
          <div class="hashing-animation">
            <div class="hash-spinner"></div>
            <p class="hashing-text" id="joinProgressText">Verifying file...</p>
            <div class="hash-progress-bar">
              <div class="hash-progress-fill" id="joinProgressFill"></div>
            </div>
          </div>
        </div>

        <!-- Error -->
        <div id="joinError" style="display:none;">
          <div class="error-card">
            <p class="error-text" id="joinErrorText"></p>
          </div>
        </div>

        <!-- Mismatch -->
        <div id="joinMismatch" style="display:none;">
          <div class="error-card">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#EF4444" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            <p class="error-text">File fingerprint does not match!</p>
            <p class="error-desc">Make sure you have the exact same movie file as the host.</p>
          </div>
        </div>
      </div>
    </div>
  `;

  let selectedFile = null;
  const codeInput = container.querySelector('#roomCodeInput');
  const fileInput = container.querySelector('#joinFileInput');
  const joinBtn = container.querySelector('#joinBtn');

  container.querySelector('#backBtn').addEventListener('click', () => navigate('#/'));

  // Auto-uppercase and filter input
  codeInput.addEventListener('input', () => {
    codeInput.value = codeInput.value.replace(/[^a-zA-Z0-9]/g, '').toUpperCase().slice(0, 6);
    updateJoinBtn();
  });

  container.querySelector('#joinSelectFileBtn').addEventListener('click', () => fileInput.click());

  fileInput.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    selectedFile = file;
    container.querySelector('#joinSelectedFileInfo').style.display = 'flex';
    container.querySelector('#joinSelectedFileName').textContent = file.name;
    updateJoinBtn();
  });

  function updateJoinBtn() {
    joinBtn.disabled = !(codeInput.value.length === 6 && selectedFile);
  }

  joinBtn.addEventListener('click', async () => {
    if (!selectedFile || codeInput.value.length !== 6) return;
    await processJoin(container, codeInput.value, selectedFile);
  });
}

async function processJoin(container, code, file) {
  const progress = container.querySelector('#joinProgress');
  const errorEl = container.querySelector('#joinError');
  const mismatchEl = container.querySelector('#joinMismatch');
  const progressFill = container.querySelector('#joinProgressFill');
  const progressText = container.querySelector('#joinProgressText');

  progress.style.display = 'block';
  errorEl.style.display = 'none';
  mismatchEl.style.display = 'none';

  try {
    progressText.textContent = 'Computing fingerprint...';
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
      container.querySelector('#joinErrorText').textContent = 'Room not found';
      return;
    }

    if (result.result === 'fingerprint_mismatch') {
      progress.style.display = 'none';
      mismatchEl.style.display = 'block';
      return;
    }

    // Success!
    window.__movsync_file = file;
    window.__movsync_videoUrl = URL.createObjectURL(file);
    navigate(`#/lobby/${result.room.code}/false`);
  } catch (err) {
    console.error('Join error:', err);
    progress.style.display = 'none';
    errorEl.style.display = 'block';
    container.querySelector('#joinErrorText').textContent = err.message || 'Could not join room';
  }
}
