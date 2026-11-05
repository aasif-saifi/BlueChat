package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageType {
    TEXT, PHOTO, VIDEO, FILE
}

enum class MessageStatus {
    SENDING, SENT, DELIVERED, FAILED
}

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String,
    val senderAddress: String,
    val senderName: String,
    val receiverAddress: String,
    val isFromMe: Boolean,
    val textContent: String,
    val mediaUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val timestamp: Long = System.currentTimeMillis()
)
