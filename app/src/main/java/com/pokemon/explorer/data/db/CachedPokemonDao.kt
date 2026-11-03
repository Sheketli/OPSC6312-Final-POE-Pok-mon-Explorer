package com.pokemon.explorer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedPokemonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(cachedPokemon: CachedPokemon)

    @Query("SELECT * FROM cached_pokemon WHERE url = :url")
    fun get(url: String): CachedPokemon?

    @Query("DELETE FROM cached_pokemon")
    fun clearAll()
}
