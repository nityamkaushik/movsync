package com.nityam.movsync.data.sync

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.math.min

class FileHasher {
    suspend fun computeQuickFingerprint(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open selected video")
        descriptor.use { fd ->
            val fileSize = resolveFileSize(context, uri, fd.statSize)
            require(fileSize > 0L) { "Cannot determine file size" }

            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                val chunkSize = 4L * 1024L * 1024L
                updateDigestFrom(channel, digest, 0L, min(chunkSize, fileSize).toInt())

                if (fileSize > chunkSize * 2L) {
                    val middle = (fileSize / 2L) - (chunkSize / 2L)
                    updateDigestFrom(channel, digest, middle, chunkSize.toInt())
                }

                if (fileSize > chunkSize) {
                    updateDigestFrom(channel, digest, fileSize - chunkSize, chunkSize.toInt())
                }
            }

            digest.update(fileSize.toString().toByteArray())
            digest.hex()
        }
    }

    suspend fun computeFullHash(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open selected video")
        descriptor.use { fd ->
            val fileSize = resolveFileSize(context, uri, fd.statSize).coerceAtLeast(1L)
            FileInputStream(fd.fileDescriptor).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read: Int
                var total = 0L
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                    total += read
                    onProgress((total.toFloat() / fileSize).coerceIn(0f, 1f))
                }
            }
        }
        digest.hex()
    }

    private fun resolveFileSize(context: Context, uri: Uri, statSize: Long): Long {
        if (statSize > 0L) return statSize
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
            }
            ?: -1L
    }

    private fun updateDigestFrom(
        channel: java.nio.channels.FileChannel,
        digest: MessageDigest,
        position: Long,
        bytesToRead: Int
    ) {
        val buffer = java.nio.ByteBuffer.allocate(bytesToRead)
        var cursor = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, cursor)
            if (read <= 0) break
            cursor += read
        }
        buffer.flip()
        digest.update(buffer)
    }

    private fun MessageDigest.hex(): String {
        return digest().joinToString("") { "%02x".format(it) }
    }
}
