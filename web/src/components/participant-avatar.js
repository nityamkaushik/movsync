/**
 * ParticipantAvatar component — Port of ParticipantAvatar.kt
 */

const AVATAR_COLORS = [
  '#7C3AED', '#06B6D4', '#F59E0B', '#22C55E',
  '#EF4444', '#EC4899', '#8B5CF6', '#14B8A6',
];

export function createParticipantAvatar(displayName, isOnline) {
  const letter = (displayName || '?')[0].toUpperCase();
  const colorIndex = letter.charCodeAt(0) % AVATAR_COLORS.length;
  const color = AVATAR_COLORS[colorIndex];

  const avatar = document.createElement('div');
  avatar.className = 'participant-avatar';
  avatar.style.backgroundColor = color;
  avatar.innerHTML = `
    <span class="avatar-letter">${letter}</span>
    <span class="avatar-status ${isOnline ? 'avatar-online' : 'avatar-offline'}"></span>
  `;
  return avatar;
}
