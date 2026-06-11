package com.nityam.movsync.ui.watch

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.BuildConfig
import com.nityam.movsync.MovSyncApp
import com.twilio.audioswitch.AudioDevice
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.livekit.android.AudioOptions
import io.livekit.android.AudioType
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.RemoteParticipant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.nio.charset.StandardCharsets

@Serializable
data class LiveKitTokenRequest(
    val room: String,
    val participantName: String,
    val participantIdentity: String
)

@Serializable
data class LiveKitTokenResponse(
    val token: String
)

enum class VoiceChatState {
    Disconnected,
    Connecting,
    Connected
}

class VoiceChatViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val container = (application as MovSyncApp).container
    private val supabaseClient = container.supabaseClient
    private val firebaseSync = container.firebaseSync

    private var room: Room? = null
    private var roomEventsJob: Job? = null
    private var connectionJob: Job? = null
    private var prefetchJob: Job? = null
    private var voiceActiveJob: Job? = null
    private var voiceSyncActionJob: Job? = null
    private var observedVoiceRoomCode: String? = null
    private var pendingLocalVoiceActive: Boolean? = null

    private var cachedToken: String? = null
    private var cachedRoomId: String? = null
    private var cachedParticipantName: String? = null
    private var cachedParticipantIdentity: String? = null

    private val _voiceChatState = MutableStateFlow(VoiceChatState.Disconnected)
    val voiceChatState: StateFlow<VoiceChatState> = _voiceChatState.asStateFlow()

    private val _voicePermissionNeeded = MutableStateFlow(false)
    val voicePermissionNeeded: StateFlow<Boolean> = _voicePermissionNeeded.asStateFlow()

    private val _peerConnected = MutableStateFlow(false)
    val peerConnected: StateFlow<Boolean> = _peerConnected.asStateFlow()

    private val _isRemoteSpeaking = MutableStateFlow(false)
    val isRemoteSpeaking: StateFlow<Boolean> = _isRemoteSpeaking.asStateFlow()

    private val _headphonesConnected = MutableStateFlow(false)
    val headphonesConnected: StateFlow<Boolean> = _headphonesConnected.asStateFlow()

    private val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                _headphonesConnected.value = checkHeadphonesConnected()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                _headphonesConnected.value = checkHeadphonesConnected()
            }
        }
    } else {
        null
    }

    init {
        _headphonesConnected.value = checkHeadphonesConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            val audioManager =
                getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        }
    }

    private fun checkHeadphonesConnected(): Boolean {
        val audioManager =
            getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any { device ->
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        }

        @Suppress("DEPRECATION")
        return audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn
    }

    fun prefetchToken(
        roomId: String,
        participantName: String,
        participantIdentity: String
    ) {
        if (roomId.isBlank() || participantIdentity.isBlank()) return

        val credentialsChanged =
            roomId != cachedRoomId ||
                participantName != cachedParticipantName ||
                participantIdentity != cachedParticipantIdentity

        if (!credentialsChanged && isCachedTokenValid()) return

        prefetchJob?.cancel()
        if (credentialsChanged) {
            cachedToken = null
        }
        cachedRoomId = roomId
        cachedParticipantName = participantName
        cachedParticipantIdentity = participantIdentity

        prefetchJob = viewModelScope.launch {
            try {
                val token = fetchToken(roomId, participantName, participantIdentity)
                if (
                    roomId == cachedRoomId &&
                    participantName == cachedParticipantName &&
                    participantIdentity == cachedParticipantIdentity
                ) {
                    cachedToken = token
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun observeVoiceActive(roomCode: String) {
        if (roomCode.isBlank()) return

        val normalizedRoomCode = roomCode.uppercase()
        if (observedVoiceRoomCode == normalizedRoomCode && voiceActiveJob?.isActive == true) {
            return
        }

        voiceActiveJob?.cancel()
        observedVoiceRoomCode = normalizedRoomCode
        voiceActiveJob = viewModelScope.launch {
            firebaseSync.observeVoiceActive(normalizedRoomCode).collect { active ->
                if (pendingLocalVoiceActive == active) {
                    pendingLocalVoiceActive = null
                    return@collect
                }

                if (active && _voiceChatState.value == VoiceChatState.Disconnected) {
                    if (hasRecordAudioPermission()) {
                        _voicePermissionNeeded.value = false
                        connectVoice()
                    } else {
                        _voicePermissionNeeded.value = true
                    }
                } else if (!active) {
                    _voicePermissionNeeded.value = false
                    if (_voiceChatState.value != VoiceChatState.Disconnected) {
                        leaveRoom()
                    }
                }
            }
        }
    }

    fun clearVoicePermissionRequest() {
        _voicePermissionNeeded.value = false
    }

    fun startVoiceForAll() {
        val roomId = cachedRoomId ?: return
        if (
            _voiceChatState.value != VoiceChatState.Disconnected ||
            voiceSyncActionJob?.isActive == true
        ) {
            return
        }

        voiceSyncActionJob = viewModelScope.launch {
            pendingLocalVoiceActive = true
            try {
                firebaseSync.setVoiceActive(roomId, true)
                connectVoice()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (pendingLocalVoiceActive == true) {
                    pendingLocalVoiceActive = null
                }
                voiceSyncActionJob = null
            }
        }
    }

    fun stopVoiceForAll() {
        val roomId = cachedRoomId ?: return
        if (
            _voiceChatState.value == VoiceChatState.Disconnected ||
            voiceSyncActionJob?.isActive == true
        ) {
            return
        }

        voiceSyncActionJob = viewModelScope.launch {
            pendingLocalVoiceActive = false
            try {
                firebaseSync.setVoiceActive(roomId, false)
                leaveRoom()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (pendingLocalVoiceActive == false) {
                    pendingLocalVoiceActive = null
                }
                voiceSyncActionJob = null
            }
        }
    }

    fun connectVoice() {
        if (_voiceChatState.value != VoiceChatState.Disconnected) return

        if (!hasRecordAudioPermission()) {
            _voicePermissionNeeded.value = true
            return
        }

        val roomId = cachedRoomId ?: return
        val participantName = cachedParticipantName ?: return
        val participantIdentity = cachedParticipantIdentity ?: return

        _voiceChatState.value = VoiceChatState.Connecting
        connectionJob = viewModelScope.launch {
            var currentRoom: Room? = null
            try {
                prefetchJob?.join()
                val token = cachedToken?.takeIf { isCachedTokenValid() }
                    ?: fetchToken(roomId, participantName, participantIdentity).also {
                        cachedToken = it
                    }

                releaseCurrentRoom()

                val hasHeadphones = checkHeadphonesConnected()
                val audioHandler = AudioSwitchHandler(getApplication()).apply {
                    preferredDeviceList = if (hasHeadphones) {
                        listOf(
                            AudioDevice.WiredHeadset::class.java,
                            AudioDevice.BluetoothHeadset::class.java,
                            AudioDevice.Speakerphone::class.java,
                            AudioDevice.Earpiece::class.java
                        )
                    } else {
                        listOf(
                            AudioDevice.Speakerphone::class.java,
                            AudioDevice.BluetoothHeadset::class.java,
                            AudioDevice.WiredHeadset::class.java,
                            AudioDevice.Earpiece::class.java
                        )
                    }
                    audioMode = AudioManager.MODE_IN_COMMUNICATION
                    audioStreamType = AudioManager.STREAM_VOICE_CALL
                    audioAttributeUsageType = AudioAttributes.USAGE_VOICE_COMMUNICATION
                    audioAttributeContentType = AudioAttributes.CONTENT_TYPE_SPEECH
                }

                currentRoom = LiveKit.create(
                    appContext = getApplication(),
                    options = RoomOptions(
                        adaptiveStream = true,
                        dynacast = true
                    ),
                    overrides = LiveKitOverrides(
                        audioOptions = AudioOptions(
                            audioOutputType = AudioType.CallAudioType(),
                            audioHandler = audioHandler
                        )
                    )
                )
                room = currentRoom
                collectRoomEvents(currentRoom)

                currentRoom.connect(BuildConfig.LIVEKIT_URL, token)
                currentRoom.localParticipant.setMicrophoneEnabled(true)
            } catch (e: CancellationException) {
                if (room === currentRoom) {
                    currentRoom?.let(::releaseRoom)
                    room = null
                }
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                cachedToken = null
                if (room === currentRoom) {
                    roomEventsJob?.cancel()
                    roomEventsJob = null
                    currentRoom?.let(::releaseRoom)
                    room = null
                }
                resetVoiceState()
            } finally {
                connectionJob = null
            }
        }
    }

    private fun collectRoomEvents(currentRoom: Room) {
        roomEventsJob?.cancel()
        roomEventsJob = viewModelScope.launch {
            currentRoom.events.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> {
                        _voiceChatState.value = VoiceChatState.Connected
                        _peerConnected.value = currentRoom.remoteParticipants.isNotEmpty()
                    }

                    is RoomEvent.Disconnected,
                    is RoomEvent.FailedToConnect -> {
                        resetVoiceState()
                    }

                    is RoomEvent.ParticipantConnected -> {
                        _peerConnected.value = true
                    }

                    is RoomEvent.ParticipantDisconnected -> {
                        _peerConnected.value = currentRoom.remoteParticipants.isNotEmpty()
                        _isRemoteSpeaking.value = false
                    }

                    is RoomEvent.ActiveSpeakersChanged -> {
                        _isRemoteSpeaking.value =
                            event.speakers.any { it is RemoteParticipant }
                    }

                    else -> Unit
                }
            }
        }
    }

    private suspend fun fetchToken(
        roomId: String,
        participantName: String,
        participantIdentity: String
    ): String {
        return supabaseClient.functions.invoke(
            "livekit-token",
            LiveKitTokenRequest(
                room = roomId,
                participantName = participantName,
                participantIdentity = participantIdentity
            )
        ).body<LiveKitTokenResponse>().token
    }

    private fun isCachedTokenValid(): Boolean {
        val token = cachedToken ?: return false
        return try {
            val encodedPayload = token.split('.').getOrNull(1) ?: return false
            val payload = String(
                Base64.decode(
                    encodedPayload,
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                ),
                StandardCharsets.UTF_8
            )
            val expiresAtMs = JSONObject(payload).optLong("exp", 0L) * 1000L
            expiresAtMs > System.currentTimeMillis() + TOKEN_EXPIRY_BUFFER_MS
        } catch (_: Exception) {
            false
        }
    }

    fun leaveRoom() {
        connectionJob?.cancel()
        connectionJob = null
        releaseCurrentRoom()
        resetVoiceState()
    }

    private fun releaseCurrentRoom() {
        roomEventsJob?.cancel()
        roomEventsJob = null
        room?.let(::releaseRoom)
        room = null
    }

    private fun releaseRoom(currentRoom: Room) {
        try {
            currentRoom.disconnect()
            currentRoom.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetVoiceState() {
        _voiceChatState.value = VoiceChatState.Disconnected
        _peerConnected.value = false
        _isRemoteSpeaking.value = false
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCleared() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            val audioManager =
                getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
        prefetchJob?.cancel()
        voiceActiveJob?.cancel()
        voiceActiveJob = null
        voiceSyncActionJob?.cancel()
        voiceSyncActionJob = null
        observedVoiceRoomCode = null
        pendingLocalVoiceActive = null
        leaveRoom()
        super.onCleared()
    }

    private companion object {
        const val TOKEN_EXPIRY_BUFFER_MS = 30_000L
    }
}
