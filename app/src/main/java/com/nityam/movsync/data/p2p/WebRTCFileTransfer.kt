package com.nityam.movsync.data.p2p

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WebRTCFileTransfer(private val context: Context) {
    private var scope = newScope()
    private val peers = ConcurrentHashMap<String, PeerConnection>()
    private val jobs = mutableListOf<Job>()
    private val factory: PeerConnectionFactory by lazy { createFactory(context.applicationContext) }
    private var activeDownload: DownloadSession? = null

    fun startSeeding(
        roomCode: String,
        fileUri: Uri,
        userId: String,
        firebaseDatabase: FirebaseDatabase,
        onPeerProgress: (peerId: String, progress: Float) -> Unit
    ) {
        ensureScope()
        val signaling = FileShareSignaling(firebaseDatabase)
        jobs += scope.launch {
            signaling.observeOffers(roomCode).collect { (peerId, sdp) ->
                if (peerId.startsWith(userId) || peers.containsKey(peerId) || peers.size >= MAX_PEERS) {
                    return@collect
                }
                launch {
                    runCatching {
                        acceptPeer(roomCode, peerId, sdp, fileUri, signaling, onPeerProgress)
                    }.onFailure {
                        closePeer(roomCode, peerId, signaling)
                    }
                }
            }
        }
    }

    suspend fun download(
        roomCode: String,
        seederId: String,
        userId: String,
        fileName: String,
        fileSize: Long,
        firebaseDatabase: FirebaseDatabase,
        onProgress: (bytesReceived: Long, totalBytes: Long) -> Unit
    ): Uri = withContext(Dispatchers.IO) {
        ensureScope()
        val signaling = FileShareSignaling(firebaseDatabase)
        val peerId = "$userId-${UUID.randomUUID()}"
        val outputUri = createDownloadUri(fileName)
        val output = BufferedOutputStream(
            context.contentResolver.openOutputStream(outputUri)
                ?: error("Cannot open download target"),
            WRITE_BUFFER_SIZE
        )
        val done = CompletableDeferred<Uri>()

        // Async write pipeline: decouple disk I/O from the WebRTC callback thread
        val writeChannel = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val writerJob = scope.launch(Dispatchers.IO) {
            try {
                for (data in writeChannel) {
                    output.write(data)
                }
                output.flush()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.update(
                        outputUri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null, null
                    )
                }
                done.complete(outputUri)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    done.completeExceptionally(e)
                }
            }
        }

        activeDownload = DownloadSession(
            peerId = peerId, output = output, done = done,
            uri = outputUri, writeChannel = writeChannel, writerJob = writerJob
        )

        val pc = createPeerConnection(
            onIceCandidate = { candidate ->
                scope.launch {
                    signaling.addCandidate(roomCode, peerId, "leecher", candidate.toInfo())
                }
            },
            onDataChannel = {}
        )
        peers[peerId] = pc

        val init = DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0
        }
        val channel = pc.createDataChannel("movsync-file", init)
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() = Unit

            override fun onMessage(buffer: DataChannel.Buffer) {
                handleDownloadMessage(buffer, fileSize, onProgress)
            }
        })

        jobs += scope.launch {
            signaling.observeAnswer(roomCode, peerId)
                .filterNotNull()
                .first()
                .let { answer ->
                    pc.awaitSetRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, answer))
                }
        }

        jobs += scope.launch {
            signaling.observeCandidates(roomCode, peerId, "seeder").collect { candidate ->
                pc.addIceCandidate(candidate.toWebRtcCandidate())
            }
        }

        val offer = pc.awaitCreateOffer()
        pc.awaitSetLocalDescription(offer)
        signaling.sendOffer(roomCode, peerId, offer.description)

        try {
            done.await()
        } finally {
            writeChannel.close()
            writerJob.cancel()
            output.closeQuietly()
            channel.close()
            closePeer(roomCode, peerId, signaling)
            activeDownload = null
            if (seederId.isBlank()) {
                // Keep parameter visible to callers and avoid accidental removal.
            }
        }
    }

    fun cancelDownload() {
        activeDownload?.let { session ->
            session.writeChannel.close()
            session.writerJob.cancel()
            session.output.closeQuietly()
            session.done.cancel()
        }
        activeDownload = null
    }

    fun stopSeeding() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        peers.values.forEach { it.close() }
        peers.clear()
    }

    fun cleanup() {
        cancelDownload()
        stopSeeding()
        scope.cancel()
    }

    private suspend fun acceptPeer(
        roomCode: String,
        peerId: String,
        offerSdp: String,
        fileUri: Uri,
        signaling: FileShareSignaling,
        onPeerProgress: (peerId: String, progress: Float) -> Unit
    ) {
        val pc = createPeerConnection(
            onIceCandidate = { candidate ->
                scope.launch {
                    signaling.addCandidate(roomCode, peerId, "seeder", candidate.toInfo())
                }
            },
            onDataChannel = { channel ->
                channel.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) = Unit

                    override fun onStateChange() {
                        if (channel.state() == DataChannel.State.OPEN) {
                            scope.launch(Dispatchers.IO) {
                                sendFile(channel, fileUri) { progress ->
                                    onPeerProgress(peerId, progress)
                                }
                            }
                        }
                    }

                    override fun onMessage(buffer: DataChannel.Buffer) = Unit
                })
            }
        )
        peers[peerId] = pc

        jobs += scope.launch {
            signaling.observeCandidates(roomCode, peerId, "leecher").collect { candidate ->
                pc.addIceCandidate(candidate.toWebRtcCandidate())
            }
        }

        pc.awaitSetRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        val answer = pc.awaitCreateAnswer()
        pc.awaitSetLocalDescription(answer)
        signaling.sendAnswer(roomCode, peerId, answer.description)
    }

    private suspend fun sendFile(
        channel: DataChannel,
        fileUri: Uri,
        onProgress: (Float) -> Unit
    ) {
        val fileName = context.displayName(fileUri) ?: "movsync-video"
        val fileSize = context.fileSize(fileUri)
        channel.sendText(
            """{"type":"header","fileName":${fileName.jsonString()},"fileSize":$fileSize,"totalChunks":${(fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE}}"""
        )

        // Callback-driven flow control: suspend until buffer drains instead of polling
        val bufferDrain = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {
                if (channel.bufferedAmount() < BUFFER_LOW_THRESHOLD) {
                    bufferDrain.trySend(Unit)
                }
            }
            override fun onStateChange() = Unit
            override fun onMessage(buffer: DataChannel.Buffer) = Unit
        })

        var sent = 0L
        var chunkIndex = 0
        var lastProgressNanos = 0L
        val readBuf = ByteArray(CHUNK_SIZE)
        // Pre-allocate payload buffer: 4-byte seq header + chunk data
        val payload = ByteArray(SEQ_HEADER_SIZE + CHUNK_SIZE)
        val input = context.contentResolver.openInputStream(fileUri)
            ?: error("Cannot open selected video")
        BufferedInputStream(input, CHUNK_SIZE).use { buffered ->
            while (scope.isActive) {
                val read = buffered.read(readBuf)
                if (read <= 0) break
                // Write 4-byte big-endian sequence number
                payload[0] = (chunkIndex shr 24).toByte()
                payload[1] = (chunkIndex shr 16).toByte()
                payload[2] = (chunkIndex shr 8).toByte()
                payload[3] = chunkIndex.toByte()
                System.arraycopy(readBuf, 0, payload, SEQ_HEADER_SIZE, read)
                val size = SEQ_HEADER_SIZE + read
                channel.send(DataChannel.Buffer(ByteBuffer.wrap(payload, 0, size), true))
                chunkIndex++
                sent += read
                val now = System.nanoTime()
                if (now - lastProgressNanos > PROGRESS_INTERVAL_NS) {
                    onProgress(if (fileSize > 0L) sent.toFloat() / fileSize else 0f)
                    lastProgressNanos = now
                }
                if (channel.bufferedAmount() > BUFFER_HIGH_THRESHOLD) {
                    bufferDrain.receive()
                }
            }
        }

        bufferDrain.close()
        channel.sendText("""{"type":"complete"}""")
        onProgress(1f)
    }

    private fun handleDownloadMessage(
        buffer: DataChannel.Buffer,
        fileSize: Long,
        onProgress: (bytesReceived: Long, totalBytes: Long) -> Unit
    ) {
        val raw = ByteArray(buffer.data.remaining())
        buffer.data.get(raw)

        if (!buffer.binary) {
            val message = raw.toString(Charsets.UTF_8)
            if (message.contains("\"type\":\"complete\"")) {
                val session = activeDownload ?: return
                session.completeReceived = true
                // If all chunks already received and in order, finalize
                if (session.reorderBuffer.isEmpty()) {
                    session.writeChannel.close()
                }
            }
            return
        }

        val session = activeDownload ?: return
        if (raw.size < SEQ_HEADER_SIZE) return

        // Extract 4-byte big-endian sequence number
        val seq = ((raw[0].toInt() and 0xFF) shl 24) or
                  ((raw[1].toInt() and 0xFF) shl 16) or
                  ((raw[2].toInt() and 0xFF) shl 8) or
                  (raw[3].toInt() and 0xFF)
        val data = raw.copyOfRange(SEQ_HEADER_SIZE, raw.size)
        session.bytesReceived += data.size

        // Reorder: write sequentially, buffer out-of-order chunks
        if (seq == session.nextExpectedSeq) {
            session.writeChannel.trySend(data)
            session.nextExpectedSeq++
            // Flush any consecutive buffered chunks
            while (true) {
                val next = session.reorderBuffer.remove(session.nextExpectedSeq) ?: break
                session.writeChannel.trySend(next)
                session.nextExpectedSeq++
            }
        } else {
            session.reorderBuffer[seq] = data
        }

        // Throttle progress updates
        val now = System.nanoTime()
        if (now - session.lastProgressNanos > PROGRESS_INTERVAL_NS) {
            onProgress(session.bytesReceived, fileSize)
            session.lastProgressNanos = now
        }

        // All chunks received and in order
        if (session.completeReceived && session.reorderBuffer.isEmpty()) {
            onProgress(session.bytesReceived, fileSize)
            session.writeChannel.close()
        }
    }

    private fun createPeerConnection(
        onIceCandidate: (IceCandidate) -> Unit,
        onDataChannel: (DataChannel) -> Unit
    ): PeerConnection {
        val config = PeerConnection.RTCConfiguration(
            ICE_SERVERS.map { PeerConnection.IceServer.builder(it).createIceServer() }
        )
        return factory.createPeerConnection(config, object : PeerObserver() {
            override fun onIceCandidate(candidate: IceCandidate) {
                onIceCandidate(candidate)
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                onDataChannel(dataChannel)
            }
        }) ?: error("Could not create WebRTC peer connection")
    }

    private suspend fun closePeer(roomCode: String, peerId: String, signaling: FileShareSignaling) {
        peers.remove(peerId)?.close()
        runCatching { signaling.clearSignaling(roomCode, peerId) }
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
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "MovSync")
                dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, File(dir, fileName).absolutePath)
            }
        }
        return context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create download file")
    }

    private fun ensureScope() {
        if (!scope.isActive) scope = newScope()
    }

    private class DownloadSession(
        val peerId: String,
        val output: OutputStream,
        val done: CompletableDeferred<Uri>,
        var bytesReceived: Long = 0L,
        val uri: Uri,
        val writeChannel: kotlinx.coroutines.channels.Channel<ByteArray>,
        val writerJob: Job,
        var nextExpectedSeq: Int = 0,
        val reorderBuffer: HashMap<Int, ByteArray> = HashMap(),
        var completeReceived: Boolean = false,
        var lastProgressNanos: Long = 0L
    )

    companion object {
        private const val CHUNK_SIZE = 1 * 1024 * 1024
        private const val SEQ_HEADER_SIZE = 4
        private const val BUFFER_HIGH_THRESHOLD = 8 * 1024 * 1024L
        private const val BUFFER_LOW_THRESHOLD = 2 * 1024 * 1024L
        private const val WRITE_BUFFER_SIZE = 2 * 1024 * 1024
        private const val MAX_PEERS = 20
        private const val PROGRESS_INTERVAL_NS = 50_000_000L // 50ms = 20fps max
        private val ICE_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302"
        )
        @Volatile private var initialized = false

        private fun createFactory(context: Context): PeerConnectionFactory {
            if (!initialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions()
                )
                initialized = true
            }
            return PeerConnectionFactory.builder().createPeerConnectionFactory()
        }

        private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

