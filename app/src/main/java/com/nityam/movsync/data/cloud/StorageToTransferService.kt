package com.nityam.movsync.data.cloud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream

class StorageToTransferService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    private var activeTransferJob: Job? = null

    private var pendingDownloadUri: Uri? = null

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPLOAD   -> handleUpload(intent)
            ACTION_DOWNLOAD -> handleDownload(intent)
            ACTION_CANCEL   -> handleCancel()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun handleUpload(intent: Intent) {
        val fileUriString = intent.getStringExtra(EXTRA_FILE_URI) ?: return
        val fileUri       = Uri.parse(fileUriString)
        val fileName      = intent.getStringExtra(EXTRA_FILE_NAME) ?: displayName(fileUri) ?: "movsync-video"
        val fileSize      = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)

        startForeground(NOTIFICATION_ID, buildProgressNotification("Uploading…", fileName, 0, 0))

        activeTransferJob = serviceScope.launch {
            runCatching {
                val result = StorageToApi.uploadFile(
                    fileName   = fileName,
                    contentType = "video/*",
                    fileSize   = fileSize,
                    openStream = { contentResolver.openInputStream(fileUri) ?: error("Cannot open file") },
                    onProgress = { uploaded, total ->
                        val state = TransferState.Uploading(uploaded, total, fileName)
                        transferState.value = state
                        updateProgressNotification("Uploading", fileName, uploaded, total)
                    }
                )

                transferState.value = TransferState.UploadComplete(result.shareUrl, result.rawUrl)
                showCompletionNotification("Upload Complete", fileName)
            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    val message = error.message ?: "Upload failed"
                    transferState.value = TransferState.Failed(message)
                    showFailureNotification(fileName)
                }
            }

            stopSelf()
        }
    }

    private fun handleDownload(intent: Intent) {
        val downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL) ?: return
        val fileName    = intent.getStringExtra(EXTRA_FILE_NAME) ?: "movsync-video"
        val fileSize    = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)

        startForeground(NOTIFICATION_ID, buildProgressNotification("Downloading…", fileName, 0, 0))

        activeTransferJob = serviceScope.launch {
            runCatching {
                val outputUri = createDownloadUri(fileName)
                pendingDownloadUri = outputUri

                val outputStream = BufferedOutputStream(
                    contentResolver.openOutputStream(outputUri)
                        ?: error("Cannot open download destination"),
                    WRITE_BUFFER_SIZE
                )

                StorageToApi.downloadFile(
                    url          = downloadUrl,
                    outputStream = outputStream,
                    totalBytes   = fileSize,
                    onProgress   = { downloaded, total ->
                        val state = TransferState.Downloading(downloaded, total, fileName)
                        transferState.value = state
                        updateProgressNotification("Downloading", fileName, downloaded, total)
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.update(
                        outputUri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null, null
                    )
                }

                pendingDownloadUri = null
                transferState.value = TransferState.DownloadComplete(outputUri)
                showCompletionNotification("Download Complete", fileName)
            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    val message = error.message ?: "Download failed"
                    transferState.value = TransferState.Failed(message)
                    showFailureNotification(fileName)
                }
            }

            stopSelf()
        }
    }

    private fun handleCancel() {
        activeTransferJob?.cancel()
        activeTransferJob = null

        pendingDownloadUri?.let { uri ->
            runCatching { contentResolver.delete(uri, null, null) }
        }
        pendingDownloadUri = null

        transferState.value = TransferState.Idle
        stopSelf()
    }

    private fun createDownloadUri(fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/*")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/MovSync")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "MovSync"
                )
                dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, java.io.File(dir, fileName).absolutePath)
            }
        }
        return contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create download file in MediaStore")
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows upload and download progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun cancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, StorageToTransferService::class.java).apply {
            action = ACTION_CANCEL
        }
        return PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildProgressNotification(
        contentText: String,
        fileName: String,
        progress: Int,
        max: Int
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(fileName)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent())

        if (max > 0) {
            builder.setProgress(max, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    @Volatile private var lastNotificationUpdateMs = 0L

    private fun updateProgressNotification(
        verb: String,
        fileName: String,
        done: Long,
        total: Long
    ) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateMs < NOTIFICATION_THROTTLE_MS) return
        lastNotificationUpdateMs = now

        val percent = if (total > 0) ((done * 100) / total).toInt() else 0
        val text = "$verb $percent%"
        notificationManager.notify(
            NOTIFICATION_ID,
            buildProgressNotification(text, fileName, percent, 100)
        )
    }

    private fun showCompletionNotification(title: String, fileName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(fileName)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showFailureNotification(fileName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Transfer Failed")
            .setContentText(fileName)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        val transferState = MutableStateFlow<TransferState>(TransferState.Idle)

        const val ACTION_UPLOAD   = "com.nityam.movsync.UPLOAD"
        const val ACTION_DOWNLOAD = "com.nityam.movsync.DOWNLOAD"
        const val ACTION_CANCEL   = "com.nityam.movsync.CANCEL_TRANSFER"

        const val EXTRA_FILE_URI      = "extra_file_uri"
        const val EXTRA_FILE_NAME     = "extra_file_name"
        const val EXTRA_FILE_SIZE     = "extra_file_size"
        const val EXTRA_DOWNLOAD_URL  = "extra_download_url"
        const val EXTRA_ROOM_CODE     = "extra_room_code"

        private const val CHANNEL_ID              = "movsync_transfers"
        private const val NOTIFICATION_ID         = 1001
        private const val WRITE_BUFFER_SIZE       = 256 * 1024
        private const val NOTIFICATION_THROTTLE_MS = 500L
    }
}

sealed interface TransferState {
    data object Idle : TransferState

    data class Uploading(
        val bytesUploaded: Long,
        val totalBytes: Long,
        val fileName: String
    ) : TransferState

    data class UploadComplete(
        val shareUrl: String,
        val rawUrl: String
    ) : TransferState

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val fileName: String
    ) : TransferState

    data class DownloadComplete(val fileUri: Uri) : TransferState

    data class Failed(val error: String) : TransferState
}
