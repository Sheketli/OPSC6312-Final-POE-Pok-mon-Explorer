package com.pokemon.explorer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendshipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendship(friendship: FriendshipEntity)

    @Query("UPDATE friendships SET status = :status WHERE (userOne = :userOne AND userTwo = :userTwo) OR (userOne = :userTwo AND userTwo = :userOne)")
    suspend fun updateFriendshipStatus(userOne: String, userTwo: String, status: String)

    @Query("DELETE FROM friendships WHERE (userOne = :userOne AND userTwo = :userTwo) OR (userOne = :userTwo AND userTwo = :userOne)")
    suspend fun deleteFriendship(userOne: String, userTwo: String)

    @Query("SELECT * FROM friendships WHERE userOne = :username OR userTwo = :username")
    suspend fun getFriendshipsForUser(username: String): List<FriendshipEntity>

    @Query("SELECT * FROM friendships WHERE userOne = :username COLLATE NOCASE OR userTwo = :username COLLATE NOCASE")
    fun observeFriendshipsForUser(username: String): Flow<List<FriendshipEntity>>

    @Query("DELETE FROM friendships")
    suspend fun clearFriendships()

    @Query("SELECT * FROM friendships WHERE (userOne = :userA AND userTwo = :userB) OR (userOne = :userB AND userTwo = :userA) LIMIT 1")
    suspend fun getFriendshipBetween(userA: String, userB: String): FriendshipEntity?

    @Query("SELECT * FROM friendships")
    suspend fun getAllFriendships(): List<FriendshipEntity>
}
