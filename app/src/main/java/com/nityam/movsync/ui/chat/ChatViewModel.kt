package com.nityam.movsync.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nityam.movsync.MovSyncApp
import com.nityam.movsync.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MovSyncApp).container
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    private var activeRoomCode: String? = null

    fun start(roomCode: String) {
        if (activeRoomCode == roomCode) return
        activeRoomCode = roomCode
        viewModelScope.launch {
            try {
                _currentUserId.value = container.authRepository.ensureSignedIn()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "ensureSignedIn failed", e)
            }
        }
        viewModelScope.launch {
            container.firebaseSync.observeChatMessages(roomCode).collect {
                _messages.value = it
            }
        }
    }

    fun sendMessage(text: String) {
        val roomCode = activeRoomCode ?: return
        if (text.isBlank()) return
        
        viewModelScope.launch {
            try {
                val userId = container.authRepository.ensureSignedIn()
                _currentUserId.value = userId
                val displayName = container.authRepository.displayName.first()
                val message = ChatMessage(
                    senderId = userId,
                    senderName = displayName.ifBlank { "User" },
                    message = text.trim()
                )
                container.firebaseSync.sendMessage(roomCode, message)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "sendMessage failed", e)
            }
        }
    }
}
