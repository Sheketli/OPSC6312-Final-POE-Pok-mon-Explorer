package com.pokemon.explorer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "trade_requests")
data class TradeRequestEntity(
    @PrimaryKey(autoGenerate = true) val tradeId: Long = 0,
    val senderUsername: String,
    val receiverUsername: String,
    val senderUserId: String = "",
    val receiverUserId: String = "",
    val offeredPokemonId: String,
    val optionalDescription: String?,
    val tradeStatus: String, // PENDING, COUNTERED, ACCEPTED, DECLINED
    val returnedPokemonId: String? = null,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val completedAt: Long = 0,
)
