package com.pokemon.explorer.data.repository

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.pokemon.explorer.data.db.AppDatabase
import com.pokemon.explorer.data.db.FriendshipEntity
import com.pokemon.explorer.data.db.MessageEntity
import com.pokemon.explorer.data.db.TradeRequestEntity
import com.pokemon.explorer.data.db.User
import com.pokemon.explorer.utils.EmailService
import com.pokemon.explorer.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SocialRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firebaseDb = FirebaseDatabase.getInstance().reference
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        listenToFirebaseEvents()
    }

    private fun listenToFirebaseEvents() {
        // Sync Users
        firebaseDb.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    for (child in snapshot.children) {
                        val username = child.child("username").getValue(String::class.java)
                        val email = child.child("email").getValue(String::class.java) ?: ""
                        val userId = child.child("userId").getValue(String::class.java) ?: child.key ?: ""
                        if (username != null && username.isNotBlank()) {
                            db.userDao().insertUser(User(username = username, userId = userId, email = email, passwordHash = ""))
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Sync Friendships
        firebaseDb.child("friendships").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    val savedUser = prefs.getString("logged_in_user", "") ?: ""
                    val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: ""
                    val activeUser = if (savedUser.isNotBlank()) savedUser else authUser

                    for (child in snapshot.children) {
                        val u1 = child.child("userOne").getValue(String::class.java)
                        val u2 = child.child("userTwo").getValue(String::class.java)
                        val u1Id = child.child("userOneId").getValue(String::class.java) ?: ""
                        val u2Id = child.child("userTwoId").getValue(String::class.java) ?: ""
                        val status = child.child("status").getValue(String::class.java)
                        if (u1 != null && u2 != null && status != null) {
                            val existing = db.friendshipDao().getFriendshipBetween(u1, u2)
                            db.friendshipDao().insertFriendship(FriendshipEntity(u1, u2, u1Id, u2Id, status))

                            // Notify receiver when a new pending request arrives
                            if (activeUser.isNotBlank() && u2.equals(activeUser, ignoreCase = true) && !u1.equals(activeUser, ignoreCase = true)) {
                                if (status == "PENDING_SENDER_ONE" && (existing == null || existing.status != "PENDING_SENDER_ONE")) {
                                    NotificationHelper.sendSocialNotification(
                                        context,
                                        title = "New Friend Request!",
                                        body = "$u1 sent you a friend request via their Explorer Account.",
                                        notificationId = 4001,
                                        targetScreen = "friends"
                                    )
                                }
                            }

                            // Notify sender when the request is accepted
                            if (activeUser.isNotBlank() && u1.equals(activeUser, ignoreCase = true) && !u2.equals(activeUser, ignoreCase = true)) {
                                if (status == "ACCEPTED" && existing != null && existing.status == "PENDING_SENDER_ONE") {
                                    NotificationHelper.sendSocialNotification(
                                        context,
                                        title = "Friend Request Accepted!",
                                        body = "$u2 accepted your friend request. You can now trade and chat!",
                                        notificationId = 4002,
                                        targetScreen = "friends"
                                    )
                                }
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Sync Messages
        firebaseDb.child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    val savedUser = prefs.getString("logged_in_user", "") ?: ""
                    val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: ""
                    val activeUser = if (savedUser.isNotBlank()) savedUser else authUser

                    for (child in snapshot.children) {
                        val id = child.child("id").getValue(Long::class.java) ?: 0L
                        val sender = child.child("senderUsername").getValue(String::class.java)
                        val receiver = child.child("receiverUsername").getValue(String::class.java)
                        val senderId = child.child("senderUserId").getValue(String::class.java) ?: ""
                        val receiverId = child.child("receiverUserId").getValue(String::class.java) ?: ""
                        val text = child.child("messageText").getValue(String::class.java)
                        val time = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                        if (sender != null && receiver != null && text != null) {
                            val existingMessages = db.messageDao().getMessagesBetween(sender, receiver)
                            val isNew = existingMessages.none { it.id == id || (it.timestamp == time && it.messageText == text) }

                            db.messageDao().insertMessage(
                                MessageEntity(
                                    id = if (id > 0) id else 0,
                                    senderUsername = sender,
                                    receiverUsername = receiver,
                                    senderUserId = senderId,
                                    receiverUserId = receiverId,
                                    messageText = text,
                                    timestamp = time
                                )
                            )

                            if (isNew && activeUser.isNotBlank() && receiver.equals(activeUser, ignoreCase = true) && !sender.equals(activeUser, ignoreCase = true)) {
                                NotificationHelper.sendSocialNotification(
                                    context,
                                    title = "Message from $sender",
                                    body = text,
                                    notificationId = sender.hashCode(),
                                    targetScreen = "chat",
                                    extraData = sender
                                )
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Sync Trade Requests
        firebaseDb.child("trade_requests").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    val savedUser = prefs.getString("logged_in_user", "") ?: ""
                    val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: ""
                    val activeUser = if (savedUser.isNotBlank()) savedUser else authUser

                    for (child in snapshot.children) {
                        val tradeId = child.child("tradeId").getValue(Long::class.java) ?: 0L
                        val sender = child.child("senderUsername").getValue(String::class.java)
                        val receiver = child.child("receiverUsername").getValue(String::class.java)
                        val senderId = child.child("senderUserId").getValue(String::class.java) ?: ""
                        val receiverId = child.child("receiverUserId").getValue(String::class.java) ?: ""
                        val pokemonId = child.child("offeredPokemonId").getValue(String::class.java)
                        val desc = child.child("optionalDescription").getValue(String::class.java)
                        val status = child.child("tradeStatus").getValue(String::class.java)
                        if (sender != null && receiver != null && pokemonId != null && status != null) {
                            val existing = db.tradeRequestDao().getTradeRequestById(tradeId)

                            db.tradeRequestDao().insertTradeRequest(
                                TradeRequestEntity(
                                    tradeId = if (tradeId > 0) tradeId else 0,
                                    senderUsername = sender,
                                    receiverUsername = receiver,
                                    senderUserId = senderId,
                                    receiverUserId = receiverId,
                                    offeredPokemonId = pokemonId,
                                    optionalDescription = desc,
                                    tradeStatus = status
                                )
                            )

                            if (activeUser.isNotBlank() && receiver.equals(activeUser, ignoreCase = true) && !sender.equals(activeUser, ignoreCase = true)) {
                                if (status == "PENDING" && (existing == null || existing.tradeStatus != "PENDING")) {
                                    NotificationHelper.sendSocialNotification(
                                        context,
                                        title = "Trade Proposal from $sender!",
                                        body = "$sender offered Pokémon #$pokemonId for trade.",
                                        notificationId = 5001,
                                        targetScreen = "trade"
                                    )
                                }
                            }

                            if (activeUser.isNotBlank() && sender.equals(activeUser, ignoreCase = true) && !receiver.equals(activeUser, ignoreCase = true)) {
                                if (status == "ACCEPTED" && existing != null && existing.tradeStatus == "PENDING") {
                                    NotificationHelper.sendSocialNotification(
                                        context,
                                        title = "Trade Accepted!",
                                        body = "$receiver accepted your trade request for Pokémon #$pokemonId!",
                                        notificationId = 5002,
                                        targetScreen = "trade"
                                    )
                                }
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    // ---------------- Search & Users ----------------
    suspend fun searchUsers(query: String): List<User> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val resultsMap = mutableMapOf<String, User>()

        // 1. Search local Room SQLite DB
        val localResults = db.userDao().searchUsers(query)
        for (u in localResults) {
            resultsMap[u.username.lowercase()] = u
        }

        // 2. Query Firebase Realtime Database for all active registered user accounts
        try {
            val snapshot = firebaseDb.child("users").get().await()
            for (child in snapshot.children) {
                val username = child.child("username").getValue(String::class.java)
                val email = child.child("email").getValue(String::class.java) ?: ""
                val userId = child.child("userId").getValue(String::class.java) ?: child.key ?: ""
                if (username != null && username.contains(query, ignoreCase = true)) {
                    val u = User(username = username, userId = userId, email = email, passwordHash = "")
                    resultsMap[username.lowercase()] = u
                    db.userDao().insertUser(u)
                }
            }
        } catch (_: Exception) {
            // Offline fallback
        }

        resultsMap.values.toList()
    }

    suspend fun getUserEmail(username: String): String = withContext(Dispatchers.IO) {
        val localUser = db.userDao().getUserByUsername(username)
        if (localUser != null && localUser.email.isNotBlank()) {
            return@withContext localUser.email
        }
        var foundEmail = ""
        try {
            val snapshot = firebaseDb.child("users").get().await()
            for (child in snapshot.children) {
                val u = child.child("username").getValue(String::class.java)
                val emailStr = child.child("email").getValue(String::class.java)
                if (u == username && emailStr != null) {
                    foundEmail = emailStr
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        foundEmail
    }

    // ---------------- Friendships ----------------
    suspend fun sendFriendRequest(senderUsername: String, targetUsername: String): Boolean = withContext(Dispatchers.IO) {
        if (senderUsername.equals(targetUsername, ignoreCase = true)) return@withContext false

        val existing = db.friendshipDao().getFriendshipBetween(senderUsername, targetUsername)
        if (existing != null && (existing.status == "ACCEPTED" || existing.status == "PENDING_SENDER_ONE")) {
            return@withContext false
        }

        val senderUser = db.userDao().getUserByUsername(senderUsername)
        val targetUser = db.userDao().getUserByUsername(targetUsername)
        val sId = senderUser?.userId ?: ""
        val tId = targetUser?.userId ?: ""

        val entity = FriendshipEntity(
            userOne = senderUsername,
            userTwo = targetUsername,
            userOneId = sId,
            userTwoId = tId,
            status = "PENDING_SENDER_ONE"
        )
        db.friendshipDao().insertFriendship(entity)

        val pairKey = "${senderUsername}_${targetUsername}"
        val firebaseMap = mapOf(
            "userOne" to senderUsername,
            "userTwo" to targetUsername,
            "userOneId" to sId,
            "userTwoId" to tId,
            "status" to "PENDING_SENDER_ONE"
        )
        firebaseDb.child("friendships").child(pairKey).setValue(firebaseMap)

        // External Email Notification for target user
        val recipientEmail = getUserEmail(targetUsername)
        if (recipientEmail.isNotBlank()) {
            EmailService.sendFriendRequestEmail(context, senderUsername, recipientEmail, targetUsername)
        }

        true
    }

    suspend fun acceptFriendRequest(currentUsername: String, requesterUsername: String) = withContext(Dispatchers.IO) {
        db.friendshipDao().updateFriendshipStatus(requesterUsername, currentUsername, "ACCEPTED")
        val pairKey1 = "${requesterUsername}_${currentUsername}"
        val pairKey2 = "${currentUsername}_${requesterUsername}"

        firebaseDb.child("friendships").child(pairKey1).child("status").setValue("ACCEPTED")
        firebaseDb.child("friendships").child(pairKey2).child("status").setValue("ACCEPTED")
    }

    suspend fun declineFriendRequest(currentUsername: String, requesterUsername: String) = withContext(Dispatchers.IO) {
        db.friendshipDao().deleteFriendship(requesterUsername, currentUsername)
        val pairKey1 = "${requesterUsername}_${currentUsername}"
        val pairKey2 = "${currentUsername}_${requesterUsername}"
        firebaseDb.child("friendships").child(pairKey1).removeValue()
        firebaseDb.child("friendships").child(pairKey2).removeValue()
    }

    suspend fun cancelFriendRequest(currentUsername: String, targetUsername: String) = withContext(Dispatchers.IO) {
        db.friendshipDao().deleteFriendship(currentUsername, targetUsername)
        val pairKey1 = "${currentUsername}_${targetUsername}"
        val pairKey2 = "${targetUsername}_${currentUsername}"
        firebaseDb.child("friendships").child(pairKey1).removeValue()
        firebaseDb.child("friendships").child(pairKey2).removeValue()
    }

    suspend fun removeFriend(currentUsername: String, friendUsername: String) = withContext(Dispatchers.IO) {
        db.friendshipDao().deleteFriendship(currentUsername, friendUsername)
        val pairKey1 = "${currentUsername}_${friendUsername}"
        val pairKey2 = "${friendUsername}_${currentUsername}"
        firebaseDb.child("friendships").child(pairKey1).removeValue()
        firebaseDb.child("friendships").child(pairKey2).removeValue()
    }

    suspend fun getFriendships(username: String): List<FriendshipEntity> = withContext(Dispatchers.IO) {
        db.friendshipDao().getFriendshipsForUser(username)
    }

    suspend fun isFriend(userA: String, userB: String): Boolean = withContext(Dispatchers.IO) {
        val friendship = db.friendshipDao().getFriendshipBetween(userA, userB)
        friendship?.status == "ACCEPTED"
    }

    // ---------------- Messages ----------------
    suspend fun sendMessage(senderUsername: String, receiverUsername: String, messageText: String): Boolean = withContext(Dispatchers.IO) {
        if (messageText.isBlank()) return@withContext false

        val senderUser = db.userDao().getUserByUsername(senderUsername)
        val receiverUser = db.userDao().getUserByUsername(receiverUsername)
        val sId = senderUser?.userId ?: ""
        val rId = receiverUser?.userId ?: ""

        val msg = MessageEntity(
            senderUsername = senderUsername,
            receiverUsername = receiverUsername,
            senderUserId = sId,
            receiverUserId = rId,
            messageText = messageText,
            timestamp = System.currentTimeMillis()
        )
        val insertedId = db.messageDao().insertMessage(msg)
        val finalId = if (insertedId > 0) insertedId else System.currentTimeMillis()

        val msgMap = mapOf(
            "id" to finalId,
            "senderUsername" to senderUsername,
            "receiverUsername" to receiverUsername,
            "senderUserId" to sId,
            "receiverUserId" to rId,
            "messageText" to messageText,
            "timestamp" to msg.timestamp
        )
        firebaseDb.child("messages").child(finalId.toString()).setValue(msgMap)

        true
    }

    suspend fun getMessagesBetween(userA: String, userB: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        db.messageDao().getMessagesBetween(userA, userB)
    }

    // ---------------- Trades ----------------
    suspend fun sendTradeRequest(
        senderUsername: String,
        receiverUsername: String,
        offeredPokemonId: String,
        optionalDescription: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val senderUser = db.userDao().getUserByUsername(senderUsername)
        val receiverUser = db.userDao().getUserByUsername(receiverUsername)
        val sId = senderUser?.userId ?: ""
        val rId = receiverUser?.userId ?: ""

        val trade = TradeRequestEntity(
            senderUsername = senderUsername,
            receiverUsername = receiverUsername,
            senderUserId = sId,
            receiverUserId = rId,
            offeredPokemonId = offeredPokemonId,
            optionalDescription = optionalDescription,
            tradeStatus = "PENDING"
        )
        val tradeId = db.tradeRequestDao().insertTradeRequest(trade)
        val finalId = if (tradeId > 0) tradeId else System.currentTimeMillis()

        val tradeMap = mapOf(
            "tradeId" to finalId,
            "senderUsername" to senderUsername,
            "receiverUsername" to receiverUsername,
            "senderUserId" to sId,
            "receiverUserId" to rId,
            "offeredPokemonId" to offeredPokemonId,
            "optionalDescription" to (optionalDescription ?: ""),
            "tradeStatus" to "PENDING"
        )
        firebaseDb.child("trade_requests").child(finalId.toString()).setValue(tradeMap)

        // External Email Notification
        val recipientEmail = getUserEmail(receiverUsername)
        if (recipientEmail.isNotBlank()) {
            EmailService.sendTradeNotificationEmail(context, senderUsername, recipientEmail, receiverUsername, "Pokémon #$offeredPokemonId")
        }

        true
    }

    suspend fun acceptTradeRequest(tradeId: Long, recipientUsername: String, senderUsername: String, pokemonId: String): Boolean = withContext(Dispatchers.IO) {
        db.tradeRequestDao().updateTradeStatus(tradeId, "ACCEPTED")
        firebaseDb.child("trade_requests").child(tradeId.toString()).child("tradeStatus").setValue("ACCEPTED")
        true
    }

    suspend fun declineTradeRequest(tradeId: Long): Boolean = withContext(Dispatchers.IO) {
        db.tradeRequestDao().updateTradeStatus(tradeId, "DECLINED")
        firebaseDb.child("trade_requests").child(tradeId.toString()).child("tradeStatus").setValue("DECLINED")
        true
    }

    suspend fun getTradeRequestsForUser(username: String): List<TradeRequestEntity> = withContext(Dispatchers.IO) {
        db.tradeRequestDao().getTradeRequestsForUser(username)
    }
}
