const INSYNC_THRESHOLD_MS = 50;
const SOFT_SEEK_THRESHOLD_MS = 1500;
const PROPORTIONAL_GAIN = 1 / 5000;
const MIN_SPEED = 0.85;
const MAX_SPEED = 1.15;
const SMOOTH_FACTOR = 0.5;

let smoothedDrift = 0;

export function evaluate(currentPosition, expectedPosition) {
  const rawDrift = currentPosition - expectedPosition;
  const magnitude = Math.abs(rawDrift);

  if (magnitude < INSYNC_THRESHOLD_MS) {
    smoothedDrift = 0;
    return { type: 'inSync', driftMs: rawDrift };
  }

  smoothedDrift = SMOOTH_FACTOR * rawDrift + (1 - SMOOTH_FACTOR) * smoothedDrift;
  const drift = Math.round(smoothedDrift);
  const mag = Math.abs(drift);

  if (mag < SOFT_SEEK_THRESHOLD_MS) {
    const speedAdjust = Math.min(Math.max(-drift * PROPORTIONAL_GAIN, MIN_SPEED - 1), MAX_SPEED - 1);
    const speed = Math.min(Math.max(1 + speedAdjust, MIN_SPEED), MAX_SPEED);
    return { type: 'softCorrect', driftMs: drift, speed };
  } else {
    return { type: 'softSeek', driftMs: drift, expectedPosition };
  }
}

export function applyDriftCorrection(video, expectedPositionMs, isPaused, onStatus) {
  const currentPosition = video.currentTime * 1000;
  const action = evaluate(currentPosition, expectedPositionMs);

  if (isPaused) return;

  switch (action.type) {
    case 'inSync':
      video.playbackRate = 1.0;
      if (onStatus) onStatus('synced');
      break;

    case 'softCorrect':
      video.playbackRate = action.speed;
      if (onStatus) onStatus('correcting');
      break;

    case 'softSeek':
      const currentMs = video.currentTime * 1000;
      const targetMs = currentMs + (action.expectedPosition - currentMs) * 0.8;
      video.currentTime = Math.max(targetMs / 1000, 0);
      video.playbackRate = 1.0;
      if (onStatus) onStatus('correcting');
      break;
  }
}

export function resetDriftOnCommand() {
  smoothedDrift = 0;
}

export function cleanup() {
  smoothedDrift = 0;
}
