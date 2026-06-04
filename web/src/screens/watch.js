/**
 * Watch Screen — Port of WatchScreen.kt + WatchViewModel.kt
 * Full-screen video player with sync overlay, chat, and keyboard controls.
 */

import { navigate } from '../router.js';
import { ensureSignedIn, getDisplayName } from '../auth.js';
import * as syncEngine from '../sync-engine.js';
import * as firebaseSync from '../firebase-sync.js';
import { leaveRoom, getRoomByCode } from '../room-repository.js';
import { createChatUI } from '../components/chat.js';

let userId = null;
let roomData = null;
let videoElement = null;
let controlsTimeout = null;
let showControls = true;
let showChat = false;
let showSettings = false;
let lastReadCount = 0;
let currentMessages = [];
let unsubChat = null;
let unsubAllowControls = null;
let allowControls = false;
let syncStatus = 'synced';
let progressInterval = null;
let subtitleCues = [];
let subtitlesEnabled = false;
let currentSubtitle = '';

// MKV Integration Variables
let libavWorker = null;
let jassub = null;
let audioCtx = null;
let audioFileBlob = null;

export function renderWatch(container, { code, isHost }) {
  const isHostBool = isHost === 'true';
  const videoUrl = window.__movsync_videoUrl;

  if (!videoUrl) {
    container.innerHTML = `
      <div class="screen-container" style="text-align:center; padding-top:100px;">
        <h2>No video file selected</h2>
        <p>Please create or join a room first.</p>
        <button class="btn btn-gradient" onclick="location.hash='#/'">Go Home</button>
      </div>
    `;
    return;
  }

  container.innerHTML = `
    <div class="watch-screen" id="watchScreen">
      <video id="videoPlayer" src="${videoUrl}" preload="auto"></video>

      <!-- Subtitle Overlay -->
      <div class="subtitle-overlay" id="subtitleOverlay"></div>
      <canvas id="jassubCanvas" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 10;"></canvas>

      <!-- Unread Indicator (when controls hidden) -->
      <div class="video-unread-dot" id="videoUnreadDot" style="display:none;"></div>

      <!-- Controls Overlay -->
      <div class="watch-overlay" id="watchOverlay">
        <!-- Top Bar -->
        <div class="watch-top-bar">
          <div class="watch-top-left">
            <div class="sync-chip" id="syncChip">
              <span class="sync-dot synced"></span>
              <span id="syncText">Synced</span>
            </div>
            ${isHostBool ? `
              <button class="controls-toggle-chip" id="toggleControlsBtn">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" id="lockIcon"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                <span id="controlsLabel">Controls: Host Only</span>
              </button>
            ` : ''}
          </div>
          <div class="watch-top-right">
            <!-- Subtitle/Settings toggle -->
            <button class="btn-icon-watch" id="subtitleToggleBtn" title="Audio & Subtitles">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="14" x2="23" y2="14"/><line x1="5" y1="18" x2="19" y2="18"/></svg>
            </button>
            <button class="btn-icon-watch" id="chatToggleBtn" title="Chat">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
              <span class="chat-badge" id="chatBadge" style="display:none;">!</span>
            </button>
            <button class="btn-icon-watch" id="leaveBtn" title="Leave">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
        </div>

        <!-- Settings Panel -->
        <div class="settings-panel" id="settingsPanel" style="display:none;">
          <div class="settings-card glass-card">
            <h4 class="settings-title">Audio & Subtitles</h4>

            <div class="settings-section">
              <label class="settings-label">Subtitle Track</label>
              <select class="settings-select" id="subtitleTrackSelect">
                <option value="off">Off</option>
              </select>
              <p class="settings-hint" id="subtitleHint">Detecting embedded tracks...</p>
            </div>

            <div class="settings-section">
              <label class="settings-label">Load External Subtitle</label>
              <input type="file" id="subtitleFileInput" accept=".srt,.vtt,.ass,.ssa" hidden />
              <div class="settings-row">
                <button class="btn btn-outline settings-btn" id="loadSubtitleBtn">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                  Load .srt / .vtt file
                </button>
                <span class="subtitle-status" id="subtitleStatus"></span>
              </div>
            </div>

            <div class="settings-section">
              <label class="settings-label">Audio Track</label>
              <select class="settings-select" id="audioTrackSelect">
                <option value="default">Default</option>
              </select>
              <p class="settings-hint" id="audioHint">Detecting embedded tracks...</p>
            </div>
          </div>
        </div>

        <!-- Center Play/Pause -->
        <div class="watch-center">
          <button class="center-play-btn" id="centerPlayBtn">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" id="centerPlayIcon"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          </button>
        </div>

        <!-- Bottom Bar -->
        <div class="watch-bottom-bar">
          <button class="btn-icon-watch" id="bottomPlayBtn">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" id="bottomPlayIcon"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          </button>
          <div class="seek-bar-container">
            <input type="range" class="seek-bar" id="seekBar" min="0" max="1000" value="0" step="1" />
            <div class="seek-bar-fill" id="seekBarFill"></div>
          </div>
          <span class="time-display" id="timeDisplay">0:00 / 0:00</span>
          <div class="volume-control">
            <button class="btn-icon-watch" id="volumeBtn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/></svg>
            </button>
            <input type="range" class="volume-slider" id="volumeSlider" min="0" max="100" value="100" />
          </div>
        </div>
      </div>

      <!-- Chat Panel -->
      <div class="watch-chat-panel" id="watchChatPanel" style="display:none;">
        <div id="watchChatContainer"></div>
      </div>

      <!-- Leave Confirmation -->
      <div class="modal-overlay" id="leaveModal" style="display:none;">
        <div class="modal-card glass-card">
          <h3>Leave watch session?</h3>
          <p>Playback sync will stop on this device.</p>
          <div class="modal-actions">
            <button class="btn btn-outline" id="stayBtn">Stay</button>
            <button class="btn btn-danger" id="confirmLeaveBtn">Leave</button>
          </div>
        </div>
      </div>
    </div>
  `;

  videoElement = container.querySelector('#videoPlayer');

  init(container, code, isHostBool);
  setupEventListeners(container, code, isHostBool);

  return () => cleanup(code);
}

