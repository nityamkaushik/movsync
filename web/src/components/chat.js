/**
 * Chat component — Port of ChatUI.kt
 * Renders a chat panel with messages and input.
 */

export function createChatUI(container, { messages, currentUserId, onSendMessage, onClose, showHeader = true }) {
  container.innerHTML = '';
  container.className = 'chat-container';

  let html = '';

  if (showHeader && onClose) {
    html += `
      <div class="chat-header">
        <span class="chat-header-title">Room Chat</span>
        <button class="chat-close-btn" id="chatCloseBtn">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    `;
  }

  html += `<div class="chat-messages" id="chatMessages">`;
  if (messages.length === 0) {
    html += `<div class="chat-empty">No messages yet</div>`;
  }
  for (const msg of messages) {
    const isMe = msg.senderId === currentUserId;
    html += `
      <div class="chat-bubble-row ${isMe ? 'chat-bubble-right' : 'chat-bubble-left'}">
        <div class="chat-bubble ${isMe ? 'chat-bubble-mine' : 'chat-bubble-other'}">
          ${!isMe ? `<span class="chat-sender">${escapeHtml(msg.senderName)}</span>` : ''}
          <span class="chat-text">${escapeHtml(msg.message)}</span>
        </div>
      </div>
    `;
  }
  html += `</div>`;

  html += `
    <div class="chat-input-row">
      <input type="text" class="chat-input" id="chatInput" placeholder="Type a message..." maxlength="500" />
      <button class="chat-send-btn" id="chatSendBtn">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
      </button>
    </div>
  `;

  container.innerHTML = html;

  // Auto-scroll to bottom
  const messagesEl = container.querySelector('#chatMessages');
  if (messagesEl) {
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  // Event handlers
  const input = container.querySelector('#chatInput');
  const sendBtn = container.querySelector('#chatSendBtn');
  const closeBtn = container.querySelector('#chatCloseBtn');

  function send() {
    const text = input.value.trim();
    if (text) {
      onSendMessage(text);
      input.value = '';
    }
  }

  sendBtn?.addEventListener('click', send);
  input?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  });
  closeBtn?.addEventListener('click', () => onClose?.());
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}
