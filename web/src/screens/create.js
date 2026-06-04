/**
 * Create Room Screen — Port of CreateRoomScreen.kt + CreateRoomViewModel.kt
 */

import { ensureSignedIn, getDisplayName } from '../auth.js';
import { computeQuickFingerprint } from '../file-hasher.js';
import { createRoom } from '../room-repository.js';
import { navigate } from '../router.js';

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