async function init(container, roomCode, isHost) {
  audioFileBlob = window.__movsync_file;
  
  if (audioFileBlob && audioFileBlob.name.toLowerCase().endsWith('.mkv')) {
    libavWorker = new Worker('/libav-worker.js');
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    
    const canvas = container.querySelector('#jassubCanvas');
    if (window.JASSUB) {
      jassub = new window.JASSUB({
        video: videoElement,
        canvas: canvas,
        workerUrl: 'https://cdn.jsdelivr.net/npm/jassub@1.0.8/dist/jassub-worker.min.js',
        wasmUrl: 'https://cdn.jsdelivr.net/npm/jassub@1.0.8/dist/jassub-worker.wasm'
      });
    }
  }

  // Detect all embedded tracks when metadata loads
  const detectTracks = () => detectEmbeddedTracks(container);
  if (videoElement.readyState >= 1) {
    detectTracks();
  } else {
    videoElement.addEventListener('loadedmetadata', detectTracks);
  }

  // Also try after a short delay (some browsers populate tracks late)
  videoElement.addEventListener('loadeddata', () => {
    setTimeout(detectTracks, 500);
  });

  userId = await ensureSignedIn();
  roomData = await getRoomByCode(roomCode);

  // Start sync engine
  syncEngine.start({
    roomCode,
    userId,
    isHost,
    video: videoElement,
    onStatus: (status) => {
      syncStatus = status;
      updateSyncChip(container);
    },
  });

  // Listen to chat
  unsubChat = firebaseSync.observeChatMessages(roomCode, (messages) => {
    currentMessages = messages;
    if (showChat) {
      renderWatchChat(container, roomCode);
      lastReadCount = messages.length;
    }
    updateChatIndicators(container);
  });

  // Listen to allow controls
  if (!isHost) {
    unsubAllowControls = firebaseSync.observeAllowControls(roomCode, (allow) => {
      allowControls = allow;
      updateControlsState(container, isHost);
    });
  }

  // Update progress bar + subtitles periodically
  progressInterval = setInterval(() => {
    updateProgress(container);
    updateSubtitleOverlay(container);
  }, 200);

  // Auto-play
  videoElement.play().catch(() => {});
}

