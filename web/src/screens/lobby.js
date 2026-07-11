/**
 * Lobby Screen
 */

import { navigate } from '../router.js';
import * as firebaseSync from '../firebase-sync.js';
import { ensureSignedIn, getDisplayName } from '../auth.js';
import { getRoomByCode } from '../room-repository.js';
import { createRoomCodeDisplay } from '../components/room-code-display.js';
import { createParticipantAvatar } from '../components/participant-avatar.js';
import { createChatUI } from '../components/chat.js';

let unsubPresence = null;
let unsubStarted = null;
let unsubChat = null;
let currentMessages = [];
let currentUserId = null;
let currentRoom = null;
let isLobbyChatOpen = false;
let lobbyUnreadCount = 0;
let chatMessagesLoaded = false;

export function renderLobby(container, { code, isHost }) {
  const isHostBool = isHost === 'true';
  isLobbyChatOpen = false;
  lobbyUnreadCount = 0;
  chatMessagesLoaded = false;

  container.innerHTML = `
    <div class="screen-container lobby-screen">
      <div class="lobby-ambient-bg"></div>
      <div class="screen-header">
        <button class="btn-icon" id="lobbyBackBtn" aria-label="Back">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        </button>
        <div class="screen-header-text">
          <h2 class="screen-title">Lobby</h2>
          <p class="screen-subtitle">Invite your friends to watch together</p>
        </div>
      </div>

      <div class="lobby-layout-grid">
        <div class="lobby-col-main">
          <div id="roomCodeContainer" class="lobby-section stagger-1"></div>

          <div id="lobbyAction" class="lobby-action lobby-section stagger-2">
            ${isHostBool
              ? `<button class="btn btn-gradient" id="startWatchingBtn" disabled>
                   <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                   Start Watching
                 </button>`
              : `<div class="waiting-host" id="waitingHost">
                   <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                   <span>Waiting for host to start</span>
                 </div>`
            }
          </div>

          <div id="fileShareContainer" class="lobby-section stagger-3">
            <div class="glass-card">
              <p style="text-align:center;color:var(--text-secondary);font-size:0.85rem;">Waiting for host to start playback...</p>
            </div>
          </div>
        </div>

        <div class="lobby-col-side">
          <div class="glass-card lobby-participants-card lobby-section stagger-4">
            <div class="participants-header">
              <h3 class="participants-title">Participants</h3>
              <button class="participants-chat-btn" id="lobbyChatFab" type="button" aria-label="Open room chat">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a4 4 0 0 1-4 4H7l-4 4V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z"/></svg>
                <span class="unread-badge" id="lobbyChatBadge" hidden>0</span>
              </button>
            </div>
            <div id="participantsList" class="participants-list">
              <div class="loading-pulse">Waiting for participants...</div>
            </div>
          </div>
        </div>
      </div>

      <div id="lobbyChatOverlay" class="lobby-chat-overlay closed" aria-hidden="true">
        <div id="lobbyChatContainer"></div>
      </div>
    </div>
  `;

  createRoomCodeDisplay(container.querySelector('#roomCodeContainer'), code);

  container.querySelector('#lobbyBackBtn').addEventListener('click', () => {
    cleanup();
    if (window.__movsync_videoUrl) {
      URL.revokeObjectURL(window.__movsync_videoUrl);
      delete window.__movsync_videoUrl;
    }
    delete window.__movsync_file;
    navigate('#/');
  });

  container.querySelector('#lobbyChatFab')?.addEventListener('click', () => {
    isLobbyChatOpen = !isLobbyChatOpen;
    if (isLobbyChatOpen) lobbyUnreadCount = 0;
    renderChatWidget(container, code);
  });

  init(container, code, isHostBool);

  return cleanup;
}

