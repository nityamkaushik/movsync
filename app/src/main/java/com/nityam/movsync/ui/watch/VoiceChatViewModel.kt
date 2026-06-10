package com.nityam.movsync.ui.watch

import android.app.Application
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.LiveKitOverrides
import io.livekit.android.AudioOptions
import io.livekit.android.AudioType
import io.livekit.android.RoomOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import io.ktor.client.call.body

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

class VoiceChatViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val supabaseClient = (application as com.nityam.movsync.MovSyncApp).container.supabaseClient
    
    private var room: Room? = null
    private var roomEventsJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

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
    } else null

    init {
        _headphonesConnected.value = checkHeadphonesConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        }
    }

    private fun checkHeadphonesConnected(): Boolean {
        val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any { device ->
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } else {
            @Suppress("DEPRECATION")
            return audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn
        }
    }

    fun joinRoom(roomId: String, participantName: String, participantIdentity: String) {
        // Leave any existing room first just in case
        leaveRoom()

        viewModelScope.launch {
            try {
                // 1. Fetch Token from Supabase Edge Function
                val tokenResponse = supabaseClient.functions.invoke(
                    "livekit-token",
                    LiveKitTokenRequest(
                        room = roomId,
                        participantName = participantName,
                        participantIdentity = participantIdentity
                    )
                ).body<LiveKitTokenResponse>()

                // 2. Determine appropriate audio output type based on headphone connection
                val hasHeadphones = checkHeadphonesConnected()
                val audioType = if (hasHeadphones) {
                    AudioType.MediaAudioType()
                } else {
                    AudioType.CallAudioType()
                }

                val currentRoom = LiveKit.create(
                    appContext = getApplication(),
                    options = RoomOptions(
                        adaptiveStream = true,
                        dynacast = true
                    ),
                    overrides = LiveKitOverrides(
                        audioOptions = AudioOptions(
                            audioOutputType = audioType
                        )
                    )
                )
                room = currentRoom

                // 3. Connect events flow for this room instance
                roomEventsJob = viewModelScope.launch {
                    currentRoom.events.events.collect { event ->
                        when (event) {
                            is RoomEvent.Connected -> {
                                _isConnected.value = true
                                _isMuted.value = !currentRoom.localParticipant.isMicrophoneEnabled()
                            }
                            is RoomEvent.Disconnected -> {
                                _isConnected.value = false
                                _isRemoteSpeaking.value = false
                            }
                            is RoomEvent.ActiveSpeakersChanged -> {
                                val remoteSpeaking = event.speakers.any { it is RemoteParticipant }
                                _isRemoteSpeaking.value = remoteSpeaking
                            }
                            else -> {}
                        }
                    }
                }

                // 4. Connect to LiveKit Room
                val url = BuildConfig.LIVEKIT_URL
                currentRoom.connect(url, tokenResponse.token)
                
                // Enable microphone by default after joining
                currentRoom.localParticipant.setMicrophoneEnabled(true)
                _isMuted.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                _isConnected.value = false
                _isRemoteSpeaking.value = false
            }
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            room?.let { currentRoom ->
                val currentlyEnabled = currentRoom.localParticipant.isMicrophoneEnabled()
                currentRoom.localParticipant.setMicrophoneEnabled(!currentlyEnabled)
                _isMuted.value = currentlyEnabled
            }
        }
    }

    fun leaveRoom() {
        roomEventsJob?.cancel()
        roomEventsJob = null
        
        room?.let { currentRoom ->
            try {
                currentRoom.disconnect()
                currentRoom.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        room = null
        _isConnected.value = false
        _isMuted.value = false
        _isRemoteSpeaking.value = false
    }

    override fun onCleared() {
        super.onCleared()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
        leaveRoom()
    }
}
