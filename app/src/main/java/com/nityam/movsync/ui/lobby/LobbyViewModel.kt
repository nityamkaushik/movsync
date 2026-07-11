package com.nityam.movsync.ui.lobby

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.MovSyncApp
import com.nityam.movsync.data.model.PresenceUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LobbyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MovSyncApp
    private val firebaseSync = app.container.firebaseSync
    private val authRepository = app.container.authRepository
    private var presenceJob: Job? = null
    private var observedRoomCode: String? = null
    private val _participants = MutableStateFlow<List<PresenceUser>>(emptyList())
    val participants: StateFlow<List<PresenceUser>> = _participants

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started

    private val _allowControls = MutableStateFlow(false)
    val allowControls: StateFlow<Boolean> = _allowControls

    fun observe(roomCode: String, isHost: Boolean) {
        if (observedRoomCode == roomCode) return
        presenceJob?.cancel()
        observedRoomCode = roomCode
        presenceJob = viewModelScope.launch {
            launch { firebaseSync.presence(roomCode).collect { _participants.value = it } }
            launch { firebaseSync.observeRoomStarted(roomCode).collect { _started.value = it } }
            launch { firebaseSync.observeAllowControls(roomCode).collect { _allowControls.value = it } }
        }
        viewModelScope.launch {
            val userId = authRepository.ensureSignedIn()
            val displayName = authRepository.displayName.first().ifBlank { "Movie Friend" }
            firebaseSync.trackPresence(roomCode, userId, displayName, isHost, verified = isHost)
        }
    }

    fun toggleControls(roomCode: String, allow: Boolean) {
        viewModelScope.launch {
            firebaseSync.setAllowControls(roomCode, allow)
        }
    }

    fun startRoom(roomCode: String) {
        viewModelScope.launch {
            firebaseSync.setRoomStarted(roomCode)
        }
    }
}
