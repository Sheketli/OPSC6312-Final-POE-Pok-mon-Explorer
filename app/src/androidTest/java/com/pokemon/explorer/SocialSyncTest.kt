package com.pokemon.explorer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokemon.explorer.data.db.AppDatabase
import com.pokemon.explorer.data.db.MessageEntity
import com.pokemon.explorer.data.db.TradeRequestEntity
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
class SocialSyncTest {
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).build()
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test
    fun openConversationReceivesMessagesAndFiltersOtherAccounts() = runBlocking {
        withTimeout(5_000) {
            val dao = database.messageDao()
            assertEquals(emptyList<MessageEntity>(), dao.observeMessages("misty", "Ash").first())
            val received = async(start = CoroutineStart.UNDISPATCHED) {
                dao.observeMessages("misty", "ash").first { it.isNotEmpty() }
            }
            val message = MessageEntity(1001, "Ash", "Misty", "ash", "misty", "Hello!", 10)
            dao.insertMessage(message)
            assertEquals(listOf(message), received.await())
            assertEquals(emptyList<MessageEntity>(), dao.observeMessages("brock", "Ash").first())
            dao.insertMessage(message.copy(id = 1002, senderUsername = "Misty", receiverUsername = "Ash",
                senderUserId = "misty", receiverUserId = "ash", messageText = "Hi!", timestamp = 20))
            assertEquals(listOf("Hello!", "Hi!"), dao.observeMessages("misty", "Ash").first().map { it.messageText })
        }
    }

    @Test
    fun openTradingListReceivesProposalsAndReturnOffers() = runBlocking {
        withTimeout(5_000) {
            val dao = database.tradeRequestDao()
            val incoming = async(start = CoroutineStart.UNDISPATCHED) {
                dao.observeTrades("misty").first { it.isNotEmpty() }
            }
            val proposal = TradeRequestEntity(2001, "Ash", "Misty", "ash", "misty", "25", null, "PENDING")
            dao.insertTradeRequest(proposal)
            assertEquals(listOf(proposal), incoming.await())
            val countered = async(start = CoroutineStart.UNDISPATCHED) {
                dao.observeTrades("ash").first { it.singleOrNull()?.tradeStatus == "COUNTERED" }
            }
            dao.insertTradeRequest(proposal.copy(tradeStatus = "COUNTERED", returnedPokemonId = "7"))
            assertEquals("7", countered.await().single().returnedPokemonId)
            assertEquals(emptyList<TradeRequestEntity>(), dao.observeTrades("brock").first())
        }
    }
}