private open class PeerObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
    override fun onIceCandidate(candidate: IceCandidate) = Unit
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
    override fun onAddStream(stream: MediaStream) = Unit
    override fun onRemoveStream(stream: MediaStream) = Unit
    override fun onDataChannel(dataChannel: DataChannel) = Unit
    override fun onRenegotiationNeeded() = Unit
    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit
}

private suspend fun PeerConnection.awaitCreateOffer(): SessionDescription {
    val deferred = CompletableDeferred<SessionDescription>()
    createOffer(object : SdpObserverAdapter() {
        override fun onCreateSuccess(description: SessionDescription) {
            deferred.complete(description)
        }

        override fun onCreateFailure(error: String) {
            deferred.completeExceptionally(IllegalStateException(error))
        }
    }, MediaConstraints())
    return deferred.await()
}

private suspend fun PeerConnection.awaitCreateAnswer(): SessionDescription {
    val deferred = CompletableDeferred<SessionDescription>()
    createAnswer(object : SdpObserverAdapter() {
        override fun onCreateSuccess(description: SessionDescription) {
            deferred.complete(description)
        }

        override fun onCreateFailure(error: String) {
            deferred.completeExceptionally(IllegalStateException(error))
        }
    }, MediaConstraints())
    return deferred.await()
}

