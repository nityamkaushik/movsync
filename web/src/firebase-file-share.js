import {
  ref,
  set,
  get,
  remove,
  push,
  onValue,
  off,
  serverTimestamp,
} from 'firebase/database';
import { getFirebaseDb, ensureFirebaseAuth } from './firebase-client.js';

function roomPath(roomCode, path = '') {
  const code = roomCode.toUpperCase();
  return path ? `movsync/rooms/${code}/${path}` : `movsync/rooms/${code}`;
}

function dbRef(roomCode, path) {
  return ref(getFirebaseDb(), roomPath(roomCode, path));
}

export async function publishFileShare(roomCode, { seederId, fileName, fileSize, goFileCode }) {
  await ensureFirebaseAuth();
  await set(dbRef(roomCode, 'fileShare'), {
    seederId,
    fileName,
    fileSize,
    goFileCode: goFileCode || '',
    updatedAt: serverTimestamp(),
  });
}

export function observeFileShare(roomCode, callback) {
  const r = dbRef(roomCode, 'fileShare');
  onValue(r, (snapshot) => {
    if (!snapshot.exists()) {
      callback(null);
      return;
    }
    const value = snapshot.val();
    callback({
      seederId: value.seederId || '',
      fileName: value.fileName || '',
      fileSize: Number(value.fileSize || 0),
      goFileCode: value.goFileCode || '',
    });
  });
  return () => off(r);
}

export async function clearFileShare(roomCode) {
  await ensureFirebaseAuth();
  await remove(dbRef(roomCode, 'fileShare'));
}


export async function getFileShare(roomCode) {
  const snapshot = await get(dbRef(roomCode, 'fileShare'));
  if (!snapshot.exists()) return null;
  const value = snapshot.val();
  return {
    seederId: value.seederId || '',
    fileName: value.fileName || '',
    fileSize: Number(value.fileSize || 0),
    goFileCode: value.goFileCode || '',
  };
}
