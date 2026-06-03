import { getSupabase } from './supabase-client.js';
import { ensureFirebaseAuth } from './firebase-client.js';

const DISPLAY_NAME_KEY = 'movsync_display_name';

/**
 * Sign in anonymously to both Supabase and Firebase.
 * Returns the Supabase user ID as the canonical userId.
 */
export async function ensureSignedIn() {
  const supabase = getSupabase();

  // Supabase anonymous auth
  let session = (await supabase.auth.getSession()).data.session;
  if (!session) {
    const { data, error } = await supabase.auth.signInAnonymously();
    if (error) throw error;
    session = data.session;
  }

  // Firebase anonymous auth
  await ensureFirebaseAuth();

  return session.user.id;
}

export function getDisplayName() {
  return localStorage.getItem(DISPLAY_NAME_KEY) || '';
}

export function saveDisplayName(name) {
  localStorage.setItem(DISPLAY_NAME_KEY, name);
}
