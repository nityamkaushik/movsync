package com.nityam.movsync.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val GITHUB_API_URL = "https://api.github.com/repos/nityamkaushik/movsync/releases/latest"
    private const val CHECK_COOLDOWN_MS = 60 * 60 * 1000L // 1 hour

    /** Timestamp of the last successful check, used to throttle repeated auto-checks. */
    @Volatile
    private var lastCheckTimestamp: Long = 0L

    sealed class UpdateState {
        data object Idle : UpdateState()
        data object Checking : UpdateState()
        data object UpToDate : UpdateState()
        data class Available(val version: String, val downloadUrl: String) : UpdateState()
        data class Downloading(val progress: Int) : UpdateState()
        data class ReadyToInstall(val apkFile: File) : UpdateState()
        data object Failed : UpdateState()
    }

    data class UpdateInfo(
        val isAvailable: Boolean,
        val latestVersion: String,
        val downloadUrl: String
    )

    fun isCooldownActive(): Boolean {
        return System.currentTimeMillis() - lastCheckTimestamp < CHECK_COOLDOWN_MS
    }

    suspend fun checkLatestRelease(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_API_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "MovSync-App")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val latestVersion = json.getString("tag_name")
                
                var downloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                }

                lastCheckTimestamp = System.currentTimeMillis()

                if (downloadUrl.isNotEmpty()) {
                    val updateAvailable = isNewerVersion(currentVersion, latestVersion)
                    return@withContext UpdateInfo(updateAvailable, latestVersion, downloadUrl)
                }
            } else {
                Log.w(TAG, "GitHub API returned HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
        } finally {
            connection?.disconnect()
        }
        return@withContext UpdateInfo(false, "", "")
    }

    fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
        val cleanCurrent = currentVersion.removePrefix("v").trim().split("-")[0]
        val cleanLatest = latestVersion.removePrefix("v").trim().split("-")[0]

        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val current = currentParts.getOrElse(i) { 0 }
            val latest = latestParts.getOrElse(i) { 0 }
            if (latest > current) return true
            if (current > latest) return false
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(downloadUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            connection.connect()

            // GitHub releases often redirect to Amazon S3
            if (connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP || connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM || connection.responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                connection = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                connection.connect()
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Download HTTP ${connection.responseCode} for $downloadUrl")
                return@withContext null
            }

            val fileLength = connection.contentLength
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "app-release.apk")
            if (apkFile.exists()) apkFile.delete()

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(apkFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    var lastEmittedProgress = -1

                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt().coerceIn(0, 100)
                            if (progress != lastEmittedProgress) {
                                lastEmittedProgress = progress
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                    output.flush()
                }
            }
            Log.d(TAG, "APK downloaded: ${apkFile.length()} bytes")
            return@withContext apkFile
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
        return@withContext null
    }

    fun canRequestPackageInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun redirectToInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not open install-permission settings: ${e.message}")
            }
        }
    }

    fun triggerApkInstall(context: Context, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch APK installer: ${e.message}", e)
        }
    }
}
