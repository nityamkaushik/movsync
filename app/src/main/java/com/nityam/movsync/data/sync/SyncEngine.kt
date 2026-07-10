package com.nityam.movsync.data.sync

import androidx.media3.common.Player
import com.google.firebase.database.ValueEventListener
import com.nityam.movsync.data.firebase.FirebaseSync
import com.nityam.movsync.data.model.HeartbeatState
import com.nityam.movsync.data.model.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SyncEngine(
    private val firebaseSync: FirebaseSync,
    private val driftCorrector: DriftCorrector
) {
    private var heartbeatJob: Job? = null
    private var rttJob: Job? = null
    private var syncListener: ValueEventListener? = null
    private var heartbeatListener: ValueEventListener? = null
    private var playerListener: Player.Listener? = null
    private var applyingRemoteCommand = false

    fun start(
        roomCode: String,
        userId: String,
        isHost: Boolean,
        player: Player,
        scope: CoroutineScope,
        allowControlsFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
        onStatus: (SyncStatus) -> Unit
    ) {
        stop(roomCode, player)
        
        // 1. Listen to Sync Queue (Both Host & Participant)
        syncListener = firebaseSync.listenToSync(roomCode) { state ->
            if (state.senderId == userId) return@listenToSync
            applyingRemoteCommand = true
            driftCorrector.resetOnCommand()
            when (state.command) {
                "play" -> {
                    player.seekTo(state.position)
                    player.play()
                }
                "pause" -> {
                    player.seekTo(state.position)
                    player.pause()
                }
                "seek" -> player.seekTo(state.position)
            }
            player.setPlaybackSpeed(state.speed)
            applyingRemoteCommand = false
        }

        // 2. Explicit Broadcast function added instead of Player.Listener

        // 3. Heartbeat & Drift Correction
        if (isHost) {
            heartbeatJob = scope.launch {
                while (true) {
                    firebaseSync.writeHeartbeat(
                        roomCode,
                        HeartbeatState(
                            position = player.currentPosition,
                            isPlaying = player.isPlaying
                        )
                    )
                    delay(500L)
                }
            }
        } else {
            // Start periodic RTT measurement
            rttJob = scope.launch {
                while (true) {
                    firebaseSync.measureRtt(roomCode)
                    delay(30_000L)
                }
            }
            // Initial RTT measurement
            scope.launch { firebaseSync.measureRtt(roomCode) }

            heartbeatListener = firebaseSync.listenToHeartbeat(roomCode) { heartbeat ->
                if (applyingRemoteCommand) return@listenToHeartbeat

                val oneWayLatency = firebaseSync.getOneWayLatency()
                val adjustedPosition = heartbeat.position
                    + (firebaseSync.getEstimatedServerTime() - heartbeat.timestamp)
                    - oneWayLatency

                if (heartbeat.isPlaying && !player.isPlaying) {
                    player.play()
                } else if (!heartbeat.isPlaying && player.isPlaying) {
                    player.pause()
                }
                driftCorrector.apply(player, adjustedPosition.coerceAtLeast(0L), !player.isPlaying, onStatus)
            }
            scope.launch {
                firebaseSync.getHeartbeatOnce(roomCode)?.let { heartbeat ->
                    val oneWayLatency = firebaseSync.getOneWayLatency()
                    val adjustedPosition = heartbeat.position
                        + (firebaseSync.getEstimatedServerTime() - heartbeat.timestamp)
                        - oneWayLatency
                    player.seekTo(adjustedPosition.coerceAtLeast(0L))
                    if (heartbeat.isPlaying) player.play() else player.pause()
                }
            }
        }
    }

    fun stop(roomCode: String, player: Player? = null) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        rttJob?.cancel()
        rttJob = null
        syncListener?.let { firebaseSync.removeSyncListener(roomCode, it) }
        heartbeatListener?.let { firebaseSync.removeHeartbeatListener(roomCode, it) }
        syncListener = null
        heartbeatListener = null
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
        applyingRemoteCommand = false
    }

    suspend fun broadcastCommand(
        roomCode: String,
        userId: String,
        command: String,
        position: Long,
        speed: Float
    ) {
        firebaseSync.writeSyncCommand(
            roomCode,
            SyncState(
                command = command,
                position = position,
                speed = speed,
                senderId = userId
            )
        )
    }
}
