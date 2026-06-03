/**
 * RoomRepository — Port of RoomRepository.kt
 * Room CRUD operations using Supabase + Firebase.
 */

import { getSupabase } from './supabase-client.js';
import * as firebaseSync from './firebase-sync.js';
import { generateRoomCode } from './room-code-generator.js';

export async function createRoom(hostId, displayName, fingerprint, movieName, durationMs) {
  const supabase = getSupabase();

  // Try up to 10 times to generate a unique room code
  for (let i = 0; i < 10; i++) {
    const code = generateRoomCode();
    const existing = await getRoomByCode(code);
    if (!existing) {
      const { data: room, error } = await supabase
        .from('rooms')
        .insert({
          code,
          host_id: hostId,
          movie_fingerprint: fingerprint,
          movie_name: movieName || null,
          movie_duration_ms: durationMs || null,
          status: 'waiting',
        })
        .select()
        .single();

      if (error) throw error;

      // Add host as participant
      await addParticipant(room.id, hostId, displayName, true, true);

      // Create Firebase room node + track presence
      await firebaseSync.createRoomNode(room.code);
      await firebaseSync.trackPresence(room.code, hostId, displayName, true, true);

      return room;
    }
  }
  throw new Error('Could not generate a unique room code');
}

export async function joinRoom(userId, displayName, code, fingerprint) {
  const room = await getRoomByCode(code);
  if (!room) return { result: 'not_found' };

  if (room.movie_fingerprint !== fingerprint) {
    return { result: 'fingerprint_mismatch', room };
  }

  await addParticipant(room.id, userId, displayName, false, true);
  await firebaseSync.trackPresence(room.code, userId, displayName, false, true);

  return { result: 'joined', room };
}

export async function getRoomByCode(code) {
  const supabase = getSupabase();
  try {
    const { data, error } = await supabase
      .from('rooms')
      .select('*')
      .eq('code', code.toUpperCase())
      .limit(1);

    if (error) return null;
    return data && data.length > 0 ? data[0] : null;
  } catch {
    return null;
  }
}

export async function leaveRoom(roomCode, roomId, userId) {
  const supabase = getSupabase();
  try {
    await supabase
      .from('participants')
      .delete()
      .eq('room_id', roomId)
      .eq('user_id', userId);
  } catch (e) {
    // Ignore errors
  }
  await firebaseSync.clearPresence(roomCode, userId);
}

async function addParticipant(roomId, userId, displayName, isHost, verified) {
  const supabase = getSupabase();
  await supabase.from('participants').upsert({
    room_id: roomId,
    user_id: userId,
    display_name: displayName,
    is_host: isHost,
    fingerprint_verified: verified,
  });
}
