package com.pokemon.explorer

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokemon.explorer.data.CollectionPreferences
import com.pokemon.explorer.data.db.TradeRequestEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TradeCollectionTest {
    private lateinit var prefs: SharedPreferences
    private val exchange = TradeRequestEntity(
        1001, "Ash", "Misty", "ash-id", "misty-id", "25", null, "ACCEPTED", "7", 10, 20
    )

    @Before
    fun setup() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("trade-collection-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun cleanup() {
        prefs.edit().clear().commit()
    }

    private fun sync(uid: String, trades: List<TradeRequestEntity>, remote: Set<Int>) {
        CollectionPreferences.applyTrades(prefs, uid, trades, remote, emptySet(), emptySet())
    }

    @Test
    fun bothParticipantsExchangeAndRepeatedSnapshotsPreserveTheResult() {
        sync("ash-id", listOf(exchange), setOf(25, 1))
        sync("misty-id", listOf(exchange), setOf(7, 4))
        assertEquals(setOf(7, 1), CollectionPreferences.caught(prefs, "ash-id"))
        assertEquals(setOf(25, 4), CollectionPreferences.caught(prefs, "misty-id"))
        sync("ash-id", listOf(exchange), setOf(25, 1))
        assertEquals(setOf(7, 1), CollectionPreferences.caught(prefs, "ash-id"))
        assertEquals(setOf("1001"), CollectionPreferences.syncSnapshot(prefs, "ash-id").appliedTrades)
    }

    @Test
    fun staleCloudDataCannotRestoreAGivenPokemonAndItCanBeCaughtAgain() {
        sync("ash-id", listOf(exchange), setOf(25, 1))
        assertEquals(setOf(7, 1), CollectionPreferences.mergeRemote(prefs, "ash-id", setOf(25, 1)))
        CollectionPreferences.update(prefs, "ash-id", setOf(25, 7, 1))
        sync("ash-id", listOf(exchange), setOf(7, 1))
        assertEquals(setOf(25, 7, 1), CollectionPreferences.caught(prefs, "ash-id"))
    }

    @Test
    fun pendingAndCounteredOffersReserveEachParticipantsChosenPokemon() {
        val pending = exchange.copy(tradeStatus = "PENDING", returnedPokemonId = null)
        sync("ash-id", listOf(pending), setOf(25))
        sync("misty-id", listOf(pending), setOf(7))
        assertEquals(setOf(25), CollectionPreferences.reserved(prefs, "ash-id"))
        assertEquals(emptySet<Int>(), CollectionPreferences.reserved(prefs, "misty-id"))
        sync("misty-id", listOf(exchange.copy(tradeStatus = "COUNTERED")), setOf(7))
        assertEquals(setOf(7), CollectionPreferences.reserved(prefs, "misty-id"))
        sync("misty-id", listOf(exchange.copy(tradeStatus = "DECLINED")), setOf(7))
        assertEquals(emptySet<Int>(), CollectionPreferences.reserved(prefs, "misty-id"))
        assertEquals(setOf(7), CollectionPreferences.caught(prefs, "misty-id"))
    }

    @Test
    fun reinstallUsesCloudReceiptsAndDoesNotReplayAnOldExchange() {
        CollectionPreferences.applyTrades(
            prefs, "ash-id", listOf(exchange), setOf(25, 7), emptySet(), setOf("1001")
        )
        assertEquals(setOf(25, 7), CollectionPreferences.caught(prefs, "ash-id"))
    }

    @Test
    fun accountRestoreBeforeTradeSyncPreservesCloudReceipts() {
        CollectionPreferences.mergeRemote(prefs, "ash-id", setOf(25, 7), emptySet(), setOf("1001"))
        sync("ash-id", listOf(exchange), setOf(25, 7))
        assertEquals(true, 25 in CollectionPreferences.caught(prefs, "ash-id"))
        assertEquals(setOf("1001"), CollectionPreferences.syncSnapshot(prefs, "ash-id").appliedTrades)
    }

    @Test
    fun chainedExchangesApplyInCompletionOrderAndSurviveStaleSnapshots() {
        val next = exchange.copy(tradeId = 1002, offeredPokemonId = "7", returnedPokemonId = "4", completedAt = 30)
        sync("ash-id", listOf(next, exchange), setOf(25, 1))
        assertEquals(setOf(4, 1), CollectionPreferences.caught(prefs, "ash-id"))
        sync("ash-id", listOf(exchange, next), setOf(25, 7, 1))
        assertEquals(setOf(4, 1), CollectionPreferences.caught(prefs, "ash-id"))
    }
}
