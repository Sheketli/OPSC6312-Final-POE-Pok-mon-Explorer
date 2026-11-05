package com.pokemon.explorer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderUsername: String,
    val receiverUsername: String,
    val senderUserId: String = "",
    val receiverUserId: String = "",
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis()
)
