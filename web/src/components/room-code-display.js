/**
 * RoomCodeDisplay component — Port of RoomCodeDisplay.kt
 */

export function createRoomCodeDisplay(container, roomCode) {
  container.innerHTML = `
    <div class="room-code-display">
      <span class="room-code-label">Room Code</span>
      <div class="room-code-value" id="roomCodeValue">
        ${roomCode.split('').map(c => `<span class="room-code-char">${c}</span>`).join('')}
      </div>
      <button class="room-code-copy-btn" id="copyRoomCode">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
        <span id="copyText">Copy</span>
      </button>
    </div>
  `;

  container.querySelector('#copyRoomCode').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(roomCode);
      const copyText = container.querySelector('#copyText');
      copyText.textContent = 'Copied!';
      setTimeout(() => { copyText.textContent = 'Copy'; }, 2000);
    } catch {
      // Fallback
      const el = document.createElement('textarea');
      el.value = roomCode;
      document.body.appendChild(el);
      el.select();
      document.execCommand('copy');
      document.body.removeChild(el);
    }
  });
}
