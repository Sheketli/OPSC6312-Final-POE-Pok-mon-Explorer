package com.pokemon.explorer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserByUserId(userId: String): User?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE username LIKE '%' || :query || '%'")
    suspend fun searchUsers(query: String): List<User>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("UPDATE users SET passwordHash = :newHash WHERE username = :username")
    suspend fun updatePassword(username: String, newHash: String)

    @Query("UPDATE users SET username = :newUsername WHERE username = :oldUsername")
    suspend fun updateUsername(oldUsername: String, newUsername: String)
}