private suspend fun PeerConnection.awaitSetLocalDescription(description: SessionDescription) {
    val deferred = CompletableDeferred<Unit>()
    setLocalDescription(object : SdpObserverAdapter() {
        override fun onSetSuccess() {
            deferred.complete(Unit)
        }

        override fun onSetFailure(error: String) {
            deferred.completeExceptionally(IllegalStateException(error))
        }
    }, description)
    deferred.await()
}

private suspend fun PeerConnection.awaitSetRemoteDescription(description: SessionDescription) {
    val deferred = CompletableDeferred<Unit>()
    setRemoteDescription(object : SdpObserverAdapter() {
        override fun onSetSuccess() {
            deferred.complete(Unit)
        }

        override fun onSetFailure(error: String) {
            deferred.completeExceptionally(IllegalStateException(error))
        }
    }, description)
    deferred.await()
}

private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String) = Unit
    override fun onSetFailure(error: String) = Unit
}

private fun IceCandidate.toInfo() = IceCandidateInfo(
    sdpMid = sdpMid,
    sdpMLineIndex = sdpMLineIndex,
    candidate = sdp
)

private fun IceCandidateInfo.toWebRtcCandidate() = IceCandidate(sdpMid, sdpMLineIndex, candidate)

private fun DataChannel.sendText(value: String) {
    send(DataChannel.Buffer(ByteBuffer.wrap(value.toByteArray(Charsets.UTF_8)), false))
}

private fun OutputStream.closeQuietly() {
    runCatching { close() }
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

private fun String.jsonString(): String {
    return buildString {
        append('"')
        this@jsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}
