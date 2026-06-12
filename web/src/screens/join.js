/**
 * Join Room Screen
 */

import { ensureSignedIn, getDisplayName } from '../auth.js';
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

        <button class="btn btn-gradient join-btn" id="joinBtn" disabled>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>
          Join Room
        </button>

        <div id="joinProgress" style="display:none;">
          <div class="hashing-animation">
            <div class="hash-spinner"></div>
            <p class="hashing-text" id="joinProgressText">Joining room...</p>
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

  container.querySelector('#backBtn').addEventListener('click', () => navigate('#/'));

  codeInput.addEventListener('input', () => {
    codeInput.value = codeInput.value.replace(/[^a-zA-Z]/g, '').toUpperCase().slice(0, 6);
    joinBtn.disabled = codeInput.value.length !== 6;
  });

  joinBtn.addEventListener('click', async () => {
    if (codeInput.value.length !== 6) return;
    await processJoin(container, codeInput.value);
  });
}

async function processJoin(container, code) {
  const progress = container.querySelector('#joinProgress');
  const errorEl = container.querySelector('#joinError');
  const joinBtn = container.querySelector('#joinBtn');

  progress.style.display = 'block';
  errorEl.style.display = 'none';
  joinBtn.disabled = true;

  try {
    const userId = await ensureSignedIn();
    const displayName = getDisplayName() || 'Movie Friend';
    const result = await joinRoom(userId, displayName, code);

    if (result.result === 'not_found') {
      progress.style.display = 'none';
      errorEl.style.display = 'block';
      container.querySelector('#joinErrorText').textContent = 'Room not found';
      joinBtn.disabled = false;
      return;
    }

    navigate(`#/lobby/${result.room.code}/false`);
  } catch (err) {
    console.error('Join error:', err);
    progress.style.display = 'none';
    errorEl.style.display = 'block';
    container.querySelector('#joinErrorText').textContent = err.message || 'Could not join room';
    joinBtn.disabled = false;
  }
}
