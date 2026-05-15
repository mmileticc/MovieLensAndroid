package dev.milinko.geminiwrapper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    val imageUri: String? = null,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
