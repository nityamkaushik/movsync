package com.nityam.movsync.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

object StorageToApi {

    private const val API_BASE = "https://storage.to/api"
    private const val BUFFER_SIZE = 1024 * 1024
    private const val BUFFER_SIZE_DOWNLOAD = 4 * 1024 * 1024
    private const val MULTIPART_CONCURRENCY = 4

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .build()

    private var visitorToken: String? = null

    private fun visitorToken(): String {
        val existing = visitorToken
        if (existing != null) return existing
        val newToken = UUID.randomUUID().toString()
        visitorToken = newToken
        return newToken
    }

    suspend fun uploadFile(
        fileName: String,
        contentType: String,
        fileSize: Long,
        openStream: () -> InputStream,
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit
    ): StorageToUploadResult = withContext(Dispatchers.IO) {
        val token = visitorToken()
        val initBody = JSONObject().apply {
            put("filename", fileName)
            put("content_type", contentType)
            put("size", fileSize)
        }

        val initRequest = Request.Builder()
            .url("$API_BASE/upload/init")
            .post(initBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("X-Visitor-Token", token)
            .build()

        val initResponse = client.newCall(initRequest).execute()
        val initJson = JSONObject(initResponse.body?.string()
            ?: error("Empty init response"))
        if (!initJson.optBoolean("success", false))
            error("Init failed: ${initJson.optString("error", "unknown")}")

        val uploadType = initJson.getString("type")
        val r2Key = initJson.getString("r2_key")

        when (uploadType) {
            "single" -> {
                val uploadUrl = initJson.getString("upload_url")
                uploadSingle(uploadUrl, openStream(), fileSize, onProgress)
            }
            "multipart" -> {
                val uploadId = initJson.getString("upload_id")
                val partSize = initJson.getInt("part_size")
                val totalParts = initJson.getInt("total_parts")
                val initialUrlsJson = initJson.getJSONObject("initial_urls")
                val ownerToken = initJson.optString("owner_token", "")
                uploadMultipartParallel(
                    uploadId, r2Key, partSize, totalParts, initialUrlsJson,
                    ownerToken, fileSize, onProgress, token, openStream
                )
            }
            else -> error("Unknown upload type: $uploadType")
        }

        val confirmBody = JSONObject().apply {
            put("filename", fileName)
            put("size", fileSize)
            put("content_type", contentType)
            put("r2_key", r2Key)
        }
        val confirmRequest = Request.Builder()
            .url("$API_BASE/upload/confirm")
            .post(confirmBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("X-Visitor-Token", token)
            .build()

        val confirmResponse = client.newCall(confirmRequest).execute()
        val confirmJson = JSONObject(confirmResponse.body?.string()
            ?: error("Empty confirm response"))
        if (!confirmJson.optBoolean("success", false))
            error("Confirm failed: ${confirmJson.optString("error", "unknown")}")

        val file = confirmJson.getJSONObject("file")
        StorageToUploadResult(
            fileId = file.getString("id"),
            shareUrl = file.getString("url"),
            rawUrl = file.getString("raw_url")
        )
    }

    private fun uploadSingle(
        uploadUrl: String,
        inputStream: InputStream,
        fileSize: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val body = progressTrackingBody(inputStream, fileSize, onProgress)
        val request = Request.Builder()
            .url(uploadUrl)
            .put(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            error("R2 upload failed (${response.code})")
        }
    }

    private suspend fun uploadMultipartParallel(
        uploadId: String,
        r2Key: String,
        partSize: Int,
        totalParts: Int,
        initialUrlsJson: JSONObject,
        ownerToken: String,
        fileSize: Long,
        onProgress: (Long, Long) -> Unit,
        visitorToken: String,
        openStream: () -> InputStream
    ) {
        val semaphore = Semaphore(MULTIPART_CONCURRENCY)
        val partResults = mutableMapOf<Int, String>()

        val partUrls = mutableMapOf<Int, String>()
        for (i in 1..totalParts) {
            partUrls[i] = if (initialUrlsJson.has(i.toString())) {
                initialUrlsJson.getString(i.toString())
            } else {
                getPartUrl(uploadId, i, ownerToken, visitorToken)
            }
        }

        var completedBytes = 0L
        val completedLock = Any()

        coroutineScope {
            val deferreds = (1..totalParts).map { partNumber ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val start = (partNumber - 1) * partSize.toLong()
                        val thisPartSize = minOf(partSize.toLong(), fileSize - start)
                        if (thisPartSize <= 0) return@withPermit

                        val url = partUrls[partNumber] ?: getPartUrl(uploadId, partNumber, ownerToken, visitorToken)
                        val etag = uploadPart(url, openStream, start, thisPartSize, partNumber)

                        synchronized(completedLock) {
                            completedBytes += thisPartSize
                            onProgress(completedBytes, fileSize)
                        }

                        synchronized(partResults) {
                            partResults[partNumber] = etag
                        }
                    }
                }
            }
            deferreds.awaitAll()
        }

        val sortedParts = partResults.entries.sortedBy { it.key }
        val partsArray = JSONArray()
        for ((pn, etag) in sortedParts) {
            partsArray.put(JSONObject().apply {
                put("partNumber", pn)
                put("etag", etag)
            })
        }

        val completeBody = JSONObject().apply {
            put("upload_id", uploadId)
            put("parts", partsArray)
        }
        val completeRequest = Request.Builder()
            .url("$API_BASE/upload/complete-multipart")
            .post(completeBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Owner $ownerToken")
            .build()

        val completeResponse = client.newCall(completeRequest).execute()
        val completeJson = JSONObject(completeResponse.body?.string()
            ?: error("Empty complete-multipart response"))
        if (!completeJson.optBoolean("success", false)) {
            error("Complete multipart failed: ${completeJson.optString("error", "unknown")}")
        }
    }

    private fun uploadPart(
        url: String,
        openStream: () -> InputStream,
        offset: Long,
        size: Long,
        partNumber: Int
    ): String {
        val stream = openStream()
        stream.use { s ->
            s.skip(offset)
            val data = ByteArray(size.toInt())
            var read = 0
            while (read < size) {
                val n = s.read(data, read, (size - read).toInt())
                if (n == -1) break
                read += n
            }

            val body = RequestBody.create(null, data)
            val request = Request.Builder()
                .url(url)
                .put(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                error("R2 part $partNumber upload failed (${response.code})")
            }
            return response.header("ETag") ?: ""
        }
    }

    private fun getPartUrl(uploadId: String, partNumber: Int, ownerToken: String, visitorToken: String): String {
        val body = JSONObject().apply {
            put("upload_id", uploadId)
            put("part_numbers", JSONArray(listOf(partNumber)))
        }
        val request = Request.Builder()
            .url("$API_BASE/upload/parts")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Owner $ownerToken")
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: error("Empty parts response"))
        if (!json.optBoolean("success", false))
            error("Get part URL failed: ${json.optString("error", "unknown")}")

        val urls = json.getJSONArray("part_urls")
        return urls.getJSONObject(0).getString("url")
    }

