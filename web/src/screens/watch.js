import { navigate } from '../router.js';
import { ensureSignedIn, getDisplayName } from '../auth.js';
import * as syncEngine from '../sync-engine.js';
import * as firebaseSync from '../firebase-sync.js';
import { leaveRoom, getRoomByCode } from '../room-repository.js';
import { createChatUI } from '../components/chat.js';
import * as voiceChat from '../voice-chat.js';
import * as movi from '../movi-player-bridge.js';

let userId = null;
let roomData = null;
let controlsTimeout = null;
let showControls = true;
let showChat = false;
let showSettings = false;
let lastReadCount = 0;
let currentMessages = [];
let unsubChat = null;
let unsubAllowControls = null;
let allowControls = true;
let syncStatus = 'synced';
let progressInterval = null;
let showRemainingTime = false;
let unsubVoiceState = null;
let unsubVoicePeer = null;
let voicePeerWasConnected = false;
let externalSubtitleBlobUrl = null;

let playerReady = false;
let isReady = false;

export function renderWatch(container, { code, isHost }) {
  const isHostBool = isHost === 'true';
  const file = window.__movsync_file;

  if (!file) {
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
      <canvas id="moviCanvas" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%;"></canvas>

      <div class="video-unread-dot" id="videoUnreadDot" style="display:none;"></div>
      <div class="watch-toast" id="watchToast" role="status" aria-live="polite"></div>

      <div class="watch-overlay" id="watchOverlay">
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
            <button class="btn-icon-watch" id="subtitleToggleBtn" title="Audio & Subtitles">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="14" x2="23" y2="14"/><line x1="5" y1="18" x2="19" y2="18"/></svg>
            </button>
            <button class="btn-icon-watch" id="chatToggleBtn" title="Chat">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
              <span class="chat-badge" id="chatBadge" style="display:none;">!</span>
            </button>
            <button class="btn-icon-watch voice-mic-btn voice-disconnected" id="voiceMicBtn" title="Join Voice Chat">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="22"/><line x1="8" y1="22" x2="16" y2="22"/></svg>
            </button>
            <button class="btn-icon-watch" id="leaveBtn" title="Leave">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
        </div>

        <div class="settings-panel" id="settingsPanel" style="display:none;">
          <div class="settings-card glass-card">
            <h4 class="settings-title">Audio & Subtitles</h4>

            <div class="settings-section">
              <label class="settings-label">Audio Track</label>
              <div class="custom-select" id="audioTrackSelect" data-value="default">
                <div class="custom-select-trigger" id="audioTrackTrigger"><span>Default</span></div>
                <div class="custom-select-options" id="audioTrackOptions">
                  <div class="custom-option selected" data-value="default">Default</div>
                </div>
              </div>
              <p class="settings-hint" id="audioHint">Detecting embedded tracks...</p>
            </div>

            <div class="settings-section">
              <label class="settings-label">Subtitle Track</label>
              <div class="custom-select" id="subtitleTrackSelect" data-value="off">
                <div class="custom-select-trigger" id="subtitleTrackTrigger"><span>Off</span></div>
                <div class="custom-select-options" id="subtitleTrackOptions">
                  <div class="custom-option selected" data-value="off">Off</div>
                </div>
              </div>
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
          </div>
        </div>

        <div class="watch-center">
          <button class="center-play-btn" id="centerPlayBtn">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" id="centerPlayIcon"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          </button>
        </div>

        <div class="watch-bottom-bar">
          <button class="btn-icon-watch" id="bottomPlayBtn">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" id="bottomPlayIcon"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          </button>
          <div class="seek-bar-wrapper" style="flex: 1; display: flex; flex-direction: column; justify-content: center; margin: 0 12px;">
            <div style="display: flex; justify-content: space-between; padding: 0 4px; margin-bottom: 4px; font-size: 12px; color: white;">
              <span id="timeCurrent">0:00</span>
              <span id="timeTotal" style="cursor: pointer;">0:00</span>
            </div>
            <div class="seek-bar-container" style="position: relative; width: 100%;">
              <input type="range" class="seek-bar" id="seekBar" min="0" max="1000" value="0" step="1" style="width: 100%; margin: 0;" />
              <div class="seek-bar-fill" id="seekBarFill"></div>
            </div>
          </div>
          <div class="volume-control">
            <button class="btn-icon-watch" id="volumeBtn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/></svg>
            </button>
            <input type="range" class="volume-slider" id="volumeSlider" min="0" max="100" value="100" />
          </div>
          <button class="btn-icon-watch" id="fullscreenBtn" title="Full Screen" style="margin-left: 8px;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/></svg>
          </button>
        </div>
      </div>

      <div class="watch-chat-panel" id="watchChatPanel" style="display:none;">
        <div id="watchChatContainer"></div>
      </div>

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

  init(container, code, isHostBool);
  setupEventListeners(container, code, isHostBool);

  return () => cleanup(code);
}

async function init(container, roomCode, isHost) {
  const canvas = container.querySelector('#moviCanvas');

  const player = movi.initPlayer(canvas, window.__movsync_file);
  await movi.loadPlayer();

  isReady = true;

  movi.resizeCanvas();
  window.addEventListener('resize', () => movi.resizeCanvas());
  document.addEventListener('fullscreenchange', async () => {
    setTimeout(() => movi.resizeCanvas(), 100);
    if (document.fullscreenElement && screen.orientation && screen.orientation.lock) {
      try { await screen.orientation.lock('landscape'); } catch (e) { /* ignore */ }
    } else if (!document.fullscreenElement && screen.orientation && screen.orientation.unlock) {
      try { screen.orientation.unlock(); } catch (e) { /* ignore */ }
    }
  });

  setTimeout(() => populateTracks(container), 500);

  movi.onPlayStateChangeCallback((paused) => {
    updatePlayIcons(container);
  });

  if (roomCode === 'local') {
    const syncChip = container.querySelector('#syncChip');
    if (syncChip) syncChip.style.display = 'none';
    const chatToggleBtn = container.querySelector('#chatToggleBtn');
    if (chatToggleBtn) chatToggleBtn.style.display = 'none';
    const voiceMicBtn = container.querySelector('#voiceMicBtn');
    if (voiceMicBtn) voiceMicBtn.style.display = 'none';
    const toggleControlsBtn = container.querySelector('#toggleControlsBtn');
    if (toggleControlsBtn) toggleControlsBtn.style.display = 'none';

    progressInterval = setInterval(() => {
      updateProgress(container);
    }, 200);

    movi.play();
    return;
  }

  userId = await ensureSignedIn();
  roomData = await getRoomByCode(roomCode);
  voiceChat.prefetchToken(roomCode, getDisplayName() || 'User', userId);
  voiceChat.startObservingVoiceActive(roomCode);
  setupVoiceChat(container);

  const syncVideo = {
    get currentTime() { return movi.getCurrentTime(); },
    set currentTime(t) { movi.setCurrentTime(t); },
    get paused() { return movi.isPaused(); },
    get playbackRate() { return movi.getPlaybackRate(); },
    set playbackRate(r) { movi.setPlaybackRate(r); },
    play: () => movi.play(),
    pause: () => movi.pause(),
  };

  syncEngine.start({
    roomCode,
    userId,
    isHost,
    video: syncVideo,
    onStatus: (status) => {
      syncStatus = status;
      updateSyncChip(container);
    },
  });

  unsubChat = firebaseSync.observeChatMessages(roomCode, (messages) => {
    currentMessages = messages;
    if (showChat) {
      renderWatchChat(container, roomCode);
      lastReadCount = messages.length;
    }
    updateChatIndicators(container);
  });

  if (!isHost) {
    unsubAllowControls = firebaseSync.observeAllowControls(roomCode, (allow) => {
      allowControls = allow;
      updateControlsState(container, isHost);
    });
  }

  progressInterval = setInterval(() => {
    updateProgress(container);
  }, 200);

  movi.play().catch(() => {});
}

function populateTracks(container) {
  const audioTracks = movi.getAudioTracks();
  const subTracks = movi.getSubtitleTracks();
  const audSelect = container.querySelector('#audioTrackSelect');
  const audHint = container.querySelector('#audioHint');
  const subSelect = container.querySelector('#subtitleTrackSelect');
  const subHint = container.querySelector('#subtitleHint');

  if (audSelect && audioTracks.length > 0) {
    const opts = container.querySelector('#audioTrackOptions');
    if (opts) {
      opts.innerHTML = '<div class="custom-option selected" data-value="default">Default</div>';
      audioTracks.forEach((t) => {
        const div = document.createElement('div');
        div.className = 'custom-option';
        div.dataset.value = String(t.id);
        div.textContent = `🔊 ${t.label || t.language || `Track ${t.id}`}`;
        if (t.language) div.textContent += ` [${t.language.toUpperCase()}]`;
        opts.appendChild(div);
      });
    }
    if (audHint) audHint.textContent = `${audioTracks.length} audio tracks found`;
  } else {
    if (audHint) audHint.textContent = '1 audio track (default)';
  }

  if (subSelect && subTracks.length > 0) {
    const opts = container.querySelector('#subtitleTrackOptions');
    if (opts) {
      subTracks.forEach((t) => {
        if (t.type === 'subtitle') {
          const div = document.createElement('div');
          div.className = 'custom-option';
          div.dataset.value = String(t.id);
          div.textContent = `🎬 ${t.label || t.language || `Track ${t.id}`}`;
          if (t.language) div.textContent += ` [${t.language.toUpperCase()}]`;
          opts.appendChild(div);
        }
      });
    }
    if (subHint) subHint.textContent = `${subTracks.filter(t=>t.type==='subtitle').length} subtitle tracks found`;
  } else {
    if (subHint) subHint.textContent = 'No embedded subtitles detected. Load an external .srt/.vtt file.';
  }
}

function setupEventListeners(container, roomCode, isHost) {
  const overlay = container.querySelector('#watchOverlay');
  const watchScreen = container.querySelector('#watchScreen');

  watchScreen.addEventListener('click', (e) => {
    if (e.target.closest('.watch-top-bar') || e.target.closest('.watch-bottom-bar') ||
        e.target.closest('.watch-center') || e.target.closest('.watch-chat-panel') ||
        e.target.closest('.modal-overlay') || e.target.closest('.settings-panel')) return;

    if (showSettings) {
      showSettings = false;
      const panel = container.querySelector('#settingsPanel');
      if (panel) panel.style.display = 'none';
      showControls = false;
      if (overlay) overlay.classList.add('hidden');
      if (watchScreen) watchScreen.classList.add('idle');
      return;
    }

    toggleControls(container);
  });

  watchScreen.addEventListener('dblclick', (e) => {
    if (e.target.tagName !== 'CANVAS') return;
    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else {
      watchScreen.requestFullscreen?.();
    }
  });

  resetControlsTimer(container);

  container.querySelector('#centerPlayBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    if (!canControl(isHost)) return;
    togglePlayPause(roomCode, isHost);
  });

  container.querySelector('#bottomPlayBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    if (!canControl(isHost)) return;
    togglePlayPause(roomCode, isHost);
  });

  const seekBar = container.querySelector('#seekBar');
  seekBar.addEventListener('input', (e) => {
    e.stopPropagation();
    if (!canControl(isHost)) return;
    const duration = movi.getDuration() || 0;
    const position = (parseInt(seekBar.value) / 1000) * duration;
    movi.setCurrentTime(position);
    resetControlsTimer(container);
  });

  seekBar.addEventListener('change', (e) => {
    e.stopPropagation();
    if (!canControl(isHost)) return;
    const duration = movi.getDuration() || 0;
    const position = (parseInt(seekBar.value) / 1000) * duration;
    if (roomCode !== 'local') {
      syncEngine.broadcastCommand(roomCode, userId, 'seek', Math.round(position * 1000));
    }
  });

  container.querySelector('#timeTotal')?.addEventListener('click', (e) => {
    e.stopPropagation();
    showRemainingTime = !showRemainingTime;
    updateProgress(container);
  });

  const volumeSlider = container.querySelector('#volumeSlider');
  volumeSlider.addEventListener('input', (e) => {
    e.stopPropagation();
    const val = parseInt(volumeSlider.value) / 100;
    movi.setVolume(val);
  });

  container.querySelector('#volumeBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    movi.setMuted(!movi.isMuted());
  });

  container.querySelector('#fullscreenBtn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else {
      watchScreen.requestFullscreen?.();
    }
  });

  container.querySelector('#toggleControlsBtn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    if (isHost) {
      allowControls = !allowControls;
      firebaseSync.setAllowControls(roomCode, allowControls);
      updateControlsState(container, isHost);
    }
  });

  container.querySelector('#subtitleToggleBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    showSettings = !showSettings;
    const panel = container.querySelector('#settingsPanel');
    if (panel) panel.style.display = showSettings ? 'block' : 'none';
    if (!showSettings) {
      showControls = false;
      if (overlay) overlay.classList.add('hidden');
      if (watchScreen) watchScreen.classList.add('idle');
    }
  });

  const subtitleFileInput = container.querySelector('#subtitleFileInput');
  container.querySelector('#loadSubtitleBtn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    subtitleFileInput?.click();
  });

  subtitleFileInput?.addEventListener('change', async (e) => {
    e.stopPropagation();
    const file = e.target.files[0];
    if (!file) return;
    if (externalSubtitleBlobUrl) URL.revokeObjectURL(externalSubtitleBlobUrl);
    externalSubtitleBlobUrl = URL.createObjectURL(file);
    const lang = file.name.match(/\.([a-z]{2,3})\.(srt|vtt|ass|ssa)$/i)?.[1] || 'und';
    movi.loadExternalSubtitle(externalSubtitleBlobUrl, lang, file.name);
    const status = container.querySelector('#subtitleStatus');
    if (status) status.textContent = `✓ ${file.name}`;
  });

  container.querySelector('#settingsPanel')?.addEventListener('click', (e) => {
    const trigger = e.target.closest('.custom-select-trigger');
    if (trigger) {
      const select = trigger.closest('.custom-select');
      const isOpen = select.classList.contains('open');
      // close all others
      container.querySelectorAll('.custom-select').forEach(s => s.classList.remove('open'));
      if (!isOpen) select.classList.add('open');
      return;
    }
    
    const option = e.target.closest('.custom-option');
    if (option) {
      const select = option.closest('.custom-select');
      const val = option.dataset.value;
      const text = option.textContent;
      select.dataset.value = val;
      select.querySelector('.custom-select-trigger span').textContent = text;
      select.querySelectorAll('.custom-option').forEach(o => o.classList.remove('selected'));
      option.classList.add('selected');
      select.classList.remove('open');
      
      if (select.id === 'audioTrackSelect') {
        if (val === 'default') {
          const tracks = movi.getAudioTracks();
          if (tracks.length > 0) movi.setAudioTrack(tracks[0].id);
        } else {
          const trackId = parseInt(val, 10);
          if (!isNaN(trackId)) movi.setAudioTrack(trackId);
        }
      } else if (select.id === 'subtitleTrackSelect') {
        if (val === 'off') {
          movi.setSubtitleTrack(null);
        } else {
          const trackId = parseInt(val, 10);
          if (!isNaN(trackId)) movi.setSubtitleTrack(trackId);
        }
      }
    } else {
      // Clicked outside, close all
      container.querySelectorAll('.custom-select').forEach(s => s.classList.remove('open'));
    }
  });

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

  container.querySelector('#voiceMicBtn')?.addEventListener('click', async (e) => {
    e.stopPropagation();
    try {
      if (voiceChat.getState() === 'disconnected') {
        await voiceChat.startVoiceForAll();
      } else if (voiceChat.getState() === 'connected') {
        await voiceChat.stopVoiceForAll();
      }
    } catch (error) {
      console.error('Voice chat connection failed:', error);
      showWatchToast(container, 'Unable to connect to voice chat');
    }
  });

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
    if (roomData && roomCode !== 'local') {
      await leaveRoom(roomData.code, roomData.id, userId);
    }
    cleanup(roomCode);
    navigate('#/');
  });

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
          const newPos = Math.min((movi.getCurrentTime() + 10), movi.getDuration() || 0);
          if (roomCode !== 'local') {
            syncEngine.broadcastCommand(roomCode, userId, 'seek', Math.round(newPos * 1000));
          }
          movi.setCurrentTime(newPos);
        }
        break;
      case 'ArrowLeft':
        e.preventDefault();
        if (canControl(isHost)) {
          const newPos = Math.max((movi.getCurrentTime() - 10), 0);
          if (roomCode !== 'local') {
            syncEngine.broadcastCommand(roomCode, userId, 'seek', Math.round(newPos * 1000));
          }
          movi.setCurrentTime(newPos);
        }
        break;
      case 'ArrowUp':
        e.preventDefault();
        movi.setVolume(Math.min(movi.getVolume() + 0.1, 1));
        volumeSlider.value = Math.round(movi.getVolume() * 100);
        break;
      case 'ArrowDown':
        e.preventDefault();
        movi.setVolume(Math.max(movi.getVolume() - 0.1, 0));
        volumeSlider.value = Math.round(movi.getVolume() * 100);
        break;
      case 'c':
        e.preventDefault();
        showSettings = !showSettings;
        const panel = container.querySelector('#settingsPanel');
        if (panel) panel.style.display = showSettings ? 'block' : 'none';
        break;
      case 'm':
        e.preventDefault();
        const vol = movi.getVolume();
        if (vol > 0) {
          watchScreen._lastVol = vol;
          movi.setVolume(0);
        } else {
          movi.setVolume(watchScreen._lastVol || 1);
        }
        volumeSlider.value = Math.round(movi.getVolume() * 100);
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

  // Mouse idle detection
  const mouseHandler = () => {
    if (!showControls && !showSettings) {
      showControls = true;
      toggleControls(container);
    }
    resetControlsTimer(container);
  };
  watchScreen.addEventListener('mousemove', mouseHandler);
  watchScreen.addEventListener('click', mouseHandler);

  // Setup Media Session API (Hardware media keys & Lockscreen)
  if ('mediaSession' in navigator) {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: file.name || 'Local Video',
      artist: 'MovSync Player',
      artwork: [{ src: '/logo.jpg', sizes: '512x512', type: 'image/jpeg' }]
    });

    navigator.mediaSession.setActionHandler('play', () => {
      if (canControl(isHost)) togglePlayPause(roomCode, isHost);
    });
    navigator.mediaSession.setActionHandler('pause', () => {
      if (canControl(isHost)) togglePlayPause(roomCode, isHost);
    });
    navigator.mediaSession.setActionHandler('seekbackward', () => {
      if (canControl(isHost)) {
        const newPos = Math.max((movi.getCurrentTime() - 10), 0);
        movi.setCurrentTime(newPos);
        if (roomCode !== 'local') syncEngine.broadcastCommand(roomCode, userId, 'seek', Math.round(newPos * 1000));
      }
    });
    navigator.mediaSession.setActionHandler('seekforward', () => {
      if (canControl(isHost)) {
        const newPos = Math.min((movi.getCurrentTime() + 10), movi.getDuration() || 0);
        movi.setCurrentTime(newPos);
        if (roomCode !== 'local') syncEngine.broadcastCommand(roomCode, userId, 'seek', Math.round(newPos * 1000));
      }
    });
  }
}

