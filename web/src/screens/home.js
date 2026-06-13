/**
 * Home Screen
 */

import { getDisplayName, saveDisplayName } from '../auth.js';
import { navigate } from '../router.js';
import { isConfigured } from '../config.js';
import { clearRecentRoom, getRecentRoom } from '../recent-room.js';

export function renderHome(container) {
  const currentName = getDisplayName();
  const configured = isConfigured();
  const recentRoom = getRecentRoom();

  container.innerHTML = `
    <div class="home-layout premium-home">
      ${!configured ? `
        <div class="config-warning">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          <div>
            <strong>Backend not configured</strong>
            <p>Create a <code>.env</code> file in the <code>web/</code> folder with your Firebase &amp; Supabase credentials. See <code>.env.example</code> for the template.</p>
          </div>
        </div>
      ` : ''}

      <section class="home-hero">
        <div class="hero-particles" aria-hidden="true">
          <span></span><span></span><span></span><span></span><span></span>
        </div>
        <div class="hero-glow"></div>
        <div class="hero-icon">
          <img src="/logo.jpg" alt="MovSync Logo" class="hero-logo" />
        </div>
        <p class="hero-kicker">Private watch parties</p>
        <h1 class="hero-title">Mov<span class="hero-title-accent">Sync</span></h1>
        <p class="hero-subtitle">Watch together, perfectly in sync</p>
        <p class="hero-desc">No uploads. No streaming servers. Just your local movie files, synchronized across every screen.</p>
      </section>

      <section class="home-card glass-card name-card">
        <label class="input-label" for="displayNameInput">Your Display Name</label>
        <input
          type="text"
          id="displayNameInput"
          class="input-field"
          placeholder="Enter your name..."
          value="${escapeAttr(currentName)}"
          maxlength="50"
          autocomplete="off"
        />
      </section>

      ${recentRoom ? renderRecentRoomCard(recentRoom) : ''}

      <section class="home-actions-section">
        <p class="section-eyebrow">Start watching</p>
        <div class="home-actions-grid">
          <button class="action-card action-card-primary" id="createRoomBtn" type="button">
            <span class="action-card-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
            </span>
            <span class="action-card-copy">
              <strong>Create Room</strong>
              <small>Host and share the code</small>
            </span>
          </button>

          <button class="action-card" id="joinRoomBtn" type="button">
            <span class="action-card-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>
            </span>
            <span class="action-card-copy">
              <strong>Join Room</strong>
              <small>Enter a friend&apos;s code</small>
            </span>
          </button>
        </div>

        <input type="file" id="localPlayFileInput" accept="video/*" hidden />
        <button class="local-play-link" id="localPlayBtn" type="button">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          Local Play for testing tracks and subtitles
        </button>
      </section>

      <a href="https://github.com/nityamkaushik/movsync/releases/latest/download/app-release.apk" class="android-banner android-banner-inline" id="androidDownloadInline">
        <span class="android-banner-icon">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="#3ddc84" stroke="none"><path d="M17.523 15.341c-.551 0-.999-.448-.999-.999s.448-.999.999-.999.999.448.999.999c0 .551-.448.999-.999.999m-11.046 0c-.551 0-.999-.448-.999-.999s.448-.999.999-.999.999.448.999.999-.448.999-.999.999m11.405-6.02 1.997-3.459a.416.416 0 0 0-.72-.416l-2.022 3.503C15.546 8.164 13.845 7.7 12 7.7s-3.546.464-5.137 1.25L4.84 5.447a.416.416 0 0 0-.72.416l1.997 3.459C2.689 11.187.343 14.659 0 18.761h24c-.343-4.102-2.689-7.574-6.118-9.44"/></svg>
        </span>
        <span class="android-banner-text">
          <strong>Download for Android</strong>
          <small>Native playback, file access &amp; smoothest mobile experience</small>
        </span>
        <span class="android-banner-arrow">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 4v12M6 10l6 6 6-6"/></svg>
        </span>
      </a>

      <section class="feature-pills" aria-label="MovSync highlights">
        <span class="feature-pill"><strong>20-50ms</strong> sync</span>
        <span class="feature-pill">Files stay local</span>
        <span class="feature-pill">P2P transfer</span>
        <span class="feature-pill">Android + Web</span>
      </section>

      <footer class="home-footer">
        <span>MovSync Web</span>
        <span>by Team Nityam</span>
      </footer>
    </div>
  `;

  const nameInput = container.querySelector('#displayNameInput');

  const requireName = () => {
    const name = nameInput.value.trim();
    if (!name) {
      nameInput.classList.add('input-error');
      nameInput.focus();
      nameInput.placeholder = 'Please enter your name first!';
      return null;
    }
    saveDisplayName(name);
    return name;
  };

  nameInput.addEventListener('input', () => {
    saveDisplayName(nameInput.value.trim());
  });

  container.querySelector('#createRoomBtn').addEventListener('click', () => {
    if (requireName()) navigate('#/create');
  });

  container.querySelector('#joinRoomBtn').addEventListener('click', () => {
    if (requireName()) navigate('#/join');
  });

  container.querySelector('#recentRoomRejoinBtn')?.addEventListener('click', () => {
    if (requireName()) navigate(`#/lobby/${recentRoom.code}/false`);
  });

  container.querySelector('#recentRoomClearBtn')?.addEventListener('click', () => {
    clearRecentRoom();
    renderHome(container);
  });

  const localPlayBtn = container.querySelector('#localPlayBtn');
  const localPlayFileInput = container.querySelector('#localPlayFileInput');

  localPlayBtn.addEventListener('click', () => {
    localPlayFileInput.click();
  });

  localPlayFileInput.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    window.__movsync_file = file;
    window.__movsync_videoUrl = URL.createObjectURL(file);
    navigate('#/watch/local/true');
  });

  nameInput.addEventListener('focus', () => {
    nameInput.classList.remove('input-error');
    if (nameInput.placeholder === 'Please enter your name first!') {
      nameInput.placeholder = 'Enter your name...';
    }
  });

  // ── Sticky Android download bar ──────────────────────────────────────
  const APK_URL = 'https://github.com/nityamkaushik/movsync/releases/latest/download/app-release.apk';
  const DISMISS_KEY = 'movsync_android_bar_dismissed';

  // Remove any existing sticky bar first (in case of re-render)
  document.getElementById('androidStickyBar')?.remove();

  const stickyBar = document.createElement('a');
  stickyBar.id = 'androidStickyBar';
  stickyBar.href = APK_URL;
  stickyBar.className = 'android-sticky-bar';
  stickyBar.setAttribute('aria-label', 'Download MovSync for Android');
  stickyBar.innerHTML = `
    <span class="sticky-bar-icon">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="#3ddc84" stroke="none">
        <path d="M17.523 15.341c-.551 0-.999-.448-.999-.999s.448-.999.999-.999.999.448.999.999c0 .551-.448.999-.999.999m-11.046 0c-.551 0-.999-.448-.999-.999s.448-.999.999-.999.999.448.999.999-.448.999-.999.999m11.405-6.02 1.997-3.459a.416.416 0 0 0-.72-.416l-2.022 3.503C15.546 8.164 13.845 7.7 12 7.7s-3.546.464-5.137 1.25L4.84 5.447a.416.416 0 0 0-.72.416l1.997 3.459C2.689 11.187.343 14.659 0 18.761h24c-.343-4.102-2.689-7.574-6.118-9.44"/>
      </svg>
    </span>
    <span class="sticky-bar-copy">
      <strong>Download for Android</strong>
      <small>Native app · Better playback · Free</small>
    </span>
    <span class="sticky-bar-btn">Download APK</span>
    <button class="sticky-bar-dismiss" id="androidStickyDismiss" type="button" aria-label="Dismiss">&times;</button>
  `;

  // If previously dismissed this session, start hidden
  if (sessionStorage.getItem(DISMISS_KEY)) {
    stickyBar.classList.add('hidden');
  } else {
    document.body.classList.add('has-sticky-bar');
  }

  document.body.appendChild(stickyBar);

  document.getElementById('androidStickyDismiss')?.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    stickyBar.classList.add('hidden');
    document.body.classList.remove('has-sticky-bar');
    sessionStorage.setItem(DISMISS_KEY, '1');
  });

  // Return cleanup so router removes the bar when leaving home
  return () => {
    document.getElementById('androidStickyBar')?.remove();
    document.body.classList.remove('has-sticky-bar');
  };
}

