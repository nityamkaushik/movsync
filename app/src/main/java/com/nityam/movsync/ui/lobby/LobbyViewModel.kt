package com.nityam.movsync.ui.lobby

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.MovSyncApp
import com.nityam.movsync.data.model.PresenceUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LobbyViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseSync = (application as MovSyncApp).container.firebaseSync
    private var presenceJob: Job? = null
    private val _participants = MutableStateFlow<List<PresenceUser>>(emptyList())
    val participants: StateFlow<List<PresenceUser>> = _participants

    fun observe(roomCode: String) {
        if (presenceJob != null) return
        presenceJob = viewModelScope.launch {
            firebaseSync.presence(roomCode).collect { _participants.value = it }
        }
    }
}