    suspend fun downloadFile(
        url: String,
        outputStream: java.io.OutputStream,
        totalBytes: Long,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) error("Download failed (${response.code})")

        val responseBody = response.body ?: error("Empty download response body")
        val contentLength = if (totalBytes > 0) totalBytes else responseBody.contentLength()

        val buffer = ByteArray(BUFFER_SIZE_DOWNLOAD)
        var totalRead = 0L

        responseBody.byteStream().use { inputStream ->
            outputStream.use { output ->
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    totalRead += read
                    onProgress(totalRead, contentLength)
                }
                output.flush()
            }
        }
        onProgress(totalRead, contentLength)
    }

    private fun progressTrackingBody(
        inputStream: InputStream,
        fileSize: Long,
        onProgress: (Long, Long) -> Unit
    ) = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
        override fun contentLength() = fileSize

        override fun writeTo(sink: BufferedSink) {
            val buffer = ByteArray(BUFFER_SIZE)
            var totalWritten = 0L

            inputStream.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    sink.write(buffer, 0, read)
                    totalWritten += read
                    onProgress(totalWritten, fileSize)
                }
            }
            onProgress(fileSize, fileSize)
        }
    }

    private data class MultipartPartResult(val partNumber: Int, val etag: String)

    fun resetVisitorToken() {
        visitorToken = null
    }
}

data class StorageToUploadResult(
    val fileId: String,
    val shareUrl: String,
    val rawUrl: String
)

private fun String.toRequestBody(contentType: okhttp3.MediaType): RequestBody =
    RequestBody.create(contentType, this)
