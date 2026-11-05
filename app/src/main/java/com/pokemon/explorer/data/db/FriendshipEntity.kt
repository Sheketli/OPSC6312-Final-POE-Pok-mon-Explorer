package com.pokemon.explorer.data.db

import androidx.room.Entity

@Entity(tableName = "friendships", primaryKeys = ["userOne", "userTwo"])
data class FriendshipEntity(
    val userOne: String,
    val userTwo: String,
    val userOneId: String = "",
    val userTwoId: String = "",
    val status: String // "PENDING_SENDER_ONE", "ACCEPTED", "DECLINED"
)
