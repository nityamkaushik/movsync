package com.nityam.movsync.data.model

import java.util.UUID

data class ChatMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestamp: Long = 0L
)
