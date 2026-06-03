package com.nityam.movsync.data.model

data class SyncState(
    val command: String = "",
    val position: Long = 0L,
    val speed: Float = 1f,
    val timestamp: Long = 0L,
    val senderId: String = ""
)

data class HeartbeatState(
    val position: Long = 0L,
    val isPlaying: Boolean = false,
    val timestamp: Long = 0L
)

data class PresenceUser(
    val userId: String = "",
    val displayName: String = "",
    val isHost: Boolean = false,
    val online: Boolean = false,
    val verified: Boolean = false
)
