package com.pokemon.explorer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE (senderUsername = :userA AND receiverUsername = :userB) OR (senderUsername = :userB AND receiverUsername = :userA) ORDER BY timestamp ASC")
    suspend fun getMessagesBetween(userA: String, userB: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE (senderUserId = :uid AND receiverUsername = :friend COLLATE NOCASE) OR (receiverUserId = :uid AND senderUsername = :friend COLLATE NOCASE) ORDER BY timestamp ASC, id ASC")
    fun observeMessages(uid: String, friend: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE senderUserId = :uid OR receiverUserId = :uid")
    suspend fun clearMessagesForUser(uid: String)

    @Query("SELECT * FROM messages WHERE senderUsername = :username OR receiverUsername = :username ORDER BY timestamp DESC")
    suspend fun getAllMessagesForUser(username: String): List<MessageEntity>
}
