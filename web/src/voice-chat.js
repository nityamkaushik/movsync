import { Room, RoomEvent, Track } from 'livekit-client';
import { livekitConfig } from './config.js';
import { observeVoiceActive, setVoiceActive } from './firebase-sync.js';
import { getSupabase } from './supabase-client.js';

const TOKEN_EXPIRY_BUFFER_MS = 30_000;

let state = 'disconnected';
let peerConnected = false;
let room = null;
let cachedToken = null;
let credentials = null;
let prefetchPromise = null;
let operationId = 0;
let unsubVoiceActive = null;
let pendingLocalVoiceActive = null;
let voiceSyncAction = null;

const stateListeners = new Set();
const peerListeners = new Set();
const audioElements = new Set();

export function prefetchToken(roomCode, displayName, userId) {
  if (!roomCode || !userId) return Promise.resolve(null);

  const nextCredentials = { roomCode, displayName, userId };
  const changed = !sameCredentials(credentials, nextCredentials);
  credentials = nextCredentials;

  if (!changed && isTokenValid(cachedToken)) {
    return Promise.resolve(cachedToken);
  }
  if (changed) cachedToken = null;

  const requestedCredentials = { ...nextCredentials };
  prefetchPromise = fetchToken(requestedCredentials)
    .then((token) => {
      if (sameCredentials(credentials, requestedCredentials)) {
        cachedToken = token;
      }
      return token;
    })
    .catch((error) => {
      console.warn('Voice token prefetch failed; connect will retry.', error);
      return null;
    });

  return prefetchPromise;
}

export async function connectVoice() {
  if (state !== 'disconnected') return;
  if (!credentials) throw new Error('Voice chat has not been initialized.');
  if (!livekitConfig.url) throw new Error('VITE_LIVEKIT_URL is not configured.');

  const currentOperation = ++operationId;
  setState('connecting');

  try {
    await prefetchPromise;
    if (currentOperation !== operationId) return;

    const token = isTokenValid(cachedToken)
      ? cachedToken
      : await fetchToken(credentials);
    cachedToken = token;
    if (currentOperation !== operationId) return;

    const currentRoom = new Room({
      adaptiveStream: true,
      dynacast: true,
    });
    room = currentRoom;
    registerRoomEvents(currentRoom);

    await currentRoom.connect(livekitConfig.url, token);
    if (currentOperation !== operationId) {
      await currentRoom.disconnect(true);
      return;
    }

    await currentRoom.localParticipant.setMicrophoneEnabled(true);
    setPeerConnected(currentRoom.remoteParticipants.size > 0);
  } catch (error) {
    cachedToken = null;
    if (room) {
      await disconnectRoom(room);
      room = null;
    }
    setState('disconnected');
    setPeerConnected(false);
    throw error;
  }
}

export function startVoiceForAll() {
  if (state !== 'disconnected' || !credentials || voiceSyncAction) {
    return voiceSyncAction ?? Promise.resolve();
  }

  voiceSyncAction = (async () => {
    pendingLocalVoiceActive = true;
    try {
      await setVoiceActive(credentials.roomCode, true);
      await connectVoice();
    } finally {
      if (pendingLocalVoiceActive === true) {
        pendingLocalVoiceActive = null;
      }
      voiceSyncAction = null;
    }
  })();

  return voiceSyncAction;
}

export function stopVoiceForAll() {
  if (state === 'disconnected' || !credentials || voiceSyncAction) {
    return voiceSyncAction ?? Promise.resolve();
  }

  voiceSyncAction = (async () => {
    pendingLocalVoiceActive = false;
    try {
      await setVoiceActive(credentials.roomCode, false);
      await disconnectVoice();
    } finally {
      if (pendingLocalVoiceActive === false) {
        pendingLocalVoiceActive = null;
      }
      voiceSyncAction = null;
    }
  })();

  return voiceSyncAction;
}

export function startObservingVoiceActive(roomCode) {
  stopObservingVoiceActive();
  if (!roomCode) return;

  unsubVoiceActive = observeVoiceActive(roomCode, (active) => {
    if (pendingLocalVoiceActive === active) {
      pendingLocalVoiceActive = null;
      return;
    }

    if (active && state === 'disconnected') {
      connectVoice().catch((error) => {
        console.error('Failed to auto-connect synchronized voice chat:', error);
      });
    } else if (!active && state !== 'disconnected') {
      disconnectVoice().catch((error) => {
        console.error('Failed to auto-disconnect synchronized voice chat:', error);
      });
    }
  });
}

export function stopObservingVoiceActive() {
  if (unsubVoiceActive) {
    unsubVoiceActive();
    unsubVoiceActive = null;
  }
}

export async function disconnectVoice() {
  operationId += 1;
  const currentRoom = room;
  room = null;
  setState('disconnected');
  setPeerConnected(false);
  if (currentRoom) {
    await disconnectRoom(currentRoom);
  }
}

export function getState() {
  return state;
}

export function onStateChange(callback) {
  stateListeners.add(callback);
  callback(state);
  return () => stateListeners.delete(callback);
}

export function onPeerChange(callback) {
  peerListeners.add(callback);
  callback(peerConnected);
  return () => peerListeners.delete(callback);
}

export async function cleanup() {
  stopObservingVoiceActive();
  pendingLocalVoiceActive = null;
  voiceSyncAction = null;
  await disconnectVoice();
  cachedToken = null;
  credentials = null;
  prefetchPromise = null;
}

function registerRoomEvents(currentRoom) {
  currentRoom
    .on(RoomEvent.Connected, () => {
      if (room === currentRoom) setState('connected');
    })
    .on(RoomEvent.Disconnected, () => {
      if (room === currentRoom) {
        room = null;
        setState('disconnected');
        setPeerConnected(false);
      }
      removeAttachedAudio();
    })
    .on(RoomEvent.ParticipantConnected, () => {
      if (room === currentRoom) setPeerConnected(true);
    })
    .on(RoomEvent.ParticipantDisconnected, () => {
      if (room === currentRoom) {
        setPeerConnected(currentRoom.remoteParticipants.size > 0);
      }
    })
    .on(RoomEvent.TrackSubscribed, (track) => {
      if (track.kind === Track.Kind.Audio) {
        const element = track.attach();
        element.autoplay = true;
        element.dataset.movsyncVoiceAudio = 'true';
        audioElements.add(element);
        document.body.appendChild(element);
      }
    })
    .on(RoomEvent.TrackUnsubscribed, (track) => {
      track.detach().forEach((element) => {
        audioElements.delete(element);
        element.remove();
      });
    });
}

async function fetchToken(tokenCredentials) {
  const { data, error } = await getSupabase().functions.invoke('livekit-token', {
    body: {
      room: tokenCredentials.roomCode,
      participantName: tokenCredentials.displayName,
      participantIdentity: tokenCredentials.userId,
    },
  });

  if (error) throw error;
  if (!data?.token) throw new Error('LiveKit token response was empty.');
  return data.token;
}

function isTokenValid(token) {
  if (!token) return false;
  try {
    const encodedPayload = token.split('.')[1];
    if (!encodedPayload) return false;
    const normalized = encodedPayload
      .replace(/-/g, '+')
      .replace(/_/g, '/')
      .padEnd(Math.ceil(encodedPayload.length / 4) * 4, '=');
    const payload = JSON.parse(atob(normalized));
    return payload.exp * 1000 > Date.now() + TOKEN_EXPIRY_BUFFER_MS;
  } catch (_) {
    return false;
  }
}

function sameCredentials(left, right) {
  return left?.roomCode === right?.roomCode &&
    left?.displayName === right?.displayName &&
    left?.userId === right?.userId;
}

async function disconnectRoom(currentRoom) {
  try {
    currentRoom.removeAllListeners();
    await currentRoom.disconnect(true);
  } finally {
    removeAttachedAudio();
  }
}

function removeAttachedAudio() {
  audioElements.forEach((element) => element.remove());
  audioElements.clear();
}

function setState(nextState) {
  if (state === nextState) return;
  state = nextState;
  stateListeners.forEach((listener) => listener(state));
}

function setPeerConnected(nextPeerConnected) {
  if (peerConnected === nextPeerConnected) return;
  peerConnected = nextPeerConnected;
  peerListeners.forEach((listener) => listener(peerConnected));
}
