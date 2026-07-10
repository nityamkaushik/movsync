/**
 * SyncEngine — Port of SyncEngine.kt
 * Manages playback sync between host and participants via Firebase RTDB.
 */

import * as firebaseSync from './firebase-sync.js';
import { cleanup as cleanupDrift, resetDriftOnCommand } from './drift-corrector.js';

let heartbeatInterval = null;
let rttInterval = null;
let unsubSync = null;
let unsubHeartbeat = null;
let applyingRemoteCommand = false;

/**
 * Start the sync engine for a room.
 * @param {Object} opts
 * @param {string} opts.roomCode
 * @param {string} opts.userId
 * @param {boolean} opts.isHost
 * @param {HTMLVideoElement} opts.video
 * @param {Function} opts.onStatus - callback(status: 'synced'|'correcting'|'reconnecting')
 */
export function start({ roomCode, userId, isHost, video, onStatus }) {
  stop(roomCode);
  firebaseSync.getServerTime(); // Ensure server time offset starts observing

  // 1. Listen to sync commands (both host & participant)
  unsubSync = firebaseSync.listenToSync(roomCode, (state) => {
    if (state.senderId === userId) return;
    applyingRemoteCommand = true;
    resetDriftOnCommand();
    switch (state.command) {
      case 'play':
        video.currentTime = state.position / 1000;
        video.play().catch(() => {});
        break;
      case 'pause':
        video.currentTime = state.position / 1000;
        video.pause();
        break;
      case 'seek':
        video.currentTime = state.position / 1000;
        break;
    }
    if (state.speed && state.speed > 0) {
      video.playbackRate = state.speed;
    }
    applyingRemoteCommand = false;
  });

  // 2. Heartbeat & Drift Correction
  if (isHost) {
    // Host writes heartbeat every 500ms
    heartbeatInterval = setInterval(() => {
      firebaseSync.writeHeartbeat(roomCode, {
        position: Math.round(video.currentTime * 1000),
        isPlaying: !video.paused,
      });
    }, 500);
  } else {
    // Initial RTT measurement
    firebaseSync.measureRtt(roomCode, userId);
    // Periodic RTT re-measurement
    rttInterval = setInterval(() => {
      firebaseSync.measureRtt(roomCode, userId);
    }, 30000);

    // Participant listens to heartbeat and applies drift correction
    unsubHeartbeat = firebaseSync.listenToHeartbeat(roomCode, (heartbeat) => {
      if (applyingRemoteCommand) return;

      const oneWayLatency = firebaseSync.getOneWayLatency();
      const serverNow = firebaseSync.getServerTime();
      const adjustedPosition = heartbeat.position + (serverNow - heartbeat.timestamp) + oneWayLatency;

      if (heartbeat.isPlaying && video.paused) {
        video.play().catch(() => {});
      } else if (!heartbeat.isPlaying && !video.paused) {
        video.pause();
      }

      applyDriftCorrection(video, adjustedPosition, !heartbeat.isPlaying, onStatus);
    });

    // Late joiner: fetch heartbeat once and catch up
    firebaseSync.getHeartbeatOnce(roomCode).then((heartbeat) => {
      if (heartbeat) {
        const oneWayLatency = firebaseSync.getOneWayLatency();
        const adjustedPosition = heartbeat.position + (firebaseSync.getServerTime() - heartbeat.timestamp) + oneWayLatency;
        video.currentTime = Math.max(adjustedPosition / 1000, 0);
        if (heartbeat.isPlaying) {
          video.play().catch(() => {});
        } else {
          video.pause();
        }
      }
    });
  }
}

/**
 * Stop the sync engine and clean up listeners.
 */
export function stop(roomCode) {
  if (heartbeatInterval) {
    clearInterval(heartbeatInterval);
    heartbeatInterval = null;
  }
  if (rttInterval) {
    clearInterval(rttInterval);
    rttInterval = null;
  }
  if (unsubSync) {
    unsubSync();
    unsubSync = null;
  }
  if (unsubHeartbeat) {
    unsubHeartbeat();
    unsubHeartbeat = null;
  }
  cleanupDrift();
  applyingRemoteCommand = false;
}

/**
 * Broadcast a sync command to the room.
 */
export async function broadcastCommand(roomCode, userId, command, positionMs, speed = 1) {
  await firebaseSync.writeSyncCommand(roomCode, {
    command,
    position: positionMs,
    speed,
    senderId: userId,
  });
}

/**
 * Check if a remote command is currently being applied (to avoid echo).
 */
export function isApplyingRemote() {
  return applyingRemoteCommand;
}
