package com.nityam.movsync.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * GoFile REST API client.
 *
 * GoFile is a free, anonymous file-hosting service. No account or API key required.
 * Files are automatically deleted after ~10 days of inactivity.
 *
 * API base: https://api.gofile.io
 * Upload base: https://{server}.gofile.io/uploadFile
 */
object GoFileApi {

    /** GoFile's guest/anonymous website token. Update here if GoFile rotates it. */
    const val GUEST_TOKEN = "4fd6sg89d7s6"

    private const val BUFFER_SIZE = 256 * 1024                    // 256 KB read/write buffer
    private const val PROGRESS_REPORT_INTERVAL_BYTES = 512 * 1024L // Report every 512 KB
    private const val MAX_GET_RETRIES = 3
    private const val RETRY_DELAY_MS = 1_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // Unlimited read timeout for large file downloads
        .writeTimeout(0, TimeUnit.SECONDS)  // Unlimited write timeout for large file uploads
        .build()

    /**
     * Fetches the best available upload server from GoFile.
     *
     * GET https://api.gofile.io/servers
     * Response: {"status":"ok","data":{"servers":[{"name":"store1","zone":"eu"}, ...]}}
     *
     * @return Server name string, e.g. "store1"
     */
    suspend fun getServer(): String = withContext(Dispatchers.IO) {
        retryOnFailure {
            val request = Request.Builder()
                .url("https://api.gofile.io/servers")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: error("Empty response from GoFile server list")
            if (!response.isSuccessful) error("GoFile server list failed: ${response.code}")

            val json = JSONObject(body)
            if (json.getString("status") != "ok") error("GoFile server list error: $body")

            val servers = json.getJSONObject("data").getJSONArray("servers")
            if (servers.length() == 0) error("No GoFile servers available")

            // Pick the first available server
            servers.getJSONObject(0).getString("name")
        }
    }

    /**
     * Uploads a file to GoFile using multipart/form-data.
     *
     * POST https://{server}.gofile.io/uploadFile
     *
     * @param server       Server name from [getServer], e.g. "store1"
     * @param fileName     Display name of the file, e.g. "Movie.mp4"
     * @param inputStream  Content stream of the file
     * @param fileSize     Total file size in bytes (used for progress calculation)
     * @param onProgress   Called periodically with (bytesUploaded, totalBytes)
     * @return [GoFileUploadResult] containing the fileCode and download page URL
     */
    suspend fun uploadFile(
        server: String,
        fileName: String,
        inputStream: InputStream,
        fileSize: Long,
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit
    ): GoFileUploadResult = withContext(Dispatchers.IO) {
        val url = "https://$server.gofile.io/uploadFile"

        // Custom RequestBody that wraps the InputStream and reports progress
        val progressTrackingBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = fileSize

            override fun writeTo(sink: BufferedSink) {
                val buffer = ByteArray(BUFFER_SIZE)
                var totalWritten = 0L
                var lastReportedAt = 0L

                inputStream.use { stream ->
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        totalWritten += read

                        // Throttle progress callbacks to every ~200ms equivalent (every 512KB)
                        if (totalWritten - lastReportedAt >= PROGRESS_REPORT_INTERVAL_BYTES) {
                            onProgress(totalWritten, fileSize)
                            lastReportedAt = totalWritten
                        }
                    }
                }
                // Always report 100% at the end
                onProgress(fileSize, fileSize)
            }
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, progressTrackingBody)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(multipart)
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: error("Empty upload response from GoFile")
        if (!response.isSuccessful) error("GoFile upload failed (${response.code}): $body")

        val json = JSONObject(body)
        if (json.getString("status") != "ok") error("GoFile upload error: $body")

        val data = json.getJSONObject("data")
        GoFileUploadResult(
            fileCode = data.getString("code"),
            downloadPage = data.optString("downloadPage", "https://gofile.io/d/${data.getString("code")}")
        )
    }

    /**
     * Resolves the direct download URL for a file given its GoFile code.
     *
     * GET https://api.gofile.io/contents/{code}?wt=4fd6sg89d7s6&cache=true
     * Response contains a list of file entries, each with a direct "link" field.
     *
     * @param fileCode The file code returned by [uploadFile], e.g. "abc123"
     * @return Direct HTTPS download URL for the first file in the content
     */
    suspend fun getDirectDownloadUrl(fileCode: String): String = withContext(Dispatchers.IO) {
        retryOnFailure {
            val url = "https://api.gofile.io/contents/$fileCode?wt=$GUEST_TOKEN&cache=true"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $GUEST_TOKEN")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: error("Empty content response from GoFile")
            if (!response.isSuccessful) error("GoFile content fetch failed (${response.code}): $body")

            val json = JSONObject(body)
            if (json.getString("status") != "ok") error("GoFile content error: $body")

            val data = json.getJSONObject("data")
            val children = data.getJSONObject("children")
            val firstKey = children.keys().next()
            children.getJSONObject(firstKey).getString("link")
        }
    }

    /**
     * Downloads a file from a direct URL and writes it to the given OutputStream.
     *
     * @param url          Direct download URL from [getDirectDownloadUrl]
     * @param outputStream Target stream to write the downloaded bytes into
     * @param totalBytes   Expected file size (used for progress calculation)
     * @param onProgress   Called periodically with (bytesDownloaded, totalBytes)
     */
    suspend fun downloadFile(
        url: String,
        outputStream: OutputStream,
        totalBytes: Long,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Cookie", "accountToken=$GUEST_TOKEN")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) error("GoFile download failed (${response.code})")

        val responseBody = response.body ?: error("Empty download response body")
        val contentLength = if (totalBytes > 0) totalBytes else responseBody.contentLength()

        val buffer = ByteArray(BUFFER_SIZE)
        var totalRead = 0L
        var lastReportedAt = 0L

        responseBody.byteStream().use { inputStream ->
            outputStream.use { output ->
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    totalRead += read

                    if (totalRead - lastReportedAt >= PROGRESS_REPORT_INTERVAL_BYTES) {
                        onProgress(totalRead, contentLength)
                        lastReportedAt = totalRead
                    }
                }
                output.flush()
            }
        }

        onProgress(totalRead, contentLength)
    }

    /**
     * Retries a block up to [MAX_GET_RETRIES] times on [IOException].
     * Uses linear backoff: 1s, 2s, 3s.  Only use for idempotent GET operations.
     */
    private suspend fun <T> retryOnFailure(block: () -> T): T {
        var lastException: Exception? = null
        repeat(MAX_GET_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                lastException = e
                if (attempt < MAX_GET_RETRIES - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw lastException ?: IllegalStateException("Retry exhausted")
    }
}

/**
 * Result of a successful GoFile upload.
 *
 * @param fileCode     Unique content identifier, e.g. "abc123". Share this to allow others to download.
 * @param downloadPage Human-readable GoFile page, e.g. "https://gofile.io/d/abc123"
 */
data class GoFileUploadResult(
    val fileCode: String,
    val downloadPage: String
)
