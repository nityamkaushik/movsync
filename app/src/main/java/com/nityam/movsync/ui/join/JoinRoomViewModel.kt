package com.nityam.movsync.ui.join

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.MovSyncApp
import com.nityam.movsync.data.model.Room
import com.nityam.movsync.data.repository.JoinResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class JoinRoomViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MovSyncApp).container
    private val _state = MutableStateFlow(JoinRoomUiState())
    val state: StateFlow<JoinRoomUiState> = _state

    fun updateCode(value: String) {
        _state.value = _state.value.copy(
            code = value.filter { it.isLetter() }.uppercase().take(6),
            result = null,
            error = null
        )
    }

    fun joinWithFile(context: Context, uri: Uri) {
        _state.value = _state.value.copy(
            selectedUri = uri,
            verifying = true,
            error = null,
            result = null
        )
        viewModelScope.launch {
            runCatching {
                val fingerprint = container.fileHasher.computeQuickFingerprint(context, uri)
                val userId = container.authRepository.ensureSignedIn()
                val rawName = container.authRepository.displayName.first()
                val displayName = rawName.ifBlank { "Movie Friend" }
                when (val result = container.roomRepository.joinRoom(userId, displayName, _state.value.code, fingerprint)) {
                    is JoinResult.Joined -> _state.value = _state.value.copy(
                        verifying = false,
                        result = JoinResultUi.Joined(result.room)
                    )
                    is JoinResult.FingerprintMismatch -> _state.value = _state.value.copy(
                        verifying = false,
                        result = JoinResultUi.Mismatch(result.room)
                    )
                    JoinResult.NotFound -> _state.value = _state.value.copy(
                        verifying = false,
                        error = "Room not found"
                    )
                }
            }.onFailure {
                _state.value = _state.value.copy(
                    verifying = false,
                    error = it.message ?: "Could not join room"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

data class JoinRoomUiState(
    val code: String = "",
    val verifying: Boolean = false,
    val selectedUri: Uri? = null,
    val result: JoinResultUi? = null,
    val error: String? = null
)

sealed interface JoinResultUi {
    data class Joined(val room: Room) : JoinResultUi
    data class Mismatch(val room: Room) : JoinResultUi
    data object NotFound : JoinResultUi
}