function setupEventListeners(container, roomCode, isHost) {
  const overlay = container.querySelector('#watchOverlay');
  const watchScreen = container.querySelector('#watchScreen');

  // Click to toggle controls
  watchScreen.addEventListener('click', (e) => {
    if (e.target.closest('.watch-top-bar') || e.target.closest('.watch-bottom-bar') ||
        e.target.closest('.watch-center') || e.target.closest('.watch-chat-panel') ||
        e.target.closest('.modal-overlay') || e.target.closest('.settings-panel')) return;

    if (showSettings) {
      showSettings = false;
      const panel = container.querySelector('#settingsPanel');
      if (panel) panel.style.display = 'none';
      
      showControls = false;
      const overlay = container.querySelector('#watchOverlay');
      if (overlay) overlay.classList.add('hidden');
      return;
    }

    toggleControls(container);
  });

  // Auto-hide controls after 3s
  resetControlsTimer(container);

  // Center play/pause
  container.querySelector('#centerPlayBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    if (!canControl(isHost)) return;
    togglePlayPause(roomCode, isHost);
  });

  // Bottom play/pause
  container.querySelector('#bottomPlayBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    if (!canControl(isHost)) return;
    togglePlayPause(roomCode, isHost);
  });

  // Seek bar
  const seekBar = container.querySelector('#seekBar');
  seekBar.addEventListener('input', (e) => {
    e.stopPropagation();
    if (!canControl(isHost)) return;
    const duration = videoElement.duration || 0;
    const position = (parseInt(seekBar.value) / 1000) * duration * 1000;
    syncEngine.broadcastCommand(roomCode, userId, 'seek', Math.round(position));
    videoElement.currentTime = position / 1000;
    resetControlsTimer(container);
  });

  // Volume
  const volumeSlider = container.querySelector('#volumeSlider');
  volumeSlider.addEventListener('input', (e) => {
    e.stopPropagation();
    videoElement.volume = parseInt(volumeSlider.value) / 100;
  });

  container.querySelector('#volumeBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    videoElement.muted = !videoElement.muted;
  });

  // Toggle controls (host only)
  container.querySelector('#toggleControlsBtn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    if (isHost) {
      allowControls = !allowControls;
      firebaseSync.setAllowControls(roomCode, allowControls);
      updateControlsState(container, isHost);
    }
  });

  // Subtitle/Settings toggle
  container.querySelector('#subtitleToggleBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    showSettings = !showSettings;
    const panel = container.querySelector('#settingsPanel');
    if (panel) panel.style.display = showSettings ? 'block' : 'none';

    // If closing settings, hide all controls to return to video only
    if (!showSettings) {
      showControls = false;
      const overlay = container.querySelector('#watchOverlay');
      if (overlay) overlay.classList.add('hidden');
    }
  });

  // Load subtitle file
  const subtitleFileInput = container.querySelector('#subtitleFileInput');
  container.querySelector('#loadSubtitleBtn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    subtitleFileInput?.click();
  });

  subtitleFileInput?.addEventListener('change', async (e) => {
    e.stopPropagation();
    const file = e.target.files[0];
    if (!file) return;
    const text = await file.text();
    subtitleCues = parseSRT(text);
    subtitlesEnabled = true;
    activeSubSource = 'external';
    disableAllNativeTextTracks();
    const status = container.querySelector('#subtitleStatus');
    if (status) status.textContent = `✓ ${file.name} (${subtitleCues.length} cues)`;
    const btn = container.querySelector('#subtitleToggleBtn');
    if (btn) btn.style.opacity = '1';
    // Update the dropdown to show external selection
    const select = container.querySelector('#subtitleTrackSelect');
    if (select) {
      // Add or update external option
      let extOpt = select.querySelector('option[value="external"]');
      if (!extOpt) {
        extOpt = document.createElement('option');
        extOpt.value = 'external';
        select.appendChild(extOpt);
      }
      extOpt.textContent = `📄 ${file.name}`;
      select.value = 'external';
    }
  });

  // Subtitle track selector (embedded + external)
  container.querySelector('#subtitleTrackSelect')?.addEventListener('change', (e) => {
    e.stopPropagation();
    const val = e.target.value;
    handleSubtitleTrackChange(container, val);
  });

  // Audio track selector
  container.querySelector('#audioTrackSelect')?.addEventListener('change', (e) => {
    e.stopPropagation();
    handleAudioTrackChange(e.target.value);
  });

  // Chat toggle
  container.querySelector('#chatToggleBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    showChat = !showChat;
    const panel = container.querySelector('#watchChatPanel');
    panel.style.display = showChat ? 'flex' : 'none';
    if (showChat) {
      lastReadCount = currentMessages.length;
      renderWatchChat(container, roomCode);
    }
    updateChatIndicators(container);
  });

  // Leave
  container.querySelector('#leaveBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    container.querySelector('#leaveModal').style.display = 'flex';
  });
  container.querySelector('#stayBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    container.querySelector('#leaveModal').style.display = 'none';
  });
  container.querySelector('#confirmLeaveBtn').addEventListener('click', async (e) => {
    e.stopPropagation();
    if (roomData) {
      await leaveRoom(roomData.code, roomData.id, userId);
    }
    cleanup(roomCode);
    navigate('#/');
  });

  // Keyboard controls
  const keyHandler = (e) => {
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

    switch (e.key) {
      case ' ':
      case 'k':
        e.preventDefault();
        if (canControl(isHost)) togglePlayPause(roomCode, isHost);
        break;
      case 'ArrowRight':
        e.preventDefault();
        if (canControl(isHost)) {
          const newPos = Math.min((videoElement.currentTime + 10) * 1000, (videoElement.duration || 0) * 1000);
          syncEngine.broadcastCommand(roomCode, userId, 'seek', Math.round(newPos));
          videoElement.currentTime = newPos / 1000;
        }
        break;
      case 'ArrowLeft':
        e.preventDefault();
        if (canControl(isHost)) {
          const newPos = Math.max((videoElement.currentTime - 10) * 1000, 0);
          syncEngine.broadcastCommand(roomCode, userId, 'seek', Math.round(newPos));
          videoElement.currentTime = newPos / 1000;
        }
        break;
      case 'ArrowUp':
        e.preventDefault();
        videoElement.volume = Math.min(videoElement.volume + 0.1, 1);
        volumeSlider.value = Math.round(videoElement.volume * 100);
        break;
      case 'ArrowDown':
        e.preventDefault();
        videoElement.volume = Math.max(videoElement.volume - 0.1, 0);
        volumeSlider.value = Math.round(videoElement.volume * 100);
        break;
      case 'c':
        e.preventDefault();
        showSettings = !showSettings;
        const panel = container.querySelector('#settingsPanel');
        if (panel) panel.style.display = showSettings ? 'block' : 'none';
        break;
      case 'f':
        e.preventDefault();
        if (document.fullscreenElement) {
          document.exitFullscreen();
        } else {
          watchScreen.requestFullscreen?.();
        }
        break;
    }
  };

  document.addEventListener('keydown', keyHandler);
  watchScreen._keyHandler = keyHandler;
}

