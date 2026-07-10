/**
 * Lobby Screen
 */

import { navigate } from '../router.js';
import * as firebaseSync from '../firebase-sync.js';
import { ensureSignedIn, getDisplayName } from '../auth.js';
import { computeQuickFingerprint } from '../file-hasher.js';
import { getRoomByCode, verifyParticipant } from '../room-repository.js';
import { createRoomCodeDisplay } from '../components/room-code-display.js';
import { createParticipantAvatar } from '../components/participant-avatar.js';
import { createChatUI } from '../components/chat.js';
import { observeFileShare, publishFileShare } from '../firebase-file-share.js';
import { saveRecentRoom } from '../recent-room.js';
import { uploadToGoFile, getDirectDownloadUrl, triggerNativeDownload, formatBytes } from '../gofile-api.js';

let unsubPresence = null;
let unsubStarted = null;
let unsubChat = null;
let unsubFileShare = null;
let currentMessages = [];
let currentUserId = null;
let currentRoom = null;
let currentFileShare = null;
let isLobbyChatOpen = false;
let lobbyUnreadCount = 0;
let chatMessagesLoaded = false;

let localFileState = {
  status: 'idle',
  message: '',
  progress: 0,
  bytesReceived: 0,
  totalBytes: 0,
};

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

          <div id="fileShareContainer" class="lobby-section stagger-3"></div>
          <input type="file" id="lobbyFileInput" accept="video/*" hidden />
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
    navigate('#/');
  });

  const fileInput = container.querySelector('#lobbyFileInput');
  fileInput.addEventListener('change', async (event) => {
    const file = event.target.files?.[0];
    if (file) await verifyFile(container, code, file);
    fileInput.value = '';
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
  saveRecentRoom({
    code: roomCode,
    movieName: currentRoom?.movie_name,
    isHost,
  });
  renderFileShare(container, roomCode, isHost);

  unsubFileShare = observeFileShare(roomCode, (fileShare) => {
    currentFileShare = fileShare;
    if (localFileState.status === 'idle') {
      localFileState = { ...localFileState, message: '' };
    }
    renderFileShare(container, roomCode, isHost);
  });

  unsubPresence = firebaseSync.observePresence(roomCode, (users) => {
    renderParticipants(container, users, isHost);
  });

  if (!isHost) {
    unsubStarted = firebaseSync.observeRoomStarted(roomCode, (started) => {
      if (!started) return;
      if (!window.__movsync_videoUrl) {
        localFileState = {
          status: 'error',
          message: 'The room started, but this device has not verified a file yet.',
          progress: 0,
          bytesReceived: 0,
          totalBytes: 0,
        };
        renderFileShare(container, roomCode, isHost);
        return;
      }
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

function renderFileShare(container, roomCode, isHost) {
  const hostFile = window.__movsync_file;
  const root = container.querySelector('#fileShareContainer');
  if (!root) return;

  if (isHost) {
    const isSharing = Boolean(currentFileShare);
    root.innerHTML = `
      <div class="glass-card file-share-card">
        <div class="file-share-header">
          <div>
            <h3 class="file-share-title">Movie Sharing</h3>
            <p class="file-share-subtitle">
              ${isSharing
                ? `Uploaded ${escapeHtml(currentFileShare.fileName)} (${formatBytes(currentFileShare.fileSize)})`
                : localFileState.status === 'uploading'
                  ? `Uploading ${escapeHtml(hostFile?.name || '')}… ${localFileState.totalBytes ? Math.round((localFileState.bytesReceived / localFileState.totalBytes) * 100) : 0}%`
                  : hostFile
                    ? `Ready to upload ${escapeHtml(hostFile.name)} (${formatBytes(hostFile.size)})`
                    : 'Return to Create Room to select a movie file.'}
            </p>
          </div>
          <span class="file-share-pill ${isSharing ? 'pill-live' : ''}">${isSharing ? 'Live' : 'Off'}</span>
        </div>
        ${isSharing
          ? `<p class="file-share-status">${escapeHtml(localFileState.message || 'Guests can now download this file from the cloud.')}</p>`
          : localFileState.status === 'uploading'
            ? `<div class="download-progress-bar"><div class="download-progress-fill" style="width:${localFileState.totalBytes ? Math.round((localFileState.bytesReceived / localFileState.totalBytes) * 100) : 0}%"></div></div>`
            : `<button class="btn btn-outline" id="shareFileBtn" ${hostFile ? '' : 'disabled'}>
               <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
               Upload to Cloud
             </button>`}
      </div>
    `;

    root.querySelector('#shareFileBtn')?.addEventListener('click', () => startSharing(container, roomCode));
    return;
  }

  const verified = Boolean(window.__movsync_videoUrl);
  const progressPercent = localFileState.totalBytes
    ? Math.round((localFileState.bytesReceived / localFileState.totalBytes) * 100)
    : Math.round(localFileState.progress * 100);

  root.innerHTML = `
    <div class="glass-card file-share-card">
      <div class="file-share-header">
        <div>
          <h3 class="file-share-title">Movie File</h3>
          <p class="file-share-subtitle">
            ${currentFileShare
              ? `${escapeHtml(currentFileShare.fileName)} (${formatBytes(currentFileShare.fileSize)})`
              : 'Waiting for host to share a movie...'}
          </p>
        </div>
        <span class="file-share-pill ${verified ? 'pill-live' : ''}">${verified ? 'Verified' : 'Needed'}</span>
      </div>

      ${renderGuestFileControls(Boolean(currentFileShare), verified, progressPercent)}
    </div>
  `;

  root.querySelector('#downloadFileBtn')?.addEventListener('click', () => startDownload(container, roomCode));
  root.querySelector('#selectLocalFileBtn')?.addEventListener('click', () => {
    container.querySelector('#lobbyFileInput')?.click();
  });
}

function renderGuestFileControls(hasShare, verified, progressPercent) {
  if (verified) {
    return `<p class="file-share-status success">File verified. You are ready to watch.</p>`;
  }

  if (localFileState.status === 'downloading' || localFileState.status === 'verifying') {
    const text = localFileState.status === 'verifying'
      ? 'Verifying selected file...'
      : `${formatBytes(localFileState.bytesReceived)} / ${formatBytes(localFileState.totalBytes)}`;
    return `
      <p class="file-share-status">${escapeHtml(localFileState.message || text)}</p>
      <div class="download-progress-bar">
        <div class="download-progress-fill" style="width:${Math.max(0, Math.min(progressPercent, 100))}%"></div>
      </div>
    `;
  }

  const error = localFileState.status === 'error'
    ? `<p class="file-share-status error">${escapeHtml(localFileState.message)}</p>`
    : localFileState.message
      ? `<p class="file-share-status">${escapeHtml(localFileState.message)}</p>`
      : '';

  return `
    ${error}
    <div class="file-share-actions">
      <button class="btn btn-gradient" id="downloadFileBtn" ${hasShare ? '' : 'disabled'}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
        Download
      </button>
      <button class="btn btn-outline" id="selectLocalFileBtn">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
        Select File
      </button>
    </div>
  `;
}

async function startSharing(container, roomCode) {
  const file = window.__movsync_file;
  if (!file) {
    localFileState = {
      status: 'error',
      message: 'No host file is available in this browser session.',
      progress: 0,
      bytesReceived: 0,
      totalBytes: 0,
    };
    renderFileShare(container, roomCode, true);
    return;
  }

  try {
    localFileState = {
      status: 'uploading',
      message: 'Uploading to cloud...',
      progress: 0,
      bytesReceived: 0,
      totalBytes: file.size,
    };
    renderFileShare(container, roomCode, true);

    // Throttle DOM re-renders to once per 250 ms (Issue #6 fix)
    let lastRenderTime = 0;
    const result = await uploadToGoFile(file, (uploaded, total) => {
      localFileState = {
        status: 'uploading',
        message: `Uploading ${formatBytes(uploaded)} / ${formatBytes(total)}`,
        progress: total ? uploaded / total : 0,
        bytesReceived: uploaded,
        totalBytes: total,
      };
      const now = Date.now();
      if (now - lastRenderTime > 250) {
        lastRenderTime = now;
        renderFileShare(container, roomCode, true);
      }
    });
    // Always render the final state
    renderFileShare(container, roomCode, true);

    await publishFileShare(roomCode, {
      seederId: currentUserId,
      fileName: file.name,
      fileSize: file.size,
      goFileCode: result.fileCode,
    });

    localFileState = {
      status: 'sharing',
      message: 'Upload complete. Guests can now download.',
      progress: 1,
      bytesReceived: file.size,
      totalBytes: file.size,
    };
    renderFileShare(container, roomCode, true);
  } catch (error) {
    console.error('[file-share] Could not upload file:', error);
    localFileState = {
      status: 'error',
      message: error.message || 'Could not upload file to cloud',
      progress: 0,
      bytesReceived: 0,
      totalBytes: 0,
    };
    renderFileShare(container, roomCode, true);
  }
}

async function startDownload(container, roomCode) {
  if (!currentFileShare) return;

  try {
    localFileState = {
      status: 'downloading',
      message: 'Resolving download link...',
      progress: 0,
      bytesReceived: 0,
      totalBytes: currentFileShare.fileSize,
    };
    renderFileShare(container, roomCode, false);

    // Resolve the GoFile code to a direct download URL (via proxy)
    const downloadUrl = await getDirectDownloadUrl(currentFileShare.goFileCode);

    // Trigger a native browser download — bypasses CORS entirely and
    // avoids holding the file in JS memory (Issue #5 fix).
    triggerNativeDownload(downloadUrl, currentFileShare.fileName);

    localFileState = {
      status: 'idle',
      message: 'Download started in your browser. Once complete, click \"Select File\" to verify it.',
      progress: 0,
      bytesReceived: 0,
      totalBytes: 0,
    };
    renderFileShare(container, roomCode, false);
  } catch (error) {
    console.error('[file-share] Download failed:', error);
    localFileState = {
      status: 'error',
      message: error.message || 'Could not start download. Try selecting a local file instead.',
      progress: 0,
      bytesReceived: 0,
      totalBytes: currentFileShare?.fileSize || 0,
    };
    renderFileShare(container, roomCode, false);
  }
}

async function verifyFile(container, roomCode, file) {
  try {
    localFileState = {
      status: 'verifying',
      message: 'Computing fingerprint...',
      progress: 0,
      bytesReceived: 0,
      totalBytes: file.size,
    };
    renderFileShare(container, roomCode, false);

    if (!currentRoom) currentRoom = await getRoomByCode(roomCode);
    if (!currentRoom?.movie_fingerprint) {
      throw new Error('This room does not have a movie fingerprint to verify against.');
    }

    const fingerprint = await computeQuickFingerprint(file, (progress) => {
      localFileState = {
        status: 'verifying',
        message: `Verifying file (${Math.round(progress * 100)}%)`,
        progress,
        bytesReceived: Math.round(file.size * progress),
        totalBytes: file.size,
      };
      renderFileShare(container, roomCode, false);
    });

    if (fingerprint !== currentRoom.movie_fingerprint) {
      throw new Error('File fingerprint does not match. Select the exact movie file from the host.');
    }

    await verifyParticipant(currentRoom.id, currentRoom.code, currentUserId);
    if (window.__movsync_videoUrl) URL.revokeObjectURL(window.__movsync_videoUrl);
    window.__movsync_file = file;
    window.__movsync_videoUrl = URL.createObjectURL(file);

    localFileState = {
      status: 'verified',
      message: 'File verified. You are ready to watch.',
      progress: 1,
      bytesReceived: file.size,
      totalBytes: file.size,
    };
    renderFileShare(container, roomCode, false);
  } catch (error) {
    console.error('[file-share] Verification failed:', error);
    localFileState = {
      status: 'error',
      message: error.message || 'Could not verify file',
      progress: 0,
      bytesReceived: 0,
      totalBytes: file?.size || 0,
    };
    renderFileShare(container, roomCode, false);
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
  if (unsubFileShare) { unsubFileShare(); unsubFileShare = null; }
  currentMessages = [];
  currentRoom = null;
  currentFileShare = null;
  isLobbyChatOpen = false;
  lobbyUnreadCount = 0;
  chatMessagesLoaded = false;
  localFileState = {
    status: 'idle',
    message: '',
    progress: 0,
    bytesReceived: 0,
    totalBytes: 0,
  };
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str || '';
  return div.innerHTML;
}
