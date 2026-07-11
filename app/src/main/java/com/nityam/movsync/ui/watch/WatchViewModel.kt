package com.nityam.movsync.ui.watch

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.nityam.movsync.MovSyncApp
import com.nityam.movsync.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MovSyncApp).container
    private val _syncStatus = MutableStateFlow(SyncStatus.Synced)
    val syncStatus = _syncStatus.asStateFlow()
    
    private val _allowControls = MutableStateFlow(true)
    val allowControls = _allowControls.asStateFlow()

    private var activeRoomCode: String? = null
    private var activePlayer: Player? = null
    private var isHostRef = false

    fun start(roomCode: String, isHost: Boolean, player: Player) {
        if (activeRoomCode == roomCode && activePlayer == player) return
        activeRoomCode = roomCode
        activePlayer = player
        isHostRef = isHost
        viewModelScope.launch {
            try {
                val userId = container.authRepository.ensureSignedIn()
                
                // Observe allow controls for UI
                launch {
                    _allowControls.emitAll(container.firebaseSync.observeAllowControls(roomCode))
                }
                
                container.syncEngine.start(
                    roomCode = roomCode,
                    userId = userId,
                    isHost = isHost,
                    player = player,
                    scope = viewModelScope,
                    allowControlsFlow = _allowControls,
                    onStatus = { _syncStatus.value = it }
                )
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
            }
        }
    }

    fun stop() {
        activeRoomCode?.let { container.syncEngine.stop(it, activePlayer) }
        activeRoomCode = null
        activePlayer = null
    }

    fun toggleControls() {
        if (isHostRef) {
            val room = activeRoomCode ?: return
            viewModelScope.launch {
                container.firebaseSync.setAllowControls(room, !_allowControls.value)
            }
        }
    }

    fun userPlay() {
        val player = activePlayer ?: return
        val room = activeRoomCode ?: return
        player.play()
        viewModelScope.launch {
            try {
                val userId = container.authRepository.ensureSignedIn()
                container.syncEngine.broadcastCommand(room, userId, "play", player.currentPosition, player.playbackParameters.speed)
            } catch (e: Exception) {
                Log.e(TAG, "userPlay broadcast failed", e)
            }
        }
    }

    fun userPause() {
        val player = activePlayer ?: return
        val room = activeRoomCode ?: return
        player.pause()
        viewModelScope.launch {
            try {
                val userId = container.authRepository.ensureSignedIn()
                container.syncEngine.broadcastCommand(room, userId, "pause", player.currentPosition, player.playbackParameters.speed)
            } catch (e: Exception) {
                Log.e(TAG, "userPause broadcast failed", e)
            }
        }
    }

    fun userSeek(positionMs: Long) {
        val player = activePlayer ?: return
        val room = activeRoomCode ?: return
        player.seekTo(positionMs)
        viewModelScope.launch {
            try {
                val userId = container.authRepository.ensureSignedIn()
                container.syncEngine.broadcastCommand(room, userId, "seek", positionMs, player.playbackParameters.speed)
            } catch (e: Exception) {
                Log.e(TAG, "userSeek broadcast failed", e)
            }
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private companion object {
        const val TAG = "WatchViewModel"
    }
}