function canControl(isHost) {
  return isHost || allowControls;
}

function togglePlayPause(roomCode, isHost) {
  if (videoElement.paused) {
    videoElement.play().catch(() => {});
    syncEngine.broadcastCommand(roomCode, userId, 'play',
      Math.round(videoElement.currentTime * 1000), videoElement.playbackRate);
  } else {
    videoElement.pause();
    syncEngine.broadcastCommand(roomCode, userId, 'pause',
      Math.round(videoElement.currentTime * 1000), videoElement.playbackRate);
  }
}

function toggleControls(container) {
  showControls = !showControls;
  const overlay = container.querySelector('#watchOverlay');
  overlay.classList.toggle('hidden', !showControls);
  updateChatIndicators(container);
  if (showControls) resetControlsTimer(container);
}

function resetControlsTimer(container) {
  if (controlsTimeout) clearTimeout(controlsTimeout);
  controlsTimeout = setTimeout(() => {
    if (showSettings) return; // Don't auto-hide if settings panel is open
    showControls = false;
    const overlay = container.querySelector('#watchOverlay');
    if (overlay) overlay.classList.add('hidden');
    updateChatIndicators(container);
  }, 3000);
}

function updateProgress(container) {
  if (!videoElement) return;
  const duration = videoElement.duration || 0;
  const current = videoElement.currentTime || 0;
  const progress = duration > 0 ? (current / duration) * 1000 : 0;

  const seekBar = container.querySelector('#seekBar');
  const seekFill = container.querySelector('#seekBarFill');
  const timeDisplay = container.querySelector('#timeDisplay');

  if (seekBar && !seekBar.matches(':active')) {
    seekBar.value = Math.round(progress);
  }
  if (seekFill) {
    seekFill.style.width = `${(progress / 1000) * 100}%`;
  }
  if (timeDisplay) {
    timeDisplay.textContent = `${formatTime(current)} / ${formatTime(duration)}`;
  }

  // Update play icons
  const isPlaying = !videoElement.paused;
  const playIconSvg = isPlaying
    ? '<rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/>'
    : '<polygon points="5 3 19 12 5 21 5 3"/>';

  const centerIcon = container.querySelector('#centerPlayIcon');
  const bottomIcon = container.querySelector('#bottomPlayIcon');
  if (centerIcon) centerIcon.innerHTML = playIconSvg;
  if (bottomIcon) bottomIcon.innerHTML = playIconSvg;
}

