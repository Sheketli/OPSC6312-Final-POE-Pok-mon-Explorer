package com.pokemon.explorer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Dao
interface SyncActionDao {
    @Insert
    suspend fun insertAction(action: SyncAction)

    @Query("SELECT * FROM sync_actions ORDER BY timestamp ASC")
    suspend fun getAllActions(): List<SyncAction>

    @Delete
    suspend fun deleteAction(action: SyncAction)
}