function renderRecentRoomCard(room) {
  const movieName = room.movieName || 'Movie room';
  return `
    <section class="recent-room-card glass-card">
      <button class="recent-room-dismiss" id="recentRoomClearBtn" type="button" aria-label="Dismiss recent room">&times;</button>
      <div class="recent-room-copy">
        <p class="section-eyebrow">Recent room</p>
        <h3>${escapeHtml(movieName)}</h3>
        <p>${formatRelativeTime(room.timestamp)}</p>
      </div>
      <div class="recent-room-footer">
        <div class="recent-room-code" aria-label="Room code ${escapeAttr(room.code)}">
          ${room.code.split('').map((char) => `<span>${escapeHtml(char)}</span>`).join('')}
        </div>
        <button class="btn btn-gradient recent-room-rejoin" id="recentRoomRejoinBtn" type="button">Rejoin</button>
      </div>
    </section>
  `;
}

function formatRelativeTime(timestamp) {
  const elapsedMs = Date.now() - Number(timestamp || 0);
  if (elapsedMs < 60_000) return 'Just now';

  const minutes = Math.floor(elapsedMs / 60_000);
  if (minutes < 60) return `${minutes} min ago`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hr ago`;

  const days = Math.floor(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str || '';
  return div.innerHTML;
}

function escapeAttr(str) {
  return escapeHtml(str).replace(/"/g, '&quot;');
}
