package com.nityam.movsync.data.repository

import com.nityam.movsync.data.firebase.FirebaseSync
import com.nityam.movsync.data.model.ParticipantInsert
import com.nityam.movsync.data.model.Room
import com.nityam.movsync.data.model.RoomInsert
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class RoomRepository(
    private val supabase: SupabaseClient,
    private val firebaseSync: FirebaseSync
) {
    suspend fun createRoom(
        hostId: String,
        displayName: String,
        fingerprint: String,
        movieName: String?,
        durationMs: Long?
    ): Room {
        repeat(10) {
            val code = RoomCodeGenerator.generate()
            if (getRoomByCode(code) == null) {
                val room = supabase.from("rooms")
                    .insert(
                        RoomInsert(
                            code = code,
                            hostId = hostId,
                            movieFingerprint = fingerprint,
                            movieName = movieName,
                            movieDurationMs = durationMs
                        )
                    ) {
                        select()
                    }
                    .decodeSingle<Room>()
                addParticipant(room.id, hostId, displayName, isHost = true, verified = true)
                firebaseSync.createRoomNode(room.code)
                firebaseSync.trackPresence(room.code, hostId, displayName, isHost = true, verified = true)
                return room
            }
        }
        error("Could not generate a unique room code")
    }

    suspend fun joinRoom(
        userId: String,
        displayName: String,
        code: String,
        fingerprint: String
    ): JoinResult {
        val room = getRoomByCode(code) ?: return JoinResult.NotFound
        if (room.movieFingerprint != fingerprint) {
            return JoinResult.FingerprintMismatch(room)
        }
        addParticipant(room.id, userId, displayName, isHost = false, verified = true)
        firebaseSync.trackPresence(room.code, userId, displayName, isHost = false, verified = true)
        return JoinResult.Joined(room)
    }

    suspend fun getRoomByCode(code: String): Room? {
        return runCatching {
            supabase.from("rooms")
                .select {
                    filter {
                        eq("code", code.uppercase())
                    }
                    limit(1)
                }
                .decodeList<Room>()
                .firstOrNull()
        }.getOrNull()
    }

    suspend fun leaveRoom(roomCode: String, roomId: String, userId: String) {
        runCatching {
            supabase.from("participants").delete {
                filter {
                    eq("room_id", roomId)
                    eq("user_id", userId)
                }
            }
        }
        firebaseSync.clearPresence(roomCode, userId)
    }

    private suspend fun addParticipant(
        roomId: String,
        userId: String,
        displayName: String,
        isHost: Boolean,
        verified: Boolean
    ) {
        supabase.from("participants").upsert(
            ParticipantInsert(
                roomId = roomId,
                userId = userId,
                displayName = displayName,
                isHost = isHost,
                fingerprintVerified = verified
            )
        )
    }
}

sealed interface JoinResult {
    data class Joined(val room: Room) : JoinResult
    data class FingerprintMismatch(val room: Room) : JoinResult
    data object NotFound : JoinResult
}
