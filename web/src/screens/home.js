/**
 * Home Screen
 */

import { getOrCreateDisplayName, saveDisplayName, generateRandomName } from '../auth.js';
import { navigate } from '../router.js';
import { isConfigured } from '../config.js';
import { clearRecentRoom, getRecentRoom } from '../recent-room.js';

export function renderHome(container) {
  const displayName = getOrCreateDisplayName();
  const configured = isConfigured();
  const recentRoom = getRecentRoom();

  container.innerHTML = `
    <!-- TOP BAR -->
    <header class="top-bar">
      <div class="top-bar-left">
        <img src="/logo.jpg" alt="" class="top-bar-logo" />
        <span class="top-bar-brand">MovSync</span>
      </div>
      <div class="top-bar-center">
        <span class="top-bar-name" id="topBarName">${escapeHtml(displayName)}</span>
        <button class="top-bar-name-edit" id="editNameBtn" aria-label="Edit name">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
          </svg>
        </button>
      </div>
      <div class="top-bar-right">
        <a href="https://github.com/nityamkaushik/movsync/releases/latest/download/app-release.apk"
           class="top-bar-android" target="_blank" rel="noopener">
          Android ↓
        </a>
      </div>
    </header>

    <!-- LAPTOP/TABLET LAYOUT -->
    <main class="home-desktop">
      <div class="home-action-grid">
        <button class="home-action-card home-action-card-primary" id="createRoomBtn" type="button">
          <span class="home-action-icon home-action-icon-purple">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="16"/>
              <line x1="8" y1="12" x2="16" y2="12"/>
            </svg>
          </span>
          <span class="home-action-copy">
            <strong>Create Room</strong>
            <small>Host a watch party</small>
          </span>
        </button>
        <button class="home-action-card" id="joinRoomBtnDesktop" type="button">
          <span class="home-action-icon home-action-icon-cyan">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/>
              <polyline points="10 17 15 12 10 7"/>
              <line x1="15" y1="12" x2="3" y2="12"/>
            </svg>
          </span>
          <span class="home-action-copy">
            <strong>Join Room</strong>
            <small>Enter a code to join</small>
          </span>
        </button>
      </div>
      <input type="file" id="localPlayFileInput" accept="video/*" hidden />
      <button class="home-local-play" id="localPlayBtnDesktop" type="button">
        Local Play for testing →
      </button>
    </main>

    <!-- PHONE LAYOUT -->
    <main class="home-phone">
      <div class="home-phone-header">
        <img src="/logo.jpg" alt="" class="home-phone-logo" />
        <span class="home-phone-brand">MovSync</span>
        <div class="home-phone-name-row">
          <span class="home-phone-name" id="phoneName">${escapeHtml(displayName)}</span>
          <button class="home-phone-name-edit" id="editNameBtnPhone" aria-label="Edit name">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
            </svg>
          </button>
        </div>
      </div>
      <div class="home-phone-actions">
        <button class="home-phone-btn home-phone-btn-primary" id="createRoomBtnPhone" type="button">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="16"/>
            <line x1="8" y1="12" x2="16" y2="12"/>
          </svg>
          Create Room
        </button>
        <button class="home-phone-btn home-phone-btn-secondary" id="joinRoomBtnPhone" type="button">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/>
            <polyline points="10 17 15 12 10 7"/>
            <line x1="15" y1="12" x2="3" y2="12"/>
          </svg>
          Join Room
        </button>
      </div>
      <div class="home-phone-footer">
        <input type="file" id="localPlayFileInputPhone" accept="video/*" hidden />
        <button class="home-phone-local" id="localPlayBtnPhone" type="button">
          Local Play for testing →
        </button>
        <a href="https://github.com/nityamkaushik/movsync/releases/latest/download/app-release.apk"
           class="home-phone-android" target="_blank" rel="noopener">
          Android App ↓
        </a>
      </div>
    </main>
  `;

  // ── Config warning ─────────────────────────────────────
  if (!configured) {
    const warning = document.createElement('div');
    warning.className = 'config-warning';
    warning.innerHTML = `
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
      <div>
        <strong>Backend not configured</strong>
        <p>Create a <code>.env</code> file with your Firebase & Supabase credentials.</p>
      </div>
    `;
    container.querySelector('.top-bar').after(warning);
  }

  // ── Name editing ───────────────────────────────────────
  setupNameEditing(container);

  // ── Navigation handlers ────────────────────────────────
  const requireName = () => {
    const name = getOrCreateDisplayName();
    return name;
  };

  // Desktop buttons
  container.querySelector('#createRoomBtn')?.addEventListener('click', () => {
    if (requireName()) navigate('#/create');
  });
  container.querySelector('#joinRoomBtnDesktop')?.addEventListener('click', () => {
    if (requireName()) navigate('#/join');
  });

  // Phone buttons
  container.querySelector('#createRoomBtnPhone')?.addEventListener('click', () => {
    if (requireName()) navigate('#/create');
  });
  container.querySelector('#joinRoomBtnPhone')?.addEventListener('click', () => {
    if (requireName()) navigate('#/join');
  });

  // Local play (both layouts)
  setupLocalPlay(container, '#localPlayBtnDesktop', '#localPlayFileInput');
  setupLocalPlay(container, '#localPlayBtnPhone', '#localPlayFileInputPhone');

  // ── Recent room ────────────────────────────────────────
  if (recentRoom) {
    renderRecentRoomToast(container, recentRoom);
  }
}

