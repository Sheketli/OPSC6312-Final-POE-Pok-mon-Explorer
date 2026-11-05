package com.pokemon.explorer.data

import android.content.SharedPreferences
import com.pokemon.explorer.data.db.TradeRequestEntity

data class SyncSnapshot(val appliedTrades: Set<String>, val caught: Set<Int> = emptySet(), val removed: Set<Int> = emptySet())

object CollectionPreferences {
    fun caught(prefs: SharedPreferences, uid: String): Set<Int> {
        val set = prefs.getStringSet("caught_$uid", null) ?: return emptySet()
        return set.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun reserved(prefs: SharedPreferences, uid: String): Set<Int> {
        val set = prefs.getStringSet("trade_reserved_$uid", null) ?: return emptySet()
        return set.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun update(prefs: SharedPreferences, uid: String, caught: Set<Int>) {
        val editor = prefs.edit()
        editor.putStringSet("caught_$uid", caught.map { it.toString() }.toSet())
        
        val caughtRemoved = prefs.getStringSet("caught_removed_$uid", null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        val updatedRemoved = caughtRemoved - caught
        editor.putStringSet("caught_removed_$uid", updatedRemoved.map { it.toString() }.toSet())
        
        editor.apply()
    }

    fun syncSnapshot(prefs: SharedPreferences, uid: String): SyncSnapshot {
        val applied = prefs.getStringSet("applied_trades_$uid", null) ?: emptySet()
        val c = caught(prefs, uid)
        val r = prefs.getStringSet("caught_removed_$uid", null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        return SyncSnapshot(applied, c, r)
    }

    fun mergeRemote(
        prefs: SharedPreferences,
        uid: String,
        remoteCaught: Set<Int>,
        remoteRemoved: Set<Int> = emptySet(),
        remoteAppliedTrades: Set<String> = emptySet()
    ): Set<Int> {
        val editor = prefs.edit()

        val currentApplied = prefs.getStringSet("applied_trades_$uid", null) ?: emptySet()
        val newApplied = currentApplied + remoteAppliedTrades
        editor.putStringSet("applied_trades_$uid", newApplied)

        val currentCaught = caught(prefs, uid)

        // Wait, "staleCloudDataCannotRestoreAGivenPokemonAndItCanBeCaughtAgain"
        // If we caught something previously and then removed it,
        // it's in `caughtRemoved`. But what if we caught it AGAIN locally?
        // If it's in `currentCaught`, it means we definitely have it now.
        // So we should remove from `updatedRemoved` any pokemon that we currently have locally caught.
        
        val caughtRemoved = prefs.getStringSet("caught_removed_$uid", null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        val updatedRemoved = (caughtRemoved + remoteRemoved) - currentCaught
        editor.putStringSet("caught_removed_$uid", updatedRemoved.map { it.toString() }.toSet())

        val mergedCaught = (currentCaught + remoteCaught) - updatedRemoved

        editor.putStringSet("caught_$uid", mergedCaught.map { it.toString() }.toSet())
        editor.apply()

        return mergedCaught
    }

    fun applyTrades(
        prefs: SharedPreferences,
        uid: String,
        trades: List<TradeRequestEntity>,
        remoteCaught: Set<Int>,
        remoteRemoved: Set<Int> = emptySet(),
        remoteAppliedTrades: Set<String> = emptySet()
    ) {
        val editor = prefs.edit()

        val currentApplied = prefs.getStringSet("applied_trades_$uid", null) ?: emptySet()
        val newApplied = (currentApplied + remoteAppliedTrades).toMutableSet()

        val currentCaught = caught(prefs, uid).toMutableSet()
        currentCaught.addAll(remoteCaught)

        val removed = prefs.getStringSet("caught_removed_$uid", null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        val newRemoved = (removed + remoteRemoved).toMutableSet()

        currentCaught.removeAll(newRemoved)

        val reserved = mutableSetOf<Int>()

        val sortedTrades = trades.sortedBy { it.completedAt }

        for (trade in sortedTrades) {
            val tradeIdStr = trade.tradeId.toString()

            val isSender = trade.senderUserId == uid
            val isReceiver = trade.receiverUserId == uid

            val offered = trade.offeredPokemonId.toIntOrNull()
            val returned = trade.returnedPokemonId?.toIntOrNull()

            if (trade.tradeStatus == "PENDING") {
                if (isSender && offered != null) reserved.add(offered)
            } else if (trade.tradeStatus == "COUNTERED") {
                if (isSender && offered != null) reserved.add(offered)
                if (isReceiver && returned != null) reserved.add(returned)
            } else if (trade.tradeStatus == "ACCEPTED") {
                if (!newApplied.contains(tradeIdStr)) {
                    if (isSender) {
                        if (offered != null) {
                            if (currentCaught.remove(offered)) {
                                newRemoved.add(offered)
                            }
                        }
                        if (returned != null) {
                            currentCaught.add(returned)
                            newRemoved.remove(returned)
                        }
                    } else if (isReceiver) {
                        if (returned != null) {
                            if (currentCaught.remove(returned)) {
                                newRemoved.add(returned)
                            }
                        }
                        if (offered != null) {
                            currentCaught.add(offered)
                            newRemoved.remove(offered)
                        }
                    }
                    newApplied.add(tradeIdStr)
                }
            }
        }

        editor.putStringSet("caught_$uid", currentCaught.map { it.toString() }.toSet())
        editor.putStringSet("caught_removed_$uid", newRemoved.map { it.toString() }.toSet())
        editor.putStringSet("applied_trades_$uid", newApplied)
        editor.putStringSet("trade_reserved_$uid", reserved.map { it.toString() }.toSet())
        editor.apply()
    }
}
