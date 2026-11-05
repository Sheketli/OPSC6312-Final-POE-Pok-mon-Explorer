package com.pokemon.explorer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE (senderUsername = :userA AND receiverUsername = :userB) OR (senderUsername = :userB AND receiverUsername = :userA) ORDER BY timestamp ASC")
    suspend fun getMessagesBetween(userA: String, userB: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE senderUsername = :username OR receiverUsername = :username ORDER BY timestamp DESC")
    suspend fun getAllMessagesForUser(username: String): List<MessageEntity>
}
