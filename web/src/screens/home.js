/**
 * Home Screen — Port of HomeScreen.kt
 */

import { getDisplayName, saveDisplayName } from '../auth.js';
import { navigate } from '../router.js';
import { isConfigured } from '../config.js';

export function renderHome(container) {
  const currentName = getDisplayName();

  const configured = isConfigured();

  container.innerHTML = `
    <div class="home-layout">
      <div class="home-main">
        ${!configured ? `
          <div class="config-warning">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            <div>
              <strong>Backend not configured</strong>
              <p>Create a <code>.env</code> file in the <code>web/</code> folder with your Firebase &amp; Supabase credentials. See <code>.env.example</code> for the template.</p>
            </div>
          </div>
        ` : ''}
        <div class="home-hero">
          <div class="hero-glow"></div>
          <div class="hero-icon">
            <img src="/logo.jpg" alt="MovieSync Logo" class="hero-logo" />
          </div>

          <h1 class="hero-title">Movie<span class="hero-title-accent">Sync</span></h1>
          <p class="hero-subtitle">Watch together, from anywhere</p>
          <p class="hero-desc">Select the same movie file on each device.<br>No streaming. No uploads. Just flawless sync.</p>
        </div>

        <div class="home-card glass-card">
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
          <div class="home-actions">
            <button class="btn btn-gradient" id="createRoomBtn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
              Create Room
            </button>
            <button class="btn btn-outline" id="joinRoomBtn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>
              Join Room
            </button>
          </div>
          <div class="home-actions" style="margin-top: 16px;">
            <input type="file" id="localPlayFileInput" accept="video/*" hidden />
            <button class="btn btn-outline" id="localPlayBtn" style="border-color: #8B5CF6; color: #C084FC; display: flex; justify-content: center; align-items: center; gap: 8px; width: 100%;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>
              Local Play (Test Tracks/Subs)
            </button>
          </div>
        </div>
      </div>

      <div class="home-sidebar">
        <a href="https://github.com/nityamkaushik/movsync/releases/latest/download/app-release.apk" class="home-card glass-card download-android-card" style="display: flex; align-items: center; justify-content: space-between; text-decoration: none; cursor: pointer; transition: transform 0.2s, box-shadow 0.2s;">
          <div>
            <h3 style="color: white; margin: 0 0 0.5rem 0; font-size: 1.15rem;">Download for Android</h3>
            <p style="color: rgba(255, 255, 255, 0.7); margin: 0; font-size: 0.85rem;">Get the native app for the best experience</p>
          </div>
          <div style="background: rgba(61, 220, 132, 0.2); padding: 12px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-left: 12px;">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="#3ddc84" stroke="none"><path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993.0004.5511-.4482.9997-.9993.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993 0 .5511-.4482.9997-.9993.9997m11.4045-6.02l1.9973-3.4592a.416.416 0 0 0-.1521-.5676.416.416 0 0 0-.5676.1521l-2.022 3.503C15.5458 8.1636 13.8447 7.7 12 7.7c-1.8447 0-3.5458.4636-5.1373 1.25l-2.022-3.503a.416.416 0 0 0-.5676-.1521.416.416 0 0 0-.1521.5676l1.9973 3.4592C2.6889 11.1867.3432 14.6589 0 18.761h24c-.3432-4.1021-2.6889-7.5743-6.1185-9.4396"/></svg>
          </div>
        </a>

        <div class="home-features">
          <div class="feature-item">
            <div class="feature-icon feature-icon-purple">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
            </div>
            <div>
              <h3 class="feature-title">Zero-Latency Sync</h3>
              <p class="feature-desc">~20-50ms real-time sync via Firebase</p>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon feature-icon-cyan">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </div>
            <div>
              <h3 class="feature-title">100% Private</h3>
              <p class="feature-desc">Your files never leave your device</p>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon feature-icon-gold">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="15" rx="2" ry="2"/><polyline points="17 2 12 7 7 2"/></svg>
            </div>
            <div>
              <h3 class="feature-title">Cross-Platform</h3>
              <p class="feature-desc">Works with Android app & web browser</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;

  const nameInput = container.querySelector('#displayNameInput');
  nameInput.addEventListener('input', () => {
    saveDisplayName(nameInput.value.trim());
  });

  container.querySelector('#createRoomBtn').addEventListener('click', () => {
    const name = nameInput.value.trim();
    if (!name) {
      nameInput.classList.add('input-error');
      nameInput.focus();
      nameInput.placeholder = 'Please enter your name first!';
      return;
    }
    saveDisplayName(name);
    navigate('#/create');
  });

  container.querySelector('#joinRoomBtn').addEventListener('click', () => {
    const name = nameInput.value.trim();
    if (!name) {
      nameInput.classList.add('input-error');
      nameInput.focus();
      nameInput.placeholder = 'Please enter your name first!';
      return;
    }
    saveDisplayName(name);
    navigate('#/join');
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
}

function escapeAttr(str) {
  return str.replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
