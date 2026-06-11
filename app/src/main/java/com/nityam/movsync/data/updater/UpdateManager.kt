package com.nityam.movsync.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.nityam.movsync.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val latestVersion: String,
        val downloadUrl: String?,
        val downloadedApkFile: File? = null
    )

    suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/nityamkaushik/movsync/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                
                val tagName = json.getString("tag_name")
                var downloadUrl: String? = null
                
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val browserUrl = asset.getString("browser_download_url")
                    if (browserUrl.endsWith(".apk")) {
                        downloadUrl = browserUrl
                        break
                    }
                }

                val currentVersion = BuildConfig.VERSION_NAME
                val latestClean = tagName.removePrefix("v").trim()
                val currentClean = currentVersion.removePrefix("v").trim()
                
                var downloadedApkFile: File? = null
                val updateDir = File(context.cacheDir, "updates")
                val apkFile = File(updateDir, "update.apk")
                if (apkFile.exists() && apkFile.length() > 0L) {
                    val pi = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                    val apkVersion = pi?.versionName?.removePrefix("v")?.trim()
                    
                    if (apkVersion == currentClean) {
                        apkFile.delete()
                    } else if (apkVersion == latestClean) {
                        downloadedApkFile = apkFile
                    } else {
                        apkFile.delete()
                    }
                }

                return@withContext UpdateInfo(
                    isUpdateAvailable = latestClean != currentClean && downloadUrl != null,
                    latestVersion = latestClean,
                    downloadUrl = downloadUrl,
                    downloadedApkFile = downloadedApkFile
                )
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to check for updates", e)
        }
        UpdateInfo(false, BuildConfig.VERSION_NAME, null)
    }

    suspend fun downloadAndInstallUpdate(downloadUrl: String, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()
            val apkFile = File(updateDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            val fileLength = connection.contentLength
            val input = connection.inputStream
            val output = FileOutputStream(apkFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    onProgress(total.toFloat() / fileLength.toFloat())
                }
                output.write(data, 0, count)
            }

            output.flush()
            output.close()
            input.close()

            triggerInstall(apkFile)
            apkFile
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to download update", e)
            null
        }
    }

    fun triggerInstall(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun installApk(apkFile: File) {
        triggerInstall(apkFile)
    }
}
