package com.nityam.movsync.ui.lobby

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.MovSyncApp
import com.nityam.movsync.data.cloud.StorageToApi
import com.nityam.movsync.data.cloud.StorageToTransferService
import com.nityam.movsync.data.cloud.TransferState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface FileShareUiState {
    data object NoFileShared : FileShareUiState
    data class Uploading(val bytesUploaded: Long, val totalBytes: Long) : FileShareUiState
    data class Sharing(val fileName: String, val fileSize: Long) : FileShareUiState
    data class FileAvailable(val seederId: String, val fileName: String, val fileSize: Long, val shareUrl: String) : FileShareUiState
    data class Downloading(val bytesReceived: Long, val totalBytes: Long) : FileShareUiState
    data class Downloaded(val fileName: String, val fileUri: Uri) : FileShareUiState
    data class Verifying(val progress: Float?) : FileShareUiState
    data object Verified : FileShareUiState
    data class Error(val message: String) : FileShareUiState
}

class LobbyFileShareViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MovSyncApp).container
    private val appContext = application.applicationContext
    private val _state = MutableStateFlow<FileShareUiState>(FileShareUiState.NoFileShared)
    val state: StateFlow<FileShareUiState> = _state

    private val _videoUri = MutableStateFlow<Uri?>(null)
    val videoUri: StateFlow<Uri?> = _videoUri

    private var observeJob: Job? = null
    private var activeRoomCode: String? = null

    fun observeFileShare(roomCode: String, isHost: Boolean) {
        if (activeRoomCode == roomCode && observeJob != null) return
        activeRoomCode = roomCode
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            container.fileShareSignaling.observeFileShare(roomCode).collect { info ->
                val current = _state.value
                if (current is FileShareUiState.Verified || current is FileShareUiState.Downloading || current is FileShareUiState.Verifying) {
                    return@collect
                }

                _state.value = when {
                    info == null -> FileShareUiState.NoFileShared
                    isHost -> FileShareUiState.Sharing(info.fileName, info.fileSize)
                    else -> FileShareUiState.FileAvailable(info.seederId, info.fileName, info.fileSize, info.shareUrl)
                }
            }
        }
    }

    fun startSharing(roomCode: String, fileUri: Uri) {
        viewModelScope.launch {
            runCatching {
                val userId = container.authRepository.ensureSignedIn()
                val fileName = appContext.displayName(fileUri) ?: "movsync-video"
                val fileSize = appContext.fileSize(fileUri)

                // Start foreground service for upload
                val intent = Intent(appContext, StorageToTransferService::class.java).apply {
                    action = StorageToTransferService.ACTION_UPLOAD
                    putExtra(StorageToTransferService.EXTRA_FILE_URI, fileUri.toString())
                    putExtra(StorageToTransferService.EXTRA_FILE_NAME, fileName)
                    putExtra(StorageToTransferService.EXTRA_FILE_SIZE, fileSize)
                    putExtra(StorageToTransferService.EXTRA_ROOM_CODE, roomCode)
                }
                ContextCompat.startForegroundService(appContext, intent)

                // Observe transfer state until upload completes or fails
                StorageToTransferService.transferState.collect { state ->
                    when (state) {
                        is TransferState.Uploading -> {
                            _state.value = FileShareUiState.Uploading(state.bytesUploaded, state.totalBytes)
                        }
                        is TransferState.UploadComplete -> {
                            container.fileShareSignaling.publishFileShare(
                                roomCode, userId, fileName, fileSize, state.shareUrl
                            )
                            _state.value = FileShareUiState.Sharing(fileName, fileSize)
                        }
                        is TransferState.Failed -> {
                            _state.value = FileShareUiState.Error(state.error)
                        }
                        else -> {}
                    }
                }
            }.onFailure {
                _state.value = FileShareUiState.Error(it.message ?: "Could not start upload")
            }
        }
    }

    fun startDownload(roomCode: String) {
        val available = _state.value as? FileShareUiState.FileAvailable ?: return
        viewModelScope.launch {
            runCatching {
                _state.value = FileShareUiState.Downloading(0L, available.fileSize)

                // Start foreground service for download
                val intent = Intent(appContext, StorageToTransferService::class.java).apply {
                    action = StorageToTransferService.ACTION_DOWNLOAD
                    putExtra(StorageToTransferService.EXTRA_DOWNLOAD_URL, available.shareUrl)
                    putExtra(StorageToTransferService.EXTRA_FILE_NAME, available.fileName)
                    putExtra(StorageToTransferService.EXTRA_FILE_SIZE, available.fileSize)
                    putExtra(StorageToTransferService.EXTRA_ROOM_CODE, roomCode)
                }
                ContextCompat.startForegroundService(appContext, intent)

                // Observe transfer state until download completes or fails
                StorageToTransferService.transferState.collect { state ->
                    when (state) {
                        is TransferState.Downloading -> {
                            _state.value = FileShareUiState.Downloading(state.bytesDownloaded, state.totalBytes)
                        }
                        is TransferState.DownloadComplete -> {
                            _state.value = FileShareUiState.Downloaded(available.fileName, state.fileUri)
                            selectFile(appContext, state.fileUri, roomCode)
                        }
                        is TransferState.Failed -> {
                            _state.value = FileShareUiState.Error(state.error)
                        }
                        else -> {}
                    }
                }
            }.onFailure {
                _state.value = FileShareUiState.Error(it.message ?: "Download failed")
            }
        }
    }

    fun selectFile(context: Context, uri: Uri, roomCode: String) {
        viewModelScope.launch {
            runCatching {
                _state.value = FileShareUiState.Verifying(null)
                val fingerprint = container.fileHasher.computeQuickFingerprint(context, uri)
                val room = container.roomRepository.getRoomByCode(roomCode)
                    ?: error("Room not found")
                val expected = room.movieFingerprint
                    ?: error("This room does not have a movie fingerprint")
                if (fingerprint != expected) {
                    error("This file does not match the host's movie")
                }
                val userId = container.authRepository.ensureSignedIn()
                container.roomRepository.verifyParticipant(room.id, room.code, userId)
                _videoUri.value = uri
                _state.value = FileShareUiState.Verified
            }.onFailure {
                _state.value = FileShareUiState.Error(it.message ?: "Could not verify file")
            }
        }
    }

    fun cancelDownload() {
        val intent = Intent(appContext, StorageToTransferService::class.java).apply {
            action = StorageToTransferService.ACTION_CANCEL
        }
        appContext.startService(intent)
        _state.value = FileShareUiState.NoFileShared
    }

    fun cleanup() {
        observeJob?.cancel()
        observeJob = null
        activeRoomCode = null
    }

    override fun onCleared() {
        cleanup()
        super.onCleared()
    }
}

private fun Context.displayName(uri: Uri): String? {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
}

private fun Context.fileSize(uri: Uri): Long {
    return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
        }
        ?.takeIf { it > 0L }
        ?: runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull()
        ?: 0L
}