function canControl(isHost) {
  return isHost || allowControls;
}

function togglePlayPause(roomCode, isHost) {
  if (movi.isPaused()) {
    movi.play();
    if (roomCode !== 'local') {
      syncEngine.broadcastCommand(roomCode, userId, 'play',
        Math.round(movi.getCurrentTime() * 1000), movi.getPlaybackRate());
    }
  } else {
    movi.pause();
    if (roomCode !== 'local') {
      syncEngine.broadcastCommand(roomCode, userId, 'pause',
        Math.round(movi.getCurrentTime() * 1000), movi.getPlaybackRate());
    }
  }
}

function toggleControls(container) {
  showControls = !showControls;
  const overlay = container.querySelector('#watchOverlay');
  overlay.classList.toggle('hidden', !showControls);
  const watchScreen = container.querySelector('#watchScreen');
  if (watchScreen) watchScreen.classList.toggle('idle', !showControls);
  updateChatIndicators(container);
  if (showControls) resetControlsTimer(container);
}

function resetControlsTimer(container) {
  if (controlsTimeout) clearTimeout(controlsTimeout);
  controlsTimeout = setTimeout(() => {
    if (showSettings) return;
    showControls = false;
    const overlay = container.querySelector('#watchOverlay');
    if (overlay) overlay.classList.add('hidden');
    const watchScreen = container.querySelector('#watchScreen');
    if (watchScreen) watchScreen.classList.add('idle');
    updateChatIndicators(container);
  }, 3000);
}

