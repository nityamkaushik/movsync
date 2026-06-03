package com.nityam.movsync.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.MovSyncApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = (application as MovSyncApp).container.authRepository
    private val updateManager = (application as MovSyncApp).container.updateManager

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable = _updateAvailable.asStateFlow()

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
            if (info.isUpdateAvailable) {
                _updateAvailable.value = true
            }
        }
    }

    fun updateDisplayName(name: String) {
        _displayName.value = name
        viewModelScope.launch { authRepository.saveDisplayName(name) }
    }
}
