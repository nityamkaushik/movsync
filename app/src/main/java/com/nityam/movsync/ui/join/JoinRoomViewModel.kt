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
        val code = _state.value.code
        if (code.length != 6) {
            _state.value = _state.value.copy(error = "Enter the 6 character room code")
            return
        }
        _state.value = _state.value.copy(verifying = true, error = null, selectedUri = uri)
        viewModelScope.launch {
            runCatching {
                val fingerprint = container.fileHasher.computeQuickFingerprint(context, uri)
                val userId = container.authRepository.ensureSignedIn()
                val rawName = container.authRepository.displayName.first()
                val displayName = rawName.ifBlank { "Movie Friend" }
                when (val result = container.roomRepository.joinRoom(userId, displayName, code, fingerprint)) {
                    is JoinResult.Joined -> _state.value = _state.value.copy(
                        verifying = false,
                        result = JoinResultUi.Joined(result.room, uri)
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
}

data class JoinRoomUiState(
    val code: String = "",
    val verifying: Boolean = false,
    val selectedUri: Uri? = null,
    val result: JoinResultUi? = null,
    val error: String? = null
)

sealed interface JoinResultUi {
    data class Joined(val room: Room, val uri: Uri) : JoinResultUi
    data class Mismatch(val room: Room) : JoinResultUi
}
