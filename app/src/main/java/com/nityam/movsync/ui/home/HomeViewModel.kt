package com.nityam.movsync.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.MovSyncApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = (application as MovSyncApp).container.authRepository

    val displayName = authRepository.displayName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "Movie Friend"
    )

    fun updateDisplayName(name: String) {
        viewModelScope.launch { authRepository.saveDisplayName(name) }
    }
}