function updateSyncChip(container) {
  const dot = container.querySelector('.sync-dot');
  const text = container.querySelector('#syncText');
  if (!dot || !text) return;

  dot.className = 'sync-dot ' + syncStatus;
  text.textContent = syncStatus === 'synced' ? 'Synced' : syncStatus === 'correcting' ? 'Correcting' : 'Reconnecting';
}

function updateControlsState(container, isHost) {
  const label = container.querySelector('#controlsLabel');
  const lockIcon = container.querySelector('#lockIcon');
  if (label) {
    label.textContent = allowControls ? 'Controls: Shared' : 'Controls: Host Only';
  }
  if (lockIcon) {
    lockIcon.innerHTML = allowControls
      ? '<rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 9.9-1"/>'
      : '<rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>';
  }
}

function updateChatIndicators(container) {
  const badge = container.querySelector('#chatBadge');
  const dot = container.querySelector('#videoUnreadDot');
  const hasUnread = currentMessages.length > lastReadCount;
  
  if (badge) {
    badge.style.display = hasUnread && !showChat ? 'flex' : 'none';
  }
  if (dot) {
    dot.style.display = (!showControls && hasUnread && !showChat) ? 'block' : 'none';
  }
}

function renderWatchChat(container, roomCode) {
  const chatContainer = container.querySelector('#watchChatContainer');
  if (!chatContainer) return;

  createChatUI(chatContainer, {
    messages: currentMessages,
    currentUserId: userId,
    onSendMessage: async (text) => {
      const displayName = getDisplayName() || 'User';
      const messageId = crypto.randomUUID();
      await firebaseSync.sendMessage(roomCode, {
        messageId,
        senderId: userId,
        senderName: displayName,
        message: text,
      });
    },
    onClose: () => {
      showChat = false;
      container.querySelector('#watchChatPanel').style.display = 'none';
    },
    showHeader: true,
  });
}

function cleanup(roomCode) {
  syncEngine.stop(roomCode);
  if (unsubChat) { unsubChat(); unsubChat = null; }
  if (unsubAllowControls) { unsubAllowControls(); unsubAllowControls = null; }
  if (controlsTimeout) { clearTimeout(controlsTimeout); controlsTimeout = null; }
  if (progressInterval) { clearInterval(progressInterval); progressInterval = null; }

  const watchScreen = document.querySelector('#watchScreen');
  if (watchScreen?._keyHandler) {
    document.removeEventListener('keydown', watchScreen._keyHandler);
  }

  if (videoElement) {
    videoElement.pause();
    videoElement.src = '';
    videoElement = null;
  }

  currentMessages = [];
  showChat = false;
  showSettings = false;
  showControls = true;
  lastReadCount = 0;
  allowControls = false;
  syncStatus = 'synced';
  subtitleCues = [];
  subtitlesEnabled = false;
  currentSubtitle = '';
  activeSubSource = 'none';
  activeNativeTrackIndex = -1;
  tracksDetected = false;
}

function formatTime(seconds) {
  if (!seconds || isNaN(seconds)) return '0:00';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  if (h > 0) {
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }
  return `${m}:${String(s).padStart(2, '0')}`;
}

// ===== Subtitle Support =====

function parseSRT(text) {
  // Handles both SRT and VTT formats
  const cues = [];
  // Normalize line endings
  const normalized = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  // Split into blocks
  const blocks = normalized.split(/\n\n+/);

  for (const block of blocks) {
    const lines = block.trim().split('\n');
    if (lines.length < 2) continue;

    // Find the timestamp line
    let tsLine = -1;
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].includes('-->')) {
        tsLine = i;
        break;
      }
    }
    if (tsLine === -1) continue;

    const times = lines[tsLine].split('-->');
    if (times.length !== 2) continue;

    const startMs = parseTimestamp(times[0].trim());
    const endMs = parseTimestamp(times[1].trim().split(' ')[0]); // strip position info

    if (startMs === null || endMs === null) continue;

    // Everything after timestamp line is the text
    const textContent = lines.slice(tsLine + 1).join('\n')
      .replace(/<[^>]+>/g, '') // strip HTML tags
      .replace(/\{[^}]+\}/g, '') // strip ASS/SSA tags
      .trim();

    if (textContent) {
      cues.push({ start: startMs, end: endMs, text: textContent });
    }
  }

  return cues.sort((a, b) => a.start - b.start);
}

