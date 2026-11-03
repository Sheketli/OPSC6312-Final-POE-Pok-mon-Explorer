package com.pokemon.explorer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PokemonIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pokemon: List<PokemonIndex>)

    @Query("SELECT COUNT(*) FROM pokemon_index")
    suspend fun getCount(): Int

    @Query("SELECT * FROM pokemon_index WHERE name LIKE '%' || :query || '%' OR id = :id LIMIT 50")
    suspend fun search(query: String, id: Int?): List<PokemonIndex>

    @Query("SELECT * FROM pokemon_index WHERE typesCsv LIKE '%' || :type || '%'")
    suspend fun getByType(type: String): List<PokemonIndex>
}
