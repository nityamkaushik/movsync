package com.nityam.movsync.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Participant(
    val id: String = "",
    @SerialName("room_id") val roomId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("is_host") val isHost: Boolean = false,
    @SerialName("fingerprint_verified") val fingerprintVerified: Boolean = false,
    @SerialName("full_hash_verified") val fullHashVerified: Boolean? = null,
    @SerialName("joined_at") val joinedAt: String? = null
)

@Serializable
data class ParticipantInsert(
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_host") val isHost: Boolean,
    @SerialName("fingerprint_verified") val fingerprintVerified: Boolean,
    @SerialName("full_hash_verified") val fullHashVerified: Boolean? = null
)