function parseTimestamp(ts) {
  // Supports: 00:00:00,000 or 00:00:00.000 or 00:00.000
  const clean = ts.replace(',', '.');
  const parts = clean.split(':');
  try {
    if (parts.length === 3) {
      const h = parseInt(parts[0]);
      const m = parseInt(parts[1]);
      const sms = parseFloat(parts[2]);
      return (h * 3600 + m * 60 + sms) * 1000;
    } else if (parts.length === 2) {
      const m = parseInt(parts[0]);
      const sms = parseFloat(parts[1]);
      return (m * 60 + sms) * 1000;
    }
  } catch {
    return null;
  }
  return null;
}

function updateSubtitleOverlay(container) {
  if (!videoElement) return;
  const overlay = container.querySelector('#subtitleOverlay');
  if (!overlay) return;

  // For native embedded tracks rendered via cuechange, skip manual rendering
  if (activeSubSource === 'native') {
    // Native track cues are handled by onNativeCueChange
    return;
  }

  if (!subtitlesEnabled || subtitleCues.length === 0) {
    if (currentSubtitle !== '') {
      currentSubtitle = '';
      overlay.innerHTML = '';
    }
    return;
  }

  const currentMs = videoElement.currentTime * 1000;

  let text = '';
  for (const cue of subtitleCues) {
    if (currentMs >= cue.start && currentMs <= cue.end) {
      text = cue.text;
      break;
    }
    if (cue.start > currentMs) break;
  }

  if (text !== currentSubtitle) {
    currentSubtitle = text;
    overlay.innerHTML = text ? `<span class="subtitle-text">${escapeSubHtml(text)}</span>` : '';
  }
}

function escapeSubHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>');
}

// ===== Embedded Track Detection =====

let activeSubSource = 'none'; // 'none' | 'external' | 'native'
let activeNativeTrackIndex = -1;
let tracksDetected = false;

function detectEmbeddedTracks(container) {
  if (tracksDetected) return;
  tracksDetected = true;
  
  if (libavWorker && audioFileBlob && audioFileBlob.name.toLowerCase().endsWith('.mkv')) {
    const subHint = container.querySelector('#subtitleHint');
    const audHint = container.querySelector('#audioHint');
    if (subHint) subHint.textContent = 'Probing MKV tracks...';
    if (audHint) audHint.textContent = 'Probing MKV tracks...';
    
    libavWorker.onmessage = (e) => handleWorkerMessage(e, container);
    libavWorker.postMessage({ type: 'probe', file: audioFileBlob });
  } else {
    detectEmbeddedSubtitles(container);
    detectEmbeddedAudio(container);
  }
}

let currentAudioNode = null;
let audioStartTime = 0;

function handleWorkerMessage(e, container) {
  const { type, tracks, pcm, channels, frames, sampleRate, text, error } = e.data;
  if (type === 'probed') {
    populateLibavTracks(container, tracks);
  } else if (type === 'audio_chunk') {
    if (!audioCtx) return;
    const buffer = audioCtx.createBuffer(channels, frames, sampleRate);
    buffer.copyToChannel(pcm, 0); // simplified for 1 channel
    const source = audioCtx.createBufferSource();
    source.buffer = buffer;
    source.connect(audioCtx.destination);
    source.start(audioStartTime);
    audioStartTime += buffer.duration;
  } else if (type === 'sub_data') {
    if (jassub) jassub.setTrack(text);
  } else if (type === 'error') {
    console.error('libav error:', error);
    const subHint = container.querySelector('#subtitleHint');
    const audHint = container.querySelector('#audioHint');
    if (subHint) subHint.textContent = `Error: ${error}`;
    if (audHint) audHint.textContent = `Error: ${error}`;
  }
}

