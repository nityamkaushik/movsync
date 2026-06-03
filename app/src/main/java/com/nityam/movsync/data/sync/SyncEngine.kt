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

        // 2. Broadcast Player Changes to Sync Queue
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (applyingRemoteCommand) return
                if (!isHost && !allowControlsFlow.value) return
                
                scope.launch {
                    firebaseSync.writeSyncCommand(
                        roomCode,
                        SyncState(
                            command = if (isPlaying) "play" else "pause",
                            position = player.currentPosition,
                            speed = player.playbackParameters.speed,
                            senderId = userId
                        )
                    )
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (applyingRemoteCommand || reason != Player.DISCONTINUITY_REASON_SEEK) return
                if (!isHost && !allowControlsFlow.value) return
                
                scope.launch {
                    firebaseSync.writeSyncCommand(
                        roomCode,
                        SyncState(
                            command = "seek",
                            position = newPosition.positionMs,
                            speed = player.playbackParameters.speed,
                            senderId = userId
                        )
                    )
                }
            }
        }
        player.addListener(listener)
        playerListener = listener

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
                    delay(2_000L)
                }
            }
        } else {
            heartbeatListener = firebaseSync.listenToHeartbeat(roomCode) { heartbeat ->
                val adjustedPosition = heartbeat.position + (System.currentTimeMillis() - heartbeat.timestamp)
                if (heartbeat.isPlaying && !player.isPlaying) {
                    player.play()
                } else if (!heartbeat.isPlaying && player.isPlaying) {
                    player.pause()
                }
                driftCorrector.apply(player, adjustedPosition, scope, onStatus)
            }
            scope.launch {
                firebaseSync.getHeartbeatOnce(roomCode)?.let { heartbeat ->
                    val adjustedPosition = heartbeat.position + (System.currentTimeMillis() - heartbeat.timestamp)
                    player.seekTo(adjustedPosition.coerceAtLeast(0L))
                    if (heartbeat.isPlaying) player.play() else player.pause()
                }
            }
        }
    }

    fun stop(roomCode: String, player: Player? = null) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        syncListener?.let { firebaseSync.removeSyncListener(roomCode, it) }
        heartbeatListener?.let { firebaseSync.removeHeartbeatListener(roomCode, it) }
        syncListener = null
        heartbeatListener = null
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
        applyingRemoteCommand = false
    }
}
