package com.pokemon.explorer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTradeRequest(tradeRequest: TradeRequestEntity): Long

    @Query("UPDATE trade_requests SET tradeStatus = :status WHERE tradeId = :tradeId")
    suspend fun updateTradeStatus(tradeId: Long, status: String)

    @Query("SELECT * FROM trade_requests WHERE senderUsername = :username OR receiverUsername = :username ORDER BY tradeId DESC")
    suspend fun getTradeRequestsForUser(username: String): List<TradeRequestEntity>

    @Query("SELECT * FROM trade_requests WHERE tradeId = :tradeId LIMIT 1")
    suspend fun getTradeRequestById(tradeId: Long): TradeRequestEntity?

    @Query("SELECT * FROM trade_requests WHERE senderUserId = :uid OR receiverUserId = :uid ORDER BY createdAt DESC, tradeId DESC")
    fun observeTrades(uid: String): Flow<List<TradeRequestEntity>>

    @Query("DELETE FROM trade_requests WHERE senderUserId = :uid OR receiverUserId = :uid")
    suspend fun clearTradesForUser(uid: String)
}
