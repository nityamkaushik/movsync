package com.nityam.movsync.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.nityam.movsync.data.model.HeartbeatState
import com.nityam.movsync.data.model.PresenceUser
import com.nityam.movsync.data.model.SyncState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseSync(
    private val database: FirebaseDatabase,
    private val firebaseAuth: FirebaseAuth
) {
    private var serverTimeOffset: Long = 0L

    init {
        database.getReference(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverTimeOffset = snapshot.getValue(Long::class.java) ?: 0L
            }
            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    fun getEstimatedServerTime(): Long {
        return System.currentTimeMillis() + serverTimeOffset
    }
    private fun roomRef(roomCode: String) = database.reference
        .child("movsync")
        .child("rooms")
        .child(roomCode.uppercase())

    suspend fun createRoomNode(roomCode: String) {
        roomRef(roomCode).child("createdAt").setValue(ServerValue.TIMESTAMP).await()
    }

    suspend fun setRoomStarted(roomCode: String) {
        roomRef(roomCode).child("started").setValue(true).await()
    }

    fun observeRoomStarted(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) ?: false)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = roomRef(roomCode).child("started")
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun setAllowControls(roomCode: String, allow: Boolean) {
        roomRef(roomCode).child("allowControls").setValue(allow).await()
    }

    fun observeAllowControls(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) ?: true)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = roomRef(roomCode).child("allowControls")
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun setVoiceActive(roomCode: String, active: Boolean) {
        roomRef(roomCode).child("voiceActive").setValue(active).await()
    }

    fun observeVoiceActive(roomCode: String): Flow<Boolean> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) ?: false)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = roomRef(roomCode).child("voiceActive")
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun writeSyncCommand(roomCode: String, state: SyncState) {
        val payload = mapOf(
            "command" to state.command,
            "position" to state.position,
            "speed" to state.speed,
            "timestamp" to ServerValue.TIMESTAMP,
            "senderId" to state.senderId
        )
        roomRef(roomCode).child("sync").setValue(payload).await()
    }

    suspend fun writeHeartbeat(roomCode: String, state: HeartbeatState) {
        val payload = mapOf(
            "position" to state.position,
            "isPlaying" to state.isPlaying,
            "timestamp" to ServerValue.TIMESTAMP
        )
        roomRef(roomCode).child("heartbeat").setValue(payload).await()
    }

    suspend fun trackPresence(
        roomCode: String,
        userId: String,
        displayName: String,
        isHost: Boolean,
        verified: Boolean
    ) {
        if (firebaseAuth.currentUser == null) {
            firebaseAuth.signInAnonymously().await()
        }

        val presenceRef = roomRef(roomCode).child("presence").child(userId)
        val payload = mapOf(
            "displayName" to displayName,
            "isHost" to isHost,
            "online" to true,
            "verified" to verified,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        presenceRef.setValue(payload).await()
        presenceRef.child("online").onDisconnect().setValue(false)
    }

    suspend fun setPresenceVerified(roomCode: String, userId: String, verified: Boolean = true) {
        if (firebaseAuth.currentUser == null) {
            firebaseAuth.signInAnonymously().await()
        }
        val presenceRef = roomRef(roomCode).child("presence").child(userId)
        presenceRef.child("verified").setValue(verified).await()
        presenceRef.child("updatedAt").setValue(ServerValue.TIMESTAMP).await()
    }

    suspend fun clearPresence(roomCode: String, userId: String) {
        roomRef(roomCode).child("presence").child(userId).removeValue().await()
    }

    suspend fun getHeartbeatOnce(roomCode: String): HeartbeatState? {
        return roomRef(roomCode).child("heartbeat").get().await().toHeartbeatStateOrNull()
    }

    fun listenToSync(roomCode: String, onState: (SyncState) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.toSyncStateOrNull()?.let(onState)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        roomRef(roomCode).child("sync").addValueEventListener(listener)
        return listener
    }

    fun listenToHeartbeat(roomCode: String, onState: (HeartbeatState) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.toHeartbeatStateOrNull()?.let(onState)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        roomRef(roomCode).child("heartbeat").addValueEventListener(listener)
        return listener
    }

    fun presence(roomCode: String): Flow<List<PresenceUser>> = callbackFlow {
        val ref = roomRef(roomCode).child("presence")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.map { it.toPresenceUser() })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun removeSyncListener(roomCode: String, listener: ValueEventListener) {
        roomRef(roomCode).child("sync").removeEventListener(listener)
    }

    fun removeHeartbeatListener(roomCode: String, listener: ValueEventListener) {
        roomRef(roomCode).child("heartbeat").removeEventListener(listener)
    }

    suspend fun sendMessage(roomCode: String, message: com.nityam.movsync.data.model.ChatMessage) {
        val payload = mapOf(
            "senderId" to message.senderId,
            "senderName" to message.senderName,
            "message" to message.message,
            "timestamp" to ServerValue.TIMESTAMP
        )
        roomRef(roomCode).child("chat").child(message.messageId).setValue(payload).await()
    }

    fun observeChatMessages(roomCode: String): Flow<List<com.nityam.movsync.data.model.ChatMessage>> = callbackFlow {
        val ref = roomRef(roomCode).child("chat").orderByChild("timestamp")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { it.toChatMessageOrNull() }
                trySend(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun DataSnapshot.toSyncStateOrNull(): SyncState? {
        if (!exists()) return null
        return SyncState(
            command = child("command").getValue(String::class.java).orEmpty(),
            position = child("position").getValue(Long::class.java) ?: 0L,
            speed = child("speed").getValue(Double::class.java)?.toFloat()
                ?: child("speed").getValue(Float::class.java)
                ?: 1f,
            timestamp = child("timestamp").getValue(Long::class.java) ?: 0L,
            senderId = child("senderId").getValue(String::class.java).orEmpty()
        )
    }

    private fun DataSnapshot.toHeartbeatStateOrNull(): HeartbeatState? {
        if (!exists()) return null
        return HeartbeatState(
            position = child("position").getValue(Long::class.java) ?: 0L,
            isPlaying = child("isPlaying").getValue(Boolean::class.java) ?: false,
            timestamp = child("timestamp").getValue(Long::class.java) ?: 0L
        )
    }

    private fun DataSnapshot.toPresenceUser(): PresenceUser {
        return PresenceUser(
            userId = key.orEmpty(),
            displayName = child("displayName").getValue(String::class.java).orEmpty(),
            isHost = child("isHost").getValue(Boolean::class.java) ?: false,
            online = child("online").getValue(Boolean::class.java) ?: false,
            verified = child("verified").getValue(Boolean::class.java) ?: false
        )
    }

    private fun DataSnapshot.toChatMessageOrNull(): com.nityam.movsync.data.model.ChatMessage? {
        if (!exists()) return null
        return com.nityam.movsync.data.model.ChatMessage(
            messageId = key.orEmpty(),
            senderId = child("senderId").getValue(String::class.java).orEmpty(),
            senderName = child("senderName").getValue(String::class.java).orEmpty(),
            message = child("message").getValue(String::class.java).orEmpty(),
            timestamp = child("timestamp").getValue(Long::class.java) ?: 0L
        )
    }
}
