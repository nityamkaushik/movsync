import { getSupabase } from './supabase-client.js';
import { ensureFirebaseAuth } from './firebase-client.js';

const DISPLAY_NAME_KEY = 'movsync_display_name';

const ADJECTIVES = [
  'cosmic', 'neon', 'zephyr', 'salty', 'fuzzy', 'moody', 'crispy', 'wobbly',
  'sparkly', 'mystic', 'lunar', 'solar', 'turbo', 'ultra', 'mega', 'hyper',
  'swift', 'chill', 'vivid', 'glitchy', 'frosty', 'toasty', 'breezy', 'dizzy'
];

const NOUNS = [
  'banana', 'panda', 'fox', 'iguana', 'apple', 'penguin', 'otter', 'moose',
  'cactus', 'tornado', 'waffle', 'burrito', 'noodle', 'pretzel', 'muffin',
  'rocket', 'pixel', 'goblin', 'wizard', 'ninja', 'pirate', 'robot', 'alien'
];

export function generateRandomName() {
  const adj = ADJECTIVES[Math.floor(Math.random() * ADJECTIVES.length)];
  const noun = NOUNS[Math.floor(Math.random() * NOUNS.length)];
  return `${adj}${noun}`;
}

export function getOrCreateDisplayName() {
  let name = getDisplayName();
  if (!name) {
    name = generateRandomName();
    saveDisplayName(name);
  }
  return name;
}

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
