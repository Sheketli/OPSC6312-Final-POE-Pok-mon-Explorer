package com.pokemon.explorer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [User::class, SyncAction::class, CachedPokemon::class, PokemonNote::class, PokemonIndex::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun syncActionDao(): SyncActionDao
    abstract fun cachedPokemonDao(): CachedPokemonDao
    abstract fun pokemonNoteDao(): PokemonNoteDao
    abstract fun pokemonIndexDao(): PokemonIndexDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pokemon_explorer_db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