function setupNameEditing(container) {
  const editNameBtn = container.querySelector('#editNameBtn');
  const editNameBtnPhone = container.querySelector('#editNameBtnPhone');
  const topBarName = container.querySelector('#topBarName');
  const phoneName = container.querySelector('#phoneName');

  const startEditing = (targetEl, isPhone) => {
    const currentName = getOrCreateDisplayName();
    const input = document.createElement('input');
    input.type = 'text';
    input.className = isPhone ? 'phone-name-input' : 'top-bar-name-input';
    input.value = currentName;
    input.maxLength = 50;

    targetEl.replaceWith(input);
    input.focus();
    input.select();

    const save = () => {
      const newName = input.value.trim() || generateRandomName();
      saveDisplayName(newName);

      if (isPhone) {
        const span = document.createElement('span');
        span.className = 'home-phone-name';
        span.id = 'phoneName';
        span.textContent = newName;
        input.replaceWith(span);
      } else {
        const span = document.createElement('span');
        span.className = 'top-bar-name';
        span.id = 'topBarName';
        span.textContent = newName;
        input.replaceWith(span);
      }

      if (phoneName) phoneName.textContent = newName;
      if (topBarName) topBarName.textContent = newName;
    };

    input.addEventListener('blur', save);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') input.blur();
    });
  };

  editNameBtn?.addEventListener('click', () => startEditing(topBarName, false));
  editNameBtnPhone?.addEventListener('click', () => startEditing(phoneName, true));
}

function setupLocalPlay(container, btnSelector, inputSelector) {
  const btn = container.querySelector(btnSelector);
  const input = container.querySelector(inputSelector);
  if (!btn || !input) return;

  btn.addEventListener('click', () => input.click());
  input.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    window.__movsync_file = file;
    window.__movsync_videoUrl = URL.createObjectURL(file);
    navigate('#/watch/local/true');
  });
}

function renderRecentRoomToast(container, room) {
  const toast = document.createElement('div');
  toast.className = 'recent-toast';
  toast.innerHTML = `
    <span class="recent-toast-text">Rejoin ${escapeHtml(room.movieName || 'room')}?</span>
    <button class="recent-toast-btn" id="recentRejoinBtn" type="button">Join</button>
    <button class="recent-toast-dismiss" id="recentDismissBtn" type="button">&times;</button>
  `;
  container.appendChild(toast);

  container.querySelector('#recentRejoinBtn')?.addEventListener('click', () => {
    navigate(`#/lobby/${room.code}/false`);
  });
  container.querySelector('#recentDismissBtn')?.addEventListener('click', () => {
    clearRecentRoom();
    toast.remove();
  });
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str || '';
  return div.innerHTML;
}