async function init(container, roomCode, isHost) {
  currentUserId = await ensureSignedIn();
  currentRoom = await getRoomByCode(roomCode);
  await firebaseSync.trackPresence(roomCode, currentUserId, getDisplayName() || 'Movie Friend', isHost, isHost);

  unsubPresence = firebaseSync.observePresence(roomCode, (users) => {
    renderParticipants(container, users, isHost);
  });

  if (!isHost) {
    unsubStarted = firebaseSync.observeRoomStarted(roomCode, (started) => {
      if (!started) return;
      cleanup();
      navigate(`#/watch/${roomCode}/false`);
    });
  }

  unsubChat = firebaseSync.observeChatMessages(roomCode, (messages) => {
    const previousIds = new Set(currentMessages.map((message) => message.messageId));
    currentMessages = messages;
    if (!chatMessagesLoaded) {
      chatMessagesLoaded = true;
    } else if (!isLobbyChatOpen) {
      const newRemoteCount = messages.filter((message) => (
        !previousIds.has(message.messageId) && message.senderId !== currentUserId
      )).length;
      lobbyUnreadCount += newRemoteCount;
    }
    renderChatWidget(container, roomCode);
  });

  if (isHost) {
    const startBtn = container.querySelector('#startWatchingBtn');
    startBtn?.addEventListener('click', async () => {
      await firebaseSync.setRoomStarted(roomCode);
      cleanup();
      navigate(`#/watch/${roomCode}/true`);
    });
  }
}



function renderParticipants(container, users, isHost) {
  const list = container.querySelector('#participantsList');
  if (!list) return;
  list.innerHTML = '';

  let index = 1;
  for (const user of users) {
    const item = document.createElement('div');
    const staggerDelay = Math.min(index++, 5);
    item.className = `participant-item stagger-${staggerDelay} lobby-section`;

    const avatar = createParticipantAvatar(user.displayName, user.online);
    item.appendChild(avatar);

    const info = document.createElement('div');
    info.className = 'participant-info';
    info.innerHTML = `
      <span class="participant-name">${escapeHtml(user.displayName)}</span>
      <span class="participant-status ${user.isHost ? 'status-host' : user.verified ? 'status-verified' : 'status-waiting'}">
        ${user.isHost ? 'Host' : user.verified ? 'Verified' : 'Waiting'}
      </span>
    `;
    item.appendChild(info);
    list.appendChild(item);
  }

  if (isHost) {
    const startBtn = container.querySelector('#startWatchingBtn');
    const verifiedCount = users.filter((user) => user.verified).length;
    if (startBtn) startBtn.disabled = verifiedCount < 2;
  }
}

function renderChatWidget(container, roomCode) {
  const overlay = container.querySelector('#lobbyChatOverlay');
  const fab = container.querySelector('#lobbyChatFab');
  const badge = container.querySelector('#lobbyChatBadge');
  const chatContainer = container.querySelector('#lobbyChatContainer');
  if (!overlay || !chatContainer || !fab || !badge) return;

  overlay.classList.toggle('open', isLobbyChatOpen);
  overlay.classList.toggle('closed', !isLobbyChatOpen);
  overlay.setAttribute('aria-hidden', String(!isLobbyChatOpen));
  fab.classList.toggle('has-unread', lobbyUnreadCount > 0);
  badge.hidden = lobbyUnreadCount <= 0;
  badge.textContent = String(Math.min(lobbyUnreadCount, 99));

  if (!isLobbyChatOpen) return;

  createChatUI(chatContainer, {
    messages: currentMessages,
    currentUserId,
    onSendMessage: async (text) => {
      const displayName = getDisplayName() || 'User';
      const messageId = crypto.randomUUID();
      await firebaseSync.sendMessage(roomCode, {
        messageId,
        senderId: currentUserId,
        senderName: displayName,
        message: text,
      });
    },
    onClose: () => {
      isLobbyChatOpen = false;
      lobbyUnreadCount = 0;
      renderChatWidget(container, roomCode);
    },
    showHeader: true,
  });
}

function cleanup() {
  if (unsubPresence) { unsubPresence(); unsubPresence = null; }
  if (unsubStarted) { unsubStarted(); unsubStarted = null; }
  if (unsubChat) { unsubChat(); unsubChat = null; }
  currentMessages = [];
  currentRoom = null;
  isLobbyChatOpen = false;
  lobbyUnreadCount = 0;
  chatMessagesLoaded = false;
  // File and URL cleanup happens explicitly on back, or in watch.js cleanup when leaving.
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str || '';
  return div.innerHTML;
}
