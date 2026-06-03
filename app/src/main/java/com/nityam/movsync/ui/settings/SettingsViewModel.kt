package com.nityam.movsync.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.MovSyncApp
import com.nityam.movsync.data.updater.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val updateManager = (application as MovSyncApp).container.updateManager

    private val _updateInfo = MutableStateFlow<UpdateManager.UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    fun checkForUpdates() {
        if (_isChecking.value || _isDownloading.value) return
        _isChecking.value = true
        viewModelScope.launch {
            val info = updateManager.checkForUpdates()
            _updateInfo.value = info
            _isChecking.value = false
        }
    }

    fun downloadUpdate() {
        val url = _updateInfo.value?.downloadUrl ?: return
        if (_isDownloading.value) return
        _isDownloading.value = true
        _downloadProgress.value = 0f
        viewModelScope.launch {
            updateManager.downloadAndInstallUpdate(url) { progress ->
                _downloadProgress.value = progress
            }
            _isDownloading.value = false
        }
    }
}
