package com.nityam.movsync.ui.create

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.MovSyncApp
import com.nityam.movsync.data.model.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CreateRoomViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MovSyncApp).container
    private val _state = MutableStateFlow<CreateRoomUiState>(CreateRoomUiState.SelectFile)
    val state: StateFlow<CreateRoomUiState> = _state

    fun createFromFile(context: Context, uri: Uri) {
        _state.value = CreateRoomUiState.Hashing(null)
        viewModelScope.launch {
            runCatching {
                val fingerprint = container.fileHasher.computeQuickFingerprint(context, uri)
                val userId = container.authRepository.ensureSignedIn()
                val displayName = container.authRepository.displayName.first()
                val room = container.roomRepository.createRoom(
                    hostId = userId,
                    displayName = displayName,
                    fingerprint = fingerprint,
                    movieName = context.displayName(uri),
                    durationMs = context.videoDuration(uri)
                )
                _state.value = CreateRoomUiState.RoomCreated(room, uri)
            }.onFailure {
                _state.value = CreateRoomUiState.Error(it.message ?: "Could not create room")
            }
        }
    }

    fun reset() {
        _state.value = CreateRoomUiState.SelectFile
    }
}

sealed interface CreateRoomUiState {
    data object SelectFile : CreateRoomUiState
    data class Hashing(val progress: Float?) : CreateRoomUiState
    data class RoomCreated(val room: Room, val uri: Uri) : CreateRoomUiState
    data class Error(val message: String) : CreateRoomUiState
}

private fun Context.displayName(uri: Uri): String? {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
}

private fun Context.videoDuration(uri: Uri): Long? {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        retriever.release()
        duration
    }.getOrNull()
}
