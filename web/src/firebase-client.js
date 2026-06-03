import { initializeApp } from 'firebase/app';
import { getAuth, signInAnonymously } from 'firebase/auth';
import { getDatabase } from 'firebase/database';
import { firebaseConfig } from './config.js';

let app = null;
let _auth = null;
let _db = null;

function getApp() {
  if (!app) {
    app = initializeApp(firebaseConfig);
  }
  return app;
}

export function getFirebaseAuth() {
  if (!_auth) {
    _auth = getAuth(getApp());
  }
  return _auth;
}

export function getFirebaseDb() {
  if (!_db) {
    _db = getDatabase(getApp());
  }
  return _db;
}

export async function ensureFirebaseAuth() {
  const firebaseAuth = getFirebaseAuth();
  if (!firebaseAuth.currentUser) {
    await signInAnonymously(firebaseAuth);
  }
  return firebaseAuth.currentUser;
}
