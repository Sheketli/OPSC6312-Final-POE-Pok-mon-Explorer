package com.pokemon.explorer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_actions")
data class SyncAction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // e.g., "TOGGLE_FAVOURITE", "TOGGLE_CAUGHT"
    val payload: String, // e.g., pokemon ID or other stringified data
    val timestamp: Long = System.currentTimeMillis()
)
