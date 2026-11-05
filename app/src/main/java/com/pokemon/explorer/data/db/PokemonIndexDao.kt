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

    @Query("SELECT * FROM pokemon_index WHERE name LIKE :query || '%' OR id = :id LIMIT 50")
    suspend fun search(query: String, id: Int?): List<PokemonIndex>

    @Query("SELECT * FROM pokemon_index WHERE (:query = '' OR LOWER(name) LIKE :query OR CAST(id AS TEXT) LIKE :query) AND (:type = '' OR LOWER(typesCsv) LIKE :type) LIMIT 200")
    suspend fun searchWithFilter(query: String, type: String): List<PokemonIndex>

    @Query("SELECT COUNT(*) FROM pokemon_index WHERE typesCsv = '' OR typesCsv IS NULL")
    suspend fun getEmptyTypesCount(): Int

    @Query("SELECT * FROM pokemon_index WHERE typesCsv = '' OR typesCsv IS NULL")
    suspend fun getEmptyTypes(): List<PokemonIndex>
}
