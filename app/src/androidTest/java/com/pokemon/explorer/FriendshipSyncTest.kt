package com.pokemon.explorer

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokemon.explorer.data.db.AppDatabase
import com.pokemon.explorer.data.db.FriendshipEntity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendshipSyncTest {
    private lateinit var database: AppDatabase
    private val request = FriendshipEntity(
        "Ash", "Misty", "ash-id", "misty-id", "PENDING_SENDER_ONE"
    )

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun recipientObservesRequestArrivingAfterOpeningEmptyList() = runBlocking {
        withTimeout(5_000) {
            val dao = database.friendshipDao()
            assertEquals(emptyList<FriendshipEntity>(), dao.observeFriendshipsForUser("Misty").first())
            val incoming = async(start = CoroutineStart.UNDISPATCHED) {
                dao.observeFriendshipsForUser("Misty").first { it.isNotEmpty() }
            }
            dao.insertFriendship(request)
            assertEquals(listOf(request), incoming.await())
            assertEquals(emptyList<FriendshipEntity>(), dao.observeFriendshipsForUser("Brock").first())
        }
    }

    @Test
    fun recipientObservesAcceptanceAndRemoteDeletionDespiteUsernameCase() = runBlocking {
        withTimeout(5_000) {
            val dao = database.friendshipDao()
            dao.insertFriendship(request)
            assertEquals(listOf(request), dao.observeFriendshipsForUser("misty").first())
            val accepted = async(start = CoroutineStart.UNDISPATCHED) {
                dao.observeFriendshipsForUser("misty").first { it.singleOrNull()?.status == "ACCEPTED" }
            }
            dao.updateFriendshipStatus("Ash", "Misty", "ACCEPTED")
            assertEquals("ACCEPTED", accepted.await().single().status)

            val removed = async(start = CoroutineStart.UNDISPATCHED) {
                dao.observeFriendshipsForUser("misty").first { it.isEmpty() }
            }
            database.withTransaction { dao.clearFriendships() }
            assertEquals(emptyList<FriendshipEntity>(), removed.await())
        }
    }
}