function updateProgress(container) {
  const duration = movi.getDuration() || 0;
  const current = movi.getCurrentTime() || 0;
  const progress = duration > 0 ? (current / duration) * 1000 : 0;

  const seekBar = container.querySelector('#seekBar');
  const seekFill = container.querySelector('#seekBarFill');
  const timeCurrent = container.querySelector('#timeCurrent');
  const timeTotal = container.querySelector('#timeTotal');

  if (seekBar && !seekBar.matches(':active')) {
    seekBar.value = Math.round(progress);
  }
  if (seekFill) {
    seekFill.style.width = `${(progress / 1000) * 100}%`;
  }
  if (timeCurrent) {
    timeCurrent.textContent = formatTime(current);
  }
  if (timeTotal) {
    timeTotal.textContent = showRemainingTime ? `-${formatTime(duration - current)}` : formatTime(duration);
  }
}

function updatePlayIcons(container) {
  const isPlaying = !movi.isPaused();
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
  if (badge) badge.style.display = hasUnread && !showChat ? 'flex' : 'none';
  if (dot) dot.style.display = (!showControls && hasUnread && !showChat) ? 'block' : 'none';
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

function setupVoiceChat(container) {
  unsubVoiceState?.();
  unsubVoicePeer?.();
  voicePeerWasConnected = false;

  unsubVoiceState = voiceChat.onStateChange((voiceState) => {
    const button = container.querySelector('#voiceMicBtn');
    if (!button) return;
    button.classList.remove('voice-disconnected', 'voice-connecting', 'voice-connected');
    button.classList.add(`voice-${voiceState}`);
    button.disabled = voiceState === 'connecting';
    button.title = voiceState === 'connected'
      ? 'Disconnect Voice Chat'
      : voiceState === 'connecting'
        ? 'Connecting to Voice Chat'
        : 'Join Voice Chat';
    if (voiceState !== 'connected') voicePeerWasConnected = false;
  });

  unsubVoicePeer = voiceChat.onPeerChange((connected) => {
    if (voiceChat.getState() === 'connected' && voicePeerWasConnected && !connected) {
      showWatchToast(container, 'Peer disconnected from voice chat');
    }
    voicePeerWasConnected = connected;
  });
}

function showWatchToast(container, message) {
  const toast = container.querySelector('#watchToast');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('visible');
  clearTimeout(toast._hideTimeout);
  toast._hideTimeout = setTimeout(() => toast.classList.remove('visible'), 2500);
}

function cleanup(roomCode) {
  syncEngine.stop(roomCode);
  if (unsubChat) { unsubChat(); unsubChat = null; }
  if (unsubAllowControls) { unsubAllowControls(); unsubAllowControls = null; }
  if (controlsTimeout) { clearTimeout(controlsTimeout); controlsTimeout = null; }
  if (progressInterval) { clearInterval(progressInterval); progressInterval = null; }
  if (unsubVoiceState) { unsubVoiceState(); unsubVoiceState = null; }
  if (unsubVoicePeer) { unsubVoicePeer(); unsubVoicePeer = null; }
  voicePeerWasConnected = false;
  void voiceChat.cleanup();
  movi.destroyPlayer();
  if (externalSubtitleBlobUrl) {
    URL.revokeObjectURL(externalSubtitleBlobUrl);
    externalSubtitleBlobUrl = null;
  }
  if (window.__movsync_videoUrl) {
    URL.revokeObjectURL(window.__movsync_videoUrl);
    delete window.__movsync_videoUrl;
  }
  delete window.__movsync_file;

  const watchScreen = document.querySelector('#watchScreen');
  if (watchScreen?._keyHandler) {
    document.removeEventListener('keydown', watchScreen._keyHandler);
  }

  currentMessages = [];
  showChat = false;
  showSettings = false;
  showControls = true;
  lastReadCount = 0;
  allowControls = true;
  syncStatus = 'synced';
  showRemainingTime = false;
  isReady = false;
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