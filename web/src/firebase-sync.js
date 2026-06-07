import {
  ref, set, get, remove, onValue, off,
  serverTimestamp, query, orderByChild,
  onDisconnect
} from 'firebase/database';
import { getFirebaseDb, ensureFirebaseAuth } from './firebase-client.js';

function roomRef(roomCode) {
  return ref(getFirebaseDb(), `movsync/rooms/${roomCode.toUpperCase()}`);
}

function childRef(roomCode, path) {
  return ref(getFirebaseDb(), `movsync/rooms/${roomCode.toUpperCase()}/${path}`);
}

export async function createRoomNode(roomCode) {
  await set(childRef(roomCode, 'createdAt'), serverTimestamp());
}

export async function setRoomStarted(roomCode) {
  await set(childRef(roomCode, 'started'), true);
}

export function observeRoomStarted(roomCode, callback) {
  const r = childRef(roomCode, 'started');
  const unsubscribe = onValue(r, (snapshot) => {
    callback(snapshot.val() === true);
  });
  return () => off(r);
}

export async function setAllowControls(roomCode, allow) {
  await set(childRef(roomCode, 'allowControls'), allow);
}

export function observeAllowControls(roomCode, callback) {
  const r = childRef(roomCode, 'allowControls');
  onValue(r, (snapshot) => {
    if (!snapshot.exists()) {
      callback(true);
    } else {
      callback(snapshot.val() === true);
    }
  });
  return () => off(r);
}

export async function writeSyncCommand(roomCode, state) {
  const payload = {
    command: state.command,
    position: state.position,
    speed: state.speed,
    timestamp: serverTimestamp(),
    senderId: state.senderId,
  };
  await set(childRef(roomCode, 'sync'), payload);
}

export async function writeHeartbeat(roomCode, state) {
  const payload = {
    position: state.position,
    isPlaying: state.isPlaying,
    timestamp: serverTimestamp(),
  };
  await set(childRef(roomCode, 'heartbeat'), payload);
}

export async function getHeartbeatOnce(roomCode) {
  const snapshot = await get(childRef(roomCode, 'heartbeat'));
  if (!snapshot.exists()) return null;
  const val = snapshot.val();
  return {
    position: val.position || 0,
    isPlaying: val.isPlaying || false,
    timestamp: val.timestamp || 0,
  };
}

export function listenToSync(roomCode, callback) {
  const r = childRef(roomCode, 'sync');
  onValue(r, (snapshot) => {
    if (!snapshot.exists()) return;
    const val = snapshot.val();
    callback({
      command: val.command || '',
      position: val.position || 0,
      speed: val.speed != null ? val.speed : 1,
      timestamp: val.timestamp || 0,
      senderId: val.senderId || '',
    });
  });
  return () => off(r);
}

export function listenToHeartbeat(roomCode, callback) {
  const r = childRef(roomCode, 'heartbeat');
  onValue(r, (snapshot) => {
    if (!snapshot.exists()) return;
    const val = snapshot.val();
    callback({
      position: val.position || 0,
      isPlaying: val.isPlaying || false,
      timestamp: val.timestamp || 0,
    });
  });
  return () => off(r);
}

export async function trackPresence(roomCode, userId, displayName, isHost, verified) {
  await ensureFirebaseAuth();

  const presRef = childRef(roomCode, `presence/${userId}`);
  const payload = {
    displayName,
    isHost,
    online: true,
    verified,
    updatedAt: serverTimestamp(),
  };
  await set(presRef, payload);

  // Set online to false on disconnect
  const onlineRef = childRef(roomCode, `presence/${userId}/online`);
  const disc = onDisconnect(onlineRef);
  await disc.set(false);

  // If host, remove entire room node on disconnect
  if (isHost) {
    const roomDisc = onDisconnect(roomRef(roomCode));
    await roomDisc.remove();
  }
}

export async function clearPresence(roomCode, userId) {
  await remove(childRef(roomCode, `presence/${userId}`));
}

export function observePresence(roomCode, callback) {
  const r = childRef(roomCode, 'presence');
  onValue(r, (snapshot) => {
    if (!snapshot.exists()) {
      callback([]);
      return;
    }
    const users = [];
    snapshot.forEach((child) => {
      const val = child.val();
      users.push({
        userId: child.key,
        displayName: val.displayName || '',
        isHost: val.isHost || false,
        online: val.online || false,
        verified: val.verified || false,
      });
    });
    callback(users);
  });
  return () => off(r);
}

export async function sendMessage(roomCode, message) {
  const payload = {
    senderId: message.senderId,
    senderName: message.senderName,
    message: message.message,
    timestamp: serverTimestamp(),
  };
  await set(childRef(roomCode, `chat/${message.messageId}`), payload);
}

export function observeChatMessages(roomCode, callback) {
  const r = query(childRef(roomCode, 'chat'), orderByChild('timestamp'));
  onValue(r, (snapshot) => {
    if (!snapshot.exists()) {
      callback([]);
      return;
    }
    const messages = [];
    snapshot.forEach((child) => {
      const val = child.val();
      messages.push({
        messageId: child.key,
        senderId: val.senderId || '',
        senderName: val.senderName || '',
        message: val.message || '',
        timestamp: val.timestamp || 0,
      });
    });
    callback(messages);
  });
  return () => off(r);
}

export function removeAllListeners(roomCode) {
  off(childRef(roomCode, 'sync'));
  off(childRef(roomCode, 'heartbeat'));
  off(childRef(roomCode, 'presence'));
  off(childRef(roomCode, 'chat'));
  off(childRef(roomCode, 'started'));
  off(childRef(roomCode, 'allowControls'));
}
