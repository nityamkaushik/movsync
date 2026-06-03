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
    <div class="screen-container home-screen">
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
      </div>

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
