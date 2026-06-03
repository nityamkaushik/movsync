package com.nityam.movsync.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Room(
    val id: String = "",
    val code: String = "",
    @SerialName("host_id") val hostId: String = "",
    @SerialName("movie_fingerprint") val movieFingerprint: String = "",
    @SerialName("movie_full_hash") val movieFullHash: String? = null,
    @SerialName("movie_name") val movieName: String? = null,
    @SerialName("movie_duration_ms") val movieDurationMs: Long? = null,
    val status: String = "waiting",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class RoomInsert(
    val code: String,
    @SerialName("host_id") val hostId: String,
    @SerialName("movie_fingerprint") val movieFingerprint: String,
    @SerialName("movie_name") val movieName: String?,
    @SerialName("movie_duration_ms") val movieDurationMs: Long?,
    val status: String = "waiting"
)
