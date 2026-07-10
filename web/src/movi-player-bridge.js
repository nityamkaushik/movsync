import { MoviPlayer } from 'movi-player/player';

let player = null;
let canvasEl = null;
let subtitleOverlay = null;
let onTimeUpdate = null;
let onPlayingStateChange = null;
let onSeek = null;
let ready = false;

export function initPlayer(canvas, file) {
  canvasEl = canvas;

  subtitleOverlay = document.createElement('div');
  subtitleOverlay.className = 'movi-subtitle-overlay';
  subtitleOverlay.style.cssText =
    'position:absolute;bottom:90px;left:0;right:0;display:flex;flex-direction:column;align-items:center;text-align:center;pointer-events:none;z-index:10;' +
    'font-size:1.55rem;font-weight:600;color:white;text-shadow:0 1px 2px black, 0 2px 6px black, 0 0 2px black;font-family:-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif;letter-spacing:0.3px;';
  canvas.parentElement?.appendChild(subtitleOverlay);

  // We add a global style to ensure any children added by MoviPlayer are also centered
  // and get the nice black translucent background like the old subtitle-text class.
  const style = document.createElement('style');
  style.textContent = `
    .movi-subtitle-overlay > * {
      display: inline-block !important;
      padding: 4px 8px !important;
      margin: 2px 0 !important;
      -webkit-text-stroke: 1px black;
      paint-order: stroke fill;
    }
  `;
  document.head.appendChild(style);

  player = new MoviPlayer({
    source: { type: 'file', file },
    canvas,
    renderer: 'canvas',
  });

  player.on('timeUpdate', () => {
    if (onTimeUpdate) onTimeUpdate(player.getCurrentTime());
  });

  player.on('stateChange', (state) => {
    if (onPlayingStateChange) {
      if (state === 'playing') {
        onPlayingStateChange(false);
      } else if (state === 'paused' || state === 'idle' || state === 'ready') {
        onPlayingStateChange(true);
      }
    }
  });

  player.on('seeking', () => {
    if (onSeek) onSeek(player.getCurrentTime());
  });

  player.on('seeked', () => {
    if (onSeek) onSeek(player.getCurrentTime());
  });

  return player;
}

export async function loadPlayer() {
  if (!player) return;
  await player.load();
  ready = true;
  player.setSubtitleOverlay(subtitleOverlay);
}

export function isReady() {
  return ready;
}

export function getPlayer() {
  return player;
}

export function destroyPlayer() {
  if (player) {
    try { player.destroy(); } catch (_) {}
    player = null;
  }
  if (subtitleOverlay && subtitleOverlay.parentElement) {
    subtitleOverlay.parentElement.removeChild(subtitleOverlay);
  }
  canvasEl = null;
  subtitleOverlay = null;
  onTimeUpdate = null;
  onPlayingStateChange = null;
  onSeek = null;
  ready = false;
}

export function play() {
  if (!player) return Promise.resolve();
  const p = player.play();
  return (p instanceof Promise) ? p : Promise.resolve();
}

export function pause() {
  if (!player) return;
  player.pause();
}

export function getCurrentTime() {
  return player ? player.getCurrentTime() : 0;
}

export function setCurrentTime(time) {
  if (!player) return;
  player.seek(time);
}

export function getDuration() {
  return player ? player.getDuration() : 0;
}

export function isPaused() {
  if (!player) return true;
  const s = player.getState();
  return s === 'paused' || s === 'idle' || s === 'ready';
}

export function getPlaybackRate() {
  return player ? player.getPlaybackRate() : 1;
}

export function setPlaybackRate(rate) {
  if (player) player.setPlaybackRate(rate);
}

export function getVolume() {
  return player ? player.getVolume() : 1;
}

export function setVolume(val) {
  if (player) player.setVolume(val);
}

export function isMuted() {
  return player ? player.getMuted() : false;
}

export function setMuted(val) {
  if (player) player.setMuted(val);
}

export function resizeCanvas(w, h) {
  if (!player) return;
  if (w !== undefined && h !== undefined) {
    player.resizeCanvas(w, h);
  } else if (canvasEl) {
    const rect = canvasEl.getBoundingClientRect();
    if (rect.width > 0 && rect.height > 0) {
      player.resizeCanvas(
        Math.round(rect.width * devicePixelRatio),
        Math.round(rect.height * devicePixelRatio)
      );
    }
  }
}

export function getAudioTracks() {
  if (!player) return [];
  try { return player.getAudioTracks(); } catch (_) { return []; }
}

export function getCurrentAudioTrackId() {
  return null;
}

export function setAudioTrack(trackId) {
  if (!player) return;
  if (typeof trackId === 'number') {
    player.selectAudioTrack(trackId);
  }
}

export function getSubtitleTracks() {
  if (!player) return [];
  try { return player.getSubtitleTracks(); } catch (_) { return []; }
}

export function getCurrentSubtitleTrackId() {
  return null;
}

export function setSubtitleTrack(trackId) {
  if (!player) return;
  if (trackId === null || trackId === undefined) {
    player.selectSubtitleTrack(null);
  } else if (typeof trackId === 'number') {
    player.selectSubtitleTrack(trackId);
  }
}

export function setSubtitleDelay(delay) {
  if (player) player.setSubtitleDelay(delay);
}

export function loadExternalSubtitle(urlOrFile, lang, label) {
  if (!player) return;
  player.selectSubtitleLang(lang || 'und');
}

export function getSubtitleLangs() {
  if (!player) return [];
  try { return player.getSubtitleLangs(); } catch (_) { return []; }
}

export function getState() {
  return player ? player.getState() : 'idle';
}

export function onTimeUpdateCallback(cb) {
  onTimeUpdate = cb;
}

export function onPlayStateChangeCallback(cb) {
  onPlayingStateChange = cb;
}

export function onSeekCallback(cb) {
  onSeek = cb;
}