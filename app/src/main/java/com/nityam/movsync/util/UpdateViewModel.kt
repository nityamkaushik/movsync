package com.nityam.movsync.util

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateManager.UpdateState>(UpdateManager.UpdateState.Idle)
    val updateState: StateFlow<UpdateManager.UpdateState> = _updateState.asStateFlow()

    fun checkForUpdates(currentVersion: String) {
        if (_updateState.value is UpdateManager.UpdateState.Checking || _updateState.value is UpdateManager.UpdateState.Downloading) return
        _updateState.value = UpdateManager.UpdateState.Checking

        viewModelScope.launch {
            val updateInfo = UpdateManager.checkLatestRelease(currentVersion)
            if (updateInfo.isAvailable) {
                _updateState.value = UpdateManager.UpdateState.Available(updateInfo.latestVersion, updateInfo.downloadUrl)
            } else {
                _updateState.value = UpdateManager.UpdateState.UpToDate
            }
        }
    }

    fun downloadUpdate(context: Context) {
        val currentState = _updateState.value
        if (currentState !is UpdateManager.UpdateState.Available) return
        if (_updateState.value is UpdateManager.UpdateState.Downloading) return

        val downloadUrl = currentState.downloadUrl
        val previousVersion = currentState.version

        _updateState.value = UpdateManager.UpdateState.Downloading(0)

        viewModelScope.launch {
            val apkFile = UpdateManager.downloadApk(context, downloadUrl) { progress ->
                _updateState.value = UpdateManager.UpdateState.Downloading(progress)
            }

            if (apkFile != null) {
                _updateState.value = UpdateManager.UpdateState.ReadyToInstall(apkFile)
            } else {
                _updateState.value = UpdateManager.UpdateState.Failed
                // Revert to available so they can try again
                _updateState.value = UpdateManager.UpdateState.Available(previousVersion, downloadUrl)
            }
        }
    }

    fun installUpdate(context: Context) {
        val currentState = _updateState.value
        if (currentState is UpdateManager.UpdateState.ReadyToInstall) {
            if (UpdateManager.canRequestPackageInstall(context)) {
                UpdateManager.triggerApkInstall(context, currentState.apkFile)
            } else {
                UpdateManager.redirectToInstallPermissionSettings(context)
            }
        }
    }
}
