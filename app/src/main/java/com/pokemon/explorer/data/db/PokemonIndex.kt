package com.pokemon.explorer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pokemon_index")
data class PokemonIndex(
    @PrimaryKey
    val id: Int,
    val name: String,
    val typesCsv: String
)
