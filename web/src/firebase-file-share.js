import {
  ref,
  set,
  get,
  remove,
  push,
  onValue,
  onChildAdded,
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

export async function publishFileShare(roomCode, { seederId, fileName, fileSize }) {
  await ensureFirebaseAuth();
  await set(dbRef(roomCode, 'fileShare'), {
    seederId,
    fileName,
    fileSize,
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
    });
  });
  return () => off(r);
}

export async function clearFileShare(roomCode) {
  await ensureFirebaseAuth();
  await remove(dbRef(roomCode, 'fileShare'));
}

export async function writeSignalingOffer(roomCode, peerId, sdp) {
  await ensureFirebaseAuth();
  await set(dbRef(roomCode, `signaling/${peerId}/offer`), {
    type: sdp.type,
    sdp: sdp.sdp,
    updatedAt: serverTimestamp(),
  });
}

export async function writeSignalingAnswer(roomCode, peerId, sdp) {
  await ensureFirebaseAuth();
  await set(dbRef(roomCode, `signaling/${peerId}/answer`), {
    type: sdp.type,
    sdp: sdp.sdp,
    updatedAt: serverTimestamp(),
  });
}

export async function writeIceCandidate(roomCode, peerId, role, candidate) {
  await ensureFirebaseAuth();
  const path = `signaling/${peerId}/${role}Candidates`;
  const r = push(dbRef(roomCode, path));
  await set(r, candidate.toJSON ? candidate.toJSON() : candidate);
}

export function observeSignalingAnswer(roomCode, peerId, callback) {
  const r = dbRef(roomCode, `signaling/${peerId}/answer`);
  onValue(r, (snapshot) => {
    if (!snapshot.exists()) return;
    const value = snapshot.val();
    callback({ type: value.type || 'answer', sdp: value.sdp || '' });
  });
  return () => off(r);
}

export function observeSignalingOffers(roomCode, callback) {
  const r = dbRef(roomCode, 'signaling');
  const seen = new Set();
  onValue(r, (snapshot) => {
    snapshot.forEach((child) => {
      const peerId = child.key;
      const value = child.val();
      if (peerId && !seen.has(peerId) && value?.offer?.sdp) {
        seen.add(peerId);
        callback(peerId, {
          type: value.offer.type || 'offer',
          sdp: value.offer.sdp,
        });
      }
    });
  });
  return () => off(r);
}

export function observeSignalingOffersChildAdded(roomCode, callback) {
  const r = dbRef(roomCode, 'signaling');
  onChildAdded(r, (snapshot) => {
    const value = snapshot.val();
    if (snapshot.key && value?.offer?.sdp) {
      callback(snapshot.key, {
        type: value.offer.type || 'offer',
        sdp: value.offer.sdp,
      });
    }
  });
  return () => off(r);
}

export function observeIceCandidates(roomCode, peerId, role, callback) {
  const r = dbRef(roomCode, `signaling/${peerId}/${role}Candidates`);
  onChildAdded(r, (snapshot) => {
    if (snapshot.exists()) callback(snapshot.val());
  });
  return () => off(r);
}

export async function clearSignaling(roomCode, peerId) {
  await ensureFirebaseAuth();
  await remove(dbRef(roomCode, `signaling/${peerId}`));
}

export async function getFileShare(roomCode) {
  const snapshot = await get(dbRef(roomCode, 'fileShare'));
  if (!snapshot.exists()) return null;
  const value = snapshot.val();
  return {
    seederId: value.seederId || '',
    fileName: value.fileName || '',
    fileSize: Number(value.fileSize || 0),
  };
}
