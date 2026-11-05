package com.pokemon.explorer.data.db

import androidx.room.Entity

@Entity(tableName = "pokemon_notes", primaryKeys = ["username", "pokemonId"])
data class PokemonNote(
    val username: String,
    val userId: String = "",
    val pokemonId: Int,
    val nickname: String,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)
