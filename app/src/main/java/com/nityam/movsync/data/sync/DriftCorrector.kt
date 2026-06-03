package com.nityam.movsync.data.sync

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class DriftCorrector(
    private val acceptableDriftMs: Long = 75L,
    private val hardSeekDriftMs: Long = 2_000L
) {
    private var resetJob: Job? = null
    private var repeatedHardSeeks = 0

    fun evaluate(currentPosition: Long, expectedPosition: Long): DriftAction {
        val drift = currentPosition - expectedPosition
        val magnitude = abs(drift)
        return when {
            magnitude < acceptableDriftMs -> DriftAction.InSync(drift)
            magnitude < hardSeekDriftMs -> {
                val speed = if (drift < 0L) 1.12f else 0.88f
                DriftAction.SoftCorrect(drift, speed, (magnitude / 0.12f).toLong())
            }
            else -> DriftAction.HardSeek(drift, expectedPosition)
        }
    }

    fun apply(
        player: Player,
        expectedPosition: Long,
        scope: CoroutineScope,
        onStatus: (SyncStatus) -> Unit
    ) {
        when (val action = evaluate(player.currentPosition, expectedPosition)) {
            is DriftAction.InSync -> {
                repeatedHardSeeks = 0
                onStatus(SyncStatus.Synced)
            }
            is DriftAction.SoftCorrect -> {
                repeatedHardSeeks = 0
                player.setPlaybackSpeed(action.speed)
                onStatus(SyncStatus.Correcting)
                resetJob?.cancel()
                resetJob = scope.launch {
                    delay(action.durationMs.coerceIn(250L, 6_000L))
                    player.setPlaybackSpeed(1f)
                    onStatus(SyncStatus.Synced)
                }
            }
            is DriftAction.HardSeek -> {
                repeatedHardSeeks += 1
                player.seekTo(action.targetPosition)
                player.setPlaybackSpeed(1f)
                onStatus(SyncStatus.Correcting)
            }
        }
    }
}

sealed interface DriftAction {
    data class InSync(val driftMs: Long) : DriftAction
    data class SoftCorrect(val driftMs: Long, val speed: Float, val durationMs: Long) : DriftAction
    data class HardSeek(val driftMs: Long, val targetPosition: Long) : DriftAction
}

enum class SyncStatus {
    Synced,
    Correcting,
    Reconnecting
}
