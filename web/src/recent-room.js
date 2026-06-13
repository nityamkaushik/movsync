const RECENT_ROOM_KEY = 'movsync_recent_room';

export function saveRecentRoom({ code, movieName = null, isHost = false, timestamp = Date.now() }) {
  if (!code) return;

  const recentRoom = {
    code: String(code).toUpperCase(),
    movieName: movieName || null,
    isHost: Boolean(isHost),
    timestamp: Number(timestamp) || Date.now(),
  };

  localStorage.setItem(RECENT_ROOM_KEY, JSON.stringify(recentRoom));
}

export function getRecentRoom() {
  try {
    const raw = localStorage.getItem(RECENT_ROOM_KEY);
    if (!raw) return null;

    const parsed = JSON.parse(raw);
    if (!parsed?.code || typeof parsed.code !== 'string') return null;

    return {
      code: parsed.code.toUpperCase(),
      movieName: parsed.movieName || null,
      isHost: Boolean(parsed.isHost),
      timestamp: Number(parsed.timestamp) || Date.now(),
    };
  } catch {
    clearRecentRoom();
    return null;
  }
}

export function clearRecentRoom() {
  localStorage.removeItem(RECENT_ROOM_KEY);
}
