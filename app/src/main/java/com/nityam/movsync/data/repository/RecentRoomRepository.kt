package com.nityam.movsync.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecentRoom(
    val code: String,
    val movieName: String?,
    val isHost: Boolean,
    val timestamp: Long
)

class RecentRoomRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _recentRoom = MutableStateFlow(readRecentRoom())
    val recentRoom: StateFlow<RecentRoom?> = _recentRoom.asStateFlow()

    fun save(code: String, movieName: String?, isHost: Boolean) {
        val room = RecentRoom(
            code = code.uppercase(),
            movieName = movieName?.takeIf { it.isNotBlank() },
            isHost = isHost,
            timestamp = System.currentTimeMillis()
        )
        prefs.edit()
            .putString(KEY_CODE, room.code)
            .putString(KEY_MOVIE_NAME, room.movieName)
            .putBoolean(KEY_IS_HOST, room.isHost)
            .putLong(KEY_TIMESTAMP, room.timestamp)
            .apply()
        _recentRoom.value = room
    }

    fun get(): RecentRoom? = _recentRoom.value

    fun clear() {
        prefs.edit()
            .remove(KEY_CODE)
            .remove(KEY_MOVIE_NAME)
            .remove(KEY_IS_HOST)
            .remove(KEY_TIMESTAMP)
            .apply()
        _recentRoom.value = null
    }

    private fun readRecentRoom(): RecentRoom? {
        val code = prefs.getString(KEY_CODE, null)?.takeIf { it.isNotBlank() } ?: return null
        return RecentRoom(
            code = code.uppercase(),
            movieName = prefs.getString(KEY_MOVIE_NAME, null),
            isHost = prefs.getBoolean(KEY_IS_HOST, false),
            timestamp = prefs.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
        )
    }

    private companion object {
        const val PREFS_NAME = "movsync_recent_room"
        const val KEY_CODE = "code"
        const val KEY_MOVIE_NAME = "movie_name"
        const val KEY_IS_HOST = "is_host"
        const val KEY_TIMESTAMP = "timestamp"
    }
}
