/**
 * Lobby Screen — Port of LobbyScreen.kt + LobbyViewModel.kt
 */

import { navigate } from '../router.js';
import * as firebaseSync from '../firebase-sync.js';
import { ensureSignedIn, getDisplayName } from '../auth.js';
import { createRoomCodeDisplay } from '../components/room-code-display.js';
import { createParticipantAvatar } from '../components/participant-avatar.js';
import { createChatUI } from '../components/chat.js';

let unsubPresence = null;
let unsubStarted = null;
let unsubChat = null;
let currentMessages = [];
let currentUserId = null;

export function renderLobby(container, { code, isHost }) {
  const isHostBool = isHost === 'true';

  container.innerHTML = `
    <div class="screen-container lobby-screen">
      <div class="screen-header">
        <button class="btn-icon" id="lobbyBackBtn">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        </button>
        <h2 class="screen-title">Lobby</h2>
      </div>

      <div id="roomCodeContainer"></div>

      <div class="glass-card lobby-participants-card">
        <h3 class="participants-title">Participants</h3>
        <div id="participantsList" class="participants-list">
          <div class="loading-pulse">Waiting for participants...</div>
        </div>
      </div>

      <div id="lobbyAction" class="lobby-action">
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

      <div class="lobby-chat-section">
        <div id="lobbyChatContainer" class="lobby-chat-container"></div>
      </div>
    </div>
  `;

  // Room code display
  createRoomCodeDisplay(container.querySelector('#roomCodeContainer'), code);

  container.querySelector('#lobbyBackBtn').addEventListener('click', () => {
    cleanup();
    navigate('#/');
  });

  // Initialize
  init(container, code, isHostBool);

  return cleanup;
}

async function init(container, roomCode, isHost) {
  currentUserId = await ensureSignedIn();

  // Listen to presence
  unsubPresence = firebaseSync.observePresence(roomCode, (users) => {
    renderParticipants(container, users, isHost);
  });

  // Listen to room started (for participants)
  if (!isHost) {
    unsubStarted = firebaseSync.observeRoomStarted(roomCode, (started) => {
      if (started) {
        cleanup();
        navigate(`#/watch/${roomCode}/false`);
      }
    });
  }

  // Listen to chat
  unsubChat = firebaseSync.observeChatMessages(roomCode, (messages) => {
    currentMessages = messages;
    renderChat(container, roomCode);
  });

  // Host start button
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

  for (const user of users) {
    const item = document.createElement('div');
    item.className = 'participant-item';

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

  // Update start button (need >= 2 verified)
  if (isHost) {
    const startBtn = container.querySelector('#startWatchingBtn');
    const verifiedCount = users.filter(u => u.verified).length;
    if (startBtn) startBtn.disabled = verifiedCount < 2;
  }
}

function renderChat(container, roomCode) {
  const chatContainer = container.querySelector('#lobbyChatContainer');
  if (!chatContainer) return;

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
    showHeader: false,
  });
}

function cleanup() {
  if (unsubPresence) { unsubPresence(); unsubPresence = null; }
  if (unsubStarted) { unsubStarted(); unsubStarted = null; }
  if (unsubChat) { unsubChat(); unsubChat = null; }
  currentMessages = [];
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}
