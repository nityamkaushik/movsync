package com.nityam.movsync.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.MovSyncApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUpdateState {
    data object Idle : HomeUpdateState
    data class Available(val downloadUrl: String, val latestVersion: String) : HomeUpdateState
    data class Downloading(val progress: Float) : HomeUpdateState
    data object Installing : HomeUpdateState
    data object Failed : HomeUpdateState
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = (application as MovSyncApp).container.authRepository
    private val updateManager = (application as MovSyncApp).container.updateManager

    private val _updateState = MutableStateFlow<HomeUpdateState>(HomeUpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName = _displayName.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.displayName.collect { name ->
                if (_displayName.value != name) {
                    _displayName.value = name
                }
            }
        }
        viewModelScope.launch {
            val info = updateManager.checkForUpdates()
            if (info.isUpdateAvailable && info.downloadUrl != null) {
                _updateState.value = HomeUpdateState.Available(info.downloadUrl, info.latestVersion)
            }
        }
    }

    fun updateDisplayName(name: String) {
        _displayName.value = name
        viewModelScope.launch { authRepository.saveDisplayName(name) }
    }

    fun startUpdate() {
        val currentState = _updateState.value
        if (currentState !is HomeUpdateState.Available) return
        
        val url = currentState.downloadUrl
        viewModelScope.launch {
            _updateState.value = HomeUpdateState.Downloading(0f)
            val success = updateManager.downloadAndInstallUpdate(url) { progress ->
                _updateState.value = HomeUpdateState.Downloading(progress)
            }
            if (success) {
                _updateState.value = HomeUpdateState.Installing
            } else {
                _updateState.value = HomeUpdateState.Failed
                // Revert back so they can try again
                _updateState.value = currentState
            }
        }
    }
}
