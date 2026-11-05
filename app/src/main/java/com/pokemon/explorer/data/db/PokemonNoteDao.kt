package com.pokemon.explorer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PokemonNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: PokemonNote)

    @Query("SELECT * FROM pokemon_notes WHERE username = :username AND pokemonId = :pokemonId")
    suspend fun getNote(username: String, pokemonId: Int): PokemonNote?

    @Query("SELECT * FROM pokemon_notes WHERE username = :username")
    suspend fun getAllNotesForUser(username: String): List<PokemonNote>
    
    @Query("DELETE FROM pokemon_notes WHERE username = :username AND pokemonId = :pokemonId")
    suspend fun deleteNote(username: String, pokemonId: Int)

    @Query("DELETE FROM pokemon_notes WHERE username = :username OR userId = :userId")
    suspend fun deleteAllNotesForUser(username: String, userId: String)
}
