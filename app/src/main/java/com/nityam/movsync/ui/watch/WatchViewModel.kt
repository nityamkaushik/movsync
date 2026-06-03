package com.nityam.movsync.ui.watch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.nityam.movsync.MovSyncApp
import com.nityam.movsync.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WatchViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MovSyncApp).container
    private val _syncStatus = MutableStateFlow(SyncStatus.Synced)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus
    private var activeRoomCode: String? = null
    private var activePlayer: Player? = null

    fun start(roomCode: String, isHost: Boolean, player: Player) {
        if (activeRoomCode == roomCode && activePlayer == player) return
        activeRoomCode = roomCode
        activePlayer = player
        viewModelScope.launch {
            val userId = container.authRepository.ensureSignedIn()
            if (isHost) {
                container.syncEngine.startHost(roomCode, userId, player, viewModelScope)
            } else {
                container.syncEngine.startParticipant(roomCode, userId, player, viewModelScope) {
                    _syncStatus.value = it
                }
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
