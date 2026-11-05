package com.pokemon.explorer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        User::class,
        SyncAction::class,
        CachedPokemon::class,
        PokemonNote::class,
        PokemonIndex::class,
        FriendshipEntity::class,
        MessageEntity::class,
        TradeRequestEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun syncActionDao(): SyncActionDao
    abstract fun cachedPokemonDao(): CachedPokemonDao
    abstract fun pokemonNoteDao(): PokemonNoteDao
    abstract fun pokemonIndexDao(): PokemonIndexDao
    abstract fun friendshipDao(): FriendshipDao
    abstract fun messageDao(): MessageDao
    abstract fun tradeRequestDao(): TradeRequestDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pokemon_explorer_db"
                ).addMigrations(object : Migration(9, 10) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE trade_requests ADD COLUMN returnedPokemonId TEXT")
                        db.execSQL("ALTER TABLE trade_requests ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE trade_requests ADD COLUMN completedAt INTEGER NOT NULL DEFAULT 0")
                    }
                }).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
