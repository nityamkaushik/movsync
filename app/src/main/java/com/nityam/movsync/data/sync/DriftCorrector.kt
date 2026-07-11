package com.nityam.movsync.data.sync

import androidx.media3.common.Player
import kotlin.math.abs

class DriftCorrector {
    companion object {
        private const val INSYNC_THRESHOLD_MS = 50L
        private const val SOFT_SEEK_THRESHOLD_MS = 800L
        private const val PROPORTIONAL_GAIN = 1f / 5000f
        private const val MIN_SPEED = 0.85f
        private const val MAX_SPEED = 1.15f
        private const val SMOOTH_FACTOR = 0.5
    }

    private var smoothedDrift = 0.0

    fun evaluate(currentPositionMs: Long, expectedPositionMs: Long): DriftAction {
        val rawDrift = currentPositionMs - expectedPositionMs
        val magnitude = abs(rawDrift)

        if (magnitude < INSYNC_THRESHOLD_MS) {
            smoothedDrift = 0.0
            return DriftAction.InSync(rawDrift)
        }

        smoothedDrift = SMOOTH_FACTOR * rawDrift + (1.0 - SMOOTH_FACTOR) * smoothedDrift
        val drift = smoothedDrift.toLong()
        val mag = abs(drift)

        if (mag < SOFT_SEEK_THRESHOLD_MS) {
            val speedAdjust = (-drift * PROPORTIONAL_GAIN).coerceIn(
                MIN_SPEED - 1f, MAX_SPEED - 1f
            )
            val speed = (1f + speedAdjust).coerceIn(MIN_SPEED, MAX_SPEED)
            return DriftAction.SoftCorrect(drift, speed)
        } else {
            return DriftAction.SoftSeek(drift, expectedPositionMs)
        }
    }

    fun apply(
        player: Player,
        expectedPositionMs: Long,
        isPaused: Boolean,
        onStatus: (SyncStatus) -> Unit
    ) {
        if (isPaused) return

        when (val action = evaluate(player.currentPosition, expectedPositionMs)) {
            is DriftAction.InSync -> {
                player.setPlaybackSpeed(1f)
                onStatus(SyncStatus.Synced)
            }
            is DriftAction.SoftCorrect -> {
                player.setPlaybackSpeed(action.speed)
                onStatus(SyncStatus.Correcting)
            }
            is DriftAction.SoftSeek -> {
                val currentPos = player.currentPosition
                val target = currentPos + ((action.expectedPosition - currentPos) * 1.0f).toLong()
                player.seekTo(target)
                player.setPlaybackSpeed(1f)
                onStatus(SyncStatus.Correcting)
            }
        }
    }

    fun resetOnCommand() {
        smoothedDrift = 0.0
    }
}

sealed interface DriftAction {
    data class InSync(val driftMs: Long) : DriftAction
    data class SoftCorrect(val driftMs: Long, val speed: Float) : DriftAction
    data class SoftSeek(val driftMs: Long, val expectedPosition: Long) : DriftAction
}

enum class SyncStatus {
    Synced,
    Correcting,
    Reconnecting
}