function populateLibavTracks(container, tracks) {
  const subSelect = container.querySelector('#subtitleTrackSelect');
  const subHint = container.querySelector('#subtitleHint');
  const audSelect = container.querySelector('#audioTrackSelect');
  const audHint = container.querySelector('#audioHint');

  const audioTracks = tracks.filter(t => t.type === 'audio');
  const subTracks = tracks.filter(t => t.type === 'sub');

  if (audSelect && audioTracks.length > 0) {
    audSelect.innerHTML = '<option value="default">Default</option>';
    audioTracks.forEach(t => {
      const opt = document.createElement('option');
      opt.value = `libav-aud-${t.id}`;
      opt.textContent = `🔊 ${t.title || 'Track'} [${t.lang}]`;
      audSelect.appendChild(opt);
    });
    if (audHint) audHint.textContent = `${audioTracks.length} audio tracks found in MKV`;
  }

  if (subSelect && subTracks.length > 0) {
    subTracks.forEach(t => {
      const opt = document.createElement('option');
      opt.value = `libav-sub-${t.id}`;
      opt.textContent = `🎬 ${t.title || 'Track'} [${t.lang}]`;
      subSelect.appendChild(opt);
    });
    if (subHint) subHint.textContent = `${subTracks.length} subtitle tracks found in MKV`;
  }
}

function detectEmbeddedSubtitles(container) {
  const select = container.querySelector('#subtitleTrackSelect');
  const hint = container.querySelector('#subtitleHint');
  if (!select || !videoElement) return;

  const textTracks = videoElement.textTracks;
  let embeddedCount = 0;

  if (textTracks && textTracks.length > 0) {
    for (let i = 0; i < textTracks.length; i++) {
      const track = textTracks[i];
      // Only show subtitle/captions tracks
      if (track.kind === 'subtitles' || track.kind === 'captions' || track.kind === 'metadata') {
        embeddedCount++;
        const label = track.label || track.language || `Embedded Track ${embeddedCount}`;
        const langTag = track.language ? ` [${track.language.toUpperCase()}]` : '';
        const opt = document.createElement('option');
        opt.value = `native-${i}`;
        opt.textContent = `🎬 ${label}${langTag}`;
        select.appendChild(opt);
        // Disable native rendering — we render via our overlay
        track.mode = 'hidden';
      }
    }
  }

  if (embeddedCount > 0) {
    if (hint) hint.textContent = `${embeddedCount} embedded subtitle track${embeddedCount > 1 ? 's' : ''} found`;
  } else {
    if (hint) {
      hint.innerHTML = 'Browsers cannot detect embedded subtitles in MKV files.<br>Please load an external .srt/.vtt file below.';
      hint.style.lineHeight = '1.4';
      hint.style.opacity = '0.9';
    }
  }
}

function detectEmbeddedAudio(container) {
  const select = container.querySelector('#audioTrackSelect');
  const hint = container.querySelector('#audioHint');
  if (!select || !videoElement) return;

  // Try native audioTracks API (Safari, Chrome with flag)
  if (videoElement.audioTracks && videoElement.audioTracks.length > 0) {
    const tracks = videoElement.audioTracks;
    if (tracks.length > 1) {
      select.innerHTML = '';
      for (let i = 0; i < tracks.length; i++) {
        const track = tracks[i];
        const label = track.label || track.language || `Audio ${i + 1}`;
        const langTag = track.language ? ` [${track.language.toUpperCase()}]` : '';
        const opt = document.createElement('option');
        opt.value = `native-${i}`;
        opt.textContent = `🔊 ${label}${langTag}`;
        opt.selected = track.enabled;
        select.appendChild(opt);
      }
      if (hint) hint.textContent = `${tracks.length} audio tracks found`;
    } else if (tracks.length === 1) {
      const track = tracks[0];
      select.innerHTML = '';
      const opt = document.createElement('option');
      opt.value = 'native-0';
      opt.textContent = `🔊 ${track.label || track.language || 'Default'}`;
      opt.selected = true;
      select.appendChild(opt);
      if (hint) hint.textContent = '1 audio track (default)';
    }
  } else {
    // audioTracks API not available — show info
    if (hint) {
      hint.innerHTML = 'Browsers cannot detect multiple audio tracks in MKV files.<br>Please use the Android app for full MKV track support.';
      hint.style.lineHeight = '1.4';
      hint.style.opacity = '0.9';
    }
  }
}

