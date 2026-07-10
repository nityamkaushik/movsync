/**
 * DriftCorrector — Exact port of DriftCorrector.kt
 * 3-tier drift correction for synchronized video playback.
 */

const ACCEPTABLE_DRIFT_MS = 500;
const HARD_SEEK_DRIFT_MS = 3000;

let resetTimer = null;

export function evaluate(currentPosition, expectedPosition) {
  const drift = currentPosition - expectedPosition;
  const magnitude = Math.abs(drift);

  if (magnitude < ACCEPTABLE_DRIFT_MS) {
    return { type: 'inSync', drift };
  } else if (magnitude < HARD_SEEK_DRIFT_MS) {
    const speed = drift < 0 ? 1.12 : 0.88;
    const durationMs = magnitude / 0.12;
    return { type: 'softCorrect', drift, speed, durationMs };
  } else {
    return { type: 'hardSeek', drift, targetPosition: expectedPosition };
  }
}

export function applyDriftCorrection(video, expectedPosition, isPaused, onStatus) {
  const currentPosition = video.currentTime * 1000;
  const action = evaluate(currentPosition, expectedPosition);

  if (isPaused) {
    const drift = Math.abs(currentPosition - expectedPosition);
    if (drift < HARD_SEEK_DRIFT_MS && onStatus) onStatus('synced');
    return;
  }

  switch (action.type) {
    case 'inSync':
      if (onStatus) onStatus('synced');
      break;

    case 'softCorrect':
      video.playbackRate = action.speed;
      if (onStatus) onStatus('correcting');
      if (resetTimer) clearTimeout(resetTimer);
      resetTimer = setTimeout(() => {
        video.playbackRate = 1.0;
        if (onStatus) onStatus('synced');
      }, Math.min(Math.max(action.durationMs, 250), 6000));
      break;

    case 'hardSeek':
      video.currentTime = action.targetPosition / 1000;
      video.playbackRate = 1.0;
      if (onStatus) onStatus('correcting');
      break;
  }
}

export function cleanup() {
  if (resetTimer) {
    clearTimeout(resetTimer);
    resetTimer = null;
  }
}
