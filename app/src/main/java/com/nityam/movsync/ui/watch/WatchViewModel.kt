package com.nityam.movsync.ui.watch

import android.app.Application
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
    
    private val _allowControls = MutableStateFlow(false)
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
        }
    }

    fun toggleControls() {
        if (isHostRef) {
            val room = activeRoomCode ?: return
            viewModelScope.launch {
                container.firebaseSync.setAllowControls(room, !_allowControls.value)
            }
        }
    }

    fun stop() {
        activeRoomCode?.let { container.syncEngine.stop(it, activePlayer) }
        activeRoomCode = null
        activePlayer = null
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