function handleSubtitleTrackChange(container, value) {
  const overlay = container.querySelector('#subtitleOverlay');

  if (value === 'off') {
    // Turn off all subtitles
    subtitlesEnabled = false;
    activeSubSource = 'none';
    activeNativeTrackIndex = -1;
    subtitleCues = [];
    currentSubtitle = '';
    if (overlay) overlay.innerHTML = '';
    disableAllNativeTextTracks();
    if (jassub) jassub.freeTrack();
    const btn = container.querySelector('#subtitleToggleBtn');
    if (btn) btn.style.opacity = '0.5';
    return;
  }

  if (value === 'external') {
    // Switch to externally loaded subtitles
    activeSubSource = 'external';
    subtitlesEnabled = subtitleCues.length > 0;
    disableAllNativeTextTracks();
    if (jassub) jassub.freeTrack();
    const btn = container.querySelector('#subtitleToggleBtn');
    if (btn) btn.style.opacity = subtitlesEnabled ? '1' : '0.5';
    return;
  }
  
  if (value.startsWith('libav-sub-')) {
    activeSubSource = 'libav';
    subtitlesEnabled = true;
    disableAllNativeTextTracks();
    if (overlay) overlay.innerHTML = '';
    const trackId = parseInt(value.replace('libav-sub-', ''));
    if (libavWorker) libavWorker.postMessage({ type: 'extract_sub', file: audioFileBlob, trackId });
    const btn = container.querySelector('#subtitleToggleBtn');
    if (btn) btn.style.opacity = '1';
    return;
  }

  if (value.startsWith('native-')) {
    const idx = parseInt(value.replace('native-', ''));
    activeSubSource = 'native';
    activeNativeTrackIndex = idx;
    subtitlesEnabled = true;
    currentSubtitle = '';
    if (overlay) overlay.innerHTML = '';

    // Enable this track, disable all others
    const textTracks = videoElement.textTracks;
    for (let i = 0; i < textTracks.length; i++) {
      if (i === idx) {
        textTracks[i].mode = 'hidden'; // hidden = we read cues but browser doesn't render
        // Listen to cue changes
        textTracks[i].oncuechange = () => onNativeCueChange(container, i);
      } else {
        textTracks[i].mode = 'disabled';
        textTracks[i].oncuechange = null;
      }
    }
    const btn = container.querySelector('#subtitleToggleBtn');
    if (btn) btn.style.opacity = '1';
  }
}

function onNativeCueChange(container, trackIndex) {
  if (!videoElement || activeSubSource !== 'native') return;
  const overlay = container.querySelector('#subtitleOverlay');
  if (!overlay) return;

  const track = videoElement.textTracks[trackIndex];
  if (!track || !track.activeCues || track.activeCues.length === 0) {
    if (currentSubtitle !== '') {
      currentSubtitle = '';
      overlay.innerHTML = '';
    }
    return;
  }

  // Combine all active cues
  let text = '';
  for (let i = 0; i < track.activeCues.length; i++) {
    const cue = track.activeCues[i];
    if (cue.text) {
      text += (text ? '\n' : '') + cue.text;
    }
  }

  if (text !== currentSubtitle) {
    currentSubtitle = text;
    overlay.innerHTML = text ? `<span class="subtitle-text">${escapeSubHtml(text)}</span>` : '';
  }
}

function handleAudioTrackChange(value) {
  if (value === 'default') {
    videoElement.muted = false;
    if (audioCtx) {
      audioCtx.suspend();
    }
    return;
  }
  
  if (value.startsWith('libav-aud-')) {
    videoElement.muted = true;
    const trackId = parseInt(value.replace('libav-aud-', ''));
    if (audioCtx) {
      audioCtx.resume();
      audioStartTime = audioCtx.currentTime;
    }
    if (libavWorker) libavWorker.postMessage({ type: 'decode', file: audioFileBlob, trackId });
    return;
  }

  if (!videoElement || !videoElement.audioTracks) return;

  if (value.startsWith('native-')) {
    const idx = parseInt(value.replace('native-', ''));
    for (let i = 0; i < videoElement.audioTracks.length; i++) {
      videoElement.audioTracks[i].enabled = (i === idx);
    }
  }
}

function disableAllNativeTextTracks() {
  if (!videoElement || !videoElement.textTracks) return;
  for (let i = 0; i < videoElement.textTracks.length; i++) {
    videoElement.textTracks[i].mode = 'disabled';
    videoElement.textTracks[i].oncuechange = null;
  }
}
