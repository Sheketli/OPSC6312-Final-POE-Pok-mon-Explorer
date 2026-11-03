package com.pokemon.explorer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_pokemon")
data class CachedPokemon(
    @PrimaryKey
    val url: String,
    val jsonPayload: String,
    val timestamp: Long = System.currentTimeMillis()
)
