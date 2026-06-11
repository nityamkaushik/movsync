export const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || '',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || '',
  databaseURL: import.meta.env.VITE_FIREBASE_DATABASE_URL || '',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || '',
};

export const supabaseConfig = {
  url: import.meta.env.VITE_SUPABASE_URL || '',
  key: import.meta.env.VITE_SUPABASE_KEY || '',
};

export const livekitConfig = {
  url: import.meta.env.VITE_LIVEKIT_URL || '',
};

export function isConfigured() {
  return !!(firebaseConfig.apiKey && firebaseConfig.databaseURL && supabaseConfig.url && supabaseConfig.key);
}
