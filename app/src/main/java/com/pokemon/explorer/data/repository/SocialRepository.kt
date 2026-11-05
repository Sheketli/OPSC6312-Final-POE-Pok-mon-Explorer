package com.pokemon.explorer.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.Query
import com.pokemon.explorer.data.db.AppDatabase
import com.pokemon.explorer.R
import com.pokemon.explorer.data.db.FriendshipEntity
import com.pokemon.explorer.data.db.MessageEntity
import com.pokemon.explorer.data.db.TradeRequestEntity
import com.pokemon.explorer.data.db.User
import com.pokemon.explorer.data.db.SyncAction
import com.pokemon.explorer.data.CollectionPreferences
import com.pokemon.explorer.data.sync.SyncWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import com.google.firebase.database.ServerValue
import com.pokemon.explorer.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class SocialRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firebaseDb = FirebaseDatabase.getInstance().reference
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableListOf<Pair<Query, ValueEventListener>>()
    private val _friendshipSyncError = MutableStateFlow<String?>(null)
    val friendshipSyncError = _friendshipSyncError.asStateFlow()
    private val _messageSyncError = MutableStateFlow<String?>(null)
    val messageSyncError = _messageSyncError.asStateFlow()
    private val _tradeSyncError = MutableStateFlow<String?>(null)
    val tradeSyncError = _tradeSyncError.asStateFlow()

    private fun listen(path: String, listener: ValueEventListener) {
        listen(firebaseDb.child(path), listener)
    }

    private fun listen(reference: Query, listener: ValueEventListener) {
        listeners.add(reference to listener)
        reference.addValueEventListener(listener)
    }

    fun close() {
        listeners.forEach { (reference, listener) -> reference.removeEventListener(listener) }
        scope.cancel()
    }

    suspend fun refresh() = withTimeout(15_000) {
        require(FirebaseAuth.getInstance().currentUser != null) { "Sign in online to refresh." }
        coroutineScope {
            listeners.map { (query, listener) ->
                async {
                    // Reattach listeners that Firebase may have cancelled after a permissions error.
                    query.removeEventListener(listener)
                    query.addValueEventListener(listener)
                    query.get().await()
                }
            }.awaitAll()
        }
    }

    init {
        listenToFirebaseEvents()
    }

    private fun listenToFirebaseEvents() {
        // Only public trainer names and IDs are shared across accounts.
        listen("trainer_profiles", object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    for (child in snapshot.children) {
                        val username = child.child("username").getValue(String::class.java)
                        val userId = child.child("userId").getValue(String::class.java) ?: child.key ?: ""
                        if (username != null && username.isNotBlank()) {
                            val existing = db.userDao().getUserByUsername(username)
                            if (existing == null || existing.userId == userId) {
                                db.userDao().insertUser(
                                    existing?.copy(userId = userId) ?: User(username, userId)
                                )
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Sync Friendships
        // Process snapshots in order, including deletions and rejected-write rollbacks.
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val friendshipSnapshots = Channel<Pair<String, DataSnapshot>>(Channel.UNLIMITED)
        scope.launch {
            val snapshots = mutableMapOf<String, DataSnapshot>()
            for ((field, snapshot) in friendshipSnapshots) {
                snapshots[field] = snapshot
                if (snapshots.size < 2) continue
                try {
                    db.withTransaction {
                        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                        val savedUser = prefs.getString("logged_in_user", "") ?: ""
                        val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: ""
                        val activeUser = if (savedUser.isNotBlank()) savedUser else authUser
                        val previous = db.friendshipDao().getAllFriendships()
                        db.friendshipDao().clearFriendships()

                        val records = snapshots.values.flatMap { it.children }.distinctBy { it.key }
                        for (child in records) {
                            val u1 = child.child("userOne").getValue(String::class.java)
                            val u2 = child.child("userTwo").getValue(String::class.java)
                            val u1Id = child.child("userOneId").getValue(String::class.java) ?: ""
                            val u2Id = child.child("userTwoId").getValue(String::class.java) ?: ""
                            val status = child.child("status").getValue(String::class.java)
                            if (u1 != null && u2 != null && status != null) {
                                val existing = previous.find { it.userOne == u1 && it.userTwo == u2 }
                                db.friendshipDao().insertFriendship(FriendshipEntity(u1, u2, u1Id, u2Id, status))

                                // Notify receiver when a new pending request arrives
                                if (activeUser.isNotBlank() && u2.equals(activeUser, ignoreCase = true) && !u1.equals(activeUser, ignoreCase = true)) {
                                    if (status == "PENDING_SENDER_ONE" && (existing == null || existing.status != "PENDING_SENDER_ONE")) {
                                        NotificationHelper.sendSocialNotification(
                                            context,
                                            title = context.getString(R.string.friend_request_notification, u1),
                                            body = context.getString(R.string.friend_request_notification_body),
                                            notificationId = "friend-request:${u1Id.ifBlank { u1 }}".hashCode(),
                                            targetScreen = "friends",
                                            extraData = u1
                                        )
                                    }
                                }

                                // Notify sender when the request is accepted
                                if (activeUser.isNotBlank() && u1.equals(activeUser, ignoreCase = true) && !u2.equals(activeUser, ignoreCase = true)) {
                                    if (status == "ACCEPTED" && existing != null && existing.status == "PENDING_SENDER_ONE") {
                                        NotificationHelper.sendSocialNotification(
                                            context,
                                            title = context.getString(R.string.friend_accepted_notification, u2),
                                            body = context.getString(R.string.friend_accepted_notification_body, u2),
                                            notificationId = "friend-accepted:${u2Id.ifBlank { u2 }}".hashCode(),
                                            targetScreen = "friends"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    _friendshipSyncError.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _friendshipSyncError.value = "Could not sync friend requests: ${e.localizedMessage}"
                }
            }
        }
        if (uid == null) {
            _friendshipSyncError.value = "Sign in online to sync friend requests."
        } else {
            for (field in listOf("userOneId", "userTwoId")) {
                listen(firebaseDb.child("friendships").orderByChild(field).equalTo(uid), object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        friendshipSnapshots.trySend(field to snapshot)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        _friendshipSyncError.value = "Could not sync friend requests: ${error.message}"
                    }
                })
            }
        }

        if (uid != null) {
            listenForAccountRecords("messages", uid, _messageSyncError) { records ->
                val messages = records.mapNotNull { child ->
                    val id = child.child("id").getValue(Long::class.java) ?: child.key?.toLongOrNull()
                    val sender = child.child("senderUsername").getValue(String::class.java)
                    val receiver = child.child("receiverUsername").getValue(String::class.java)
                    val text = child.child("messageText").getValue(String::class.java)
                    if (id == null || id <= 0 || sender == null || receiver == null || text == null) null
                    else MessageEntity(
                        id, sender, receiver,
                        child.child("senderUserId").getValue(String::class.java).orEmpty(),
                        child.child("receiverUserId").getValue(String::class.java).orEmpty(),
                        text, child.child("timestamp").getValue(Long::class.java) ?: 0
                    )
                }
                val previous = db.messageDao().getAllMessagesForUser(activeUsername()).map { it.id }.toSet()
                db.withTransaction {
                    db.messageDao().clearMessagesForUser(uid)
                    messages.forEach { db.messageDao().insertMessage(it) }
                }
                messages.filter { it.receiverUserId == uid && it.id !in previous }.forEach {
                    NotificationHelper.sendSocialNotification(
                        context, "Message from ${it.senderUsername}", it.messageText,
                        it.senderUserId.hashCode(), "chat", it.senderUsername
                    )
                }
            }
            listenForAccountRecords("trade_requests", uid, _tradeSyncError) { records ->
                val trades = records.mapNotNull { child ->
                    val id = child.child("tradeId").getValue(Long::class.java) ?: child.key?.toLongOrNull()
                    val sender = child.child("senderUsername").getValue(String::class.java)
                    val receiver = child.child("receiverUsername").getValue(String::class.java)
                    val offered = child.child("offeredPokemonId").getValue(String::class.java)
                    val status = child.child("tradeStatus").getValue(String::class.java)
                    if (id == null || id <= 0 || sender == null || receiver == null || offered == null || status == null) null
                    else TradeRequestEntity(
                        id, sender, receiver,
                        child.child("senderUserId").getValue(String::class.java).orEmpty(),
                        child.child("receiverUserId").getValue(String::class.java).orEmpty(),
                        offered, child.child("optionalDescription").getValue(String::class.java), status,
                        child.child("returnedPokemonId").getValue(String::class.java),
                        child.child("createdAt").getValue(Long::class.java) ?: 0,
                        child.child("completedAt").getValue(Long::class.java) ?: 0
                    )
                }
                val previous = db.tradeRequestDao().getTradeRequestsForUser(activeUsername()).associateBy { it.tradeId }
                val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val profile = withTimeout(15_000) { firebaseDb.child("users").child(uid).get().await() }
                CollectionPreferences.applyTrades(
                    prefs, uid, trades,
                    if (profile.hasChild("caught")) profile.child("caught").children.mapNotNull {
                        it.getValue(Long::class.java)?.toInt()
                    }.toSet() else emptySet(),
                    profile.child("caught_removed").children.mapNotNull { it.getValue(String::class.java)?.toIntOrNull() }.toSet(),
                    profile.child("applied_trades").children.mapNotNull { it.getValue(String::class.java) }.toSet()
                )
                if (trades.any { it.tradeStatus == "ACCEPTED" && it.returnedPokemonId != null }) {
                    db.syncActionDao().insertAction(SyncAction(type = "caught", payload = uid))
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "user-data-sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(
                            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                        ).build()
                    )
                }
                db.withTransaction {
                    db.tradeRequestDao().clearTradesForUser(uid)
                    trades.forEach { db.tradeRequestDao().insertTradeRequest(it) }
                }
                for (trade in trades) {
                    if (trade.tradeStatus == "ACCEPTED" || trade.tradeStatus == "DECLINED") {
                        val given = if (trade.senderUserId == uid) trade.offeredPokemonId else trade.returnedPokemonId
                        if (given != null) releasePokemon(uid, given, trade.tradeId)
                    }
                    if (previous[trade.tradeId]?.tradeStatus != trade.tradeStatus) {
                        val incoming = trade.receiverUserId == uid
                        if ((incoming && trade.tradeStatus == "PENDING") ||
                            (!incoming && trade.tradeStatus == "COUNTERED") || trade.tradeStatus == "ACCEPTED") {
                            val otherName = if (incoming) trade.senderUsername else trade.receiverUsername
                            val title = when (trade.tradeStatus) {
                                "PENDING" -> context.getString(R.string.trade_request_notification, trade.senderUsername)
                                "COUNTERED" -> context.getString(R.string.trade_return_notification, trade.receiverUsername)
                                else -> context.getString(R.string.trade_completed_notification, otherName)
                            }
                            val body = if (trade.returnedPokemonId == null)
                                context.getString(R.string.trade_request_notification_body, trade.offeredPokemonId)
                            else context.getString(R.string.trade_exchange_notification_body, trade.offeredPokemonId, trade.returnedPokemonId)
                            NotificationHelper.sendSocialNotification(
                                context, title, body,
                                trade.tradeId.hashCode(), "trade"
                            )
                        }
                    }
                }
            }
        } else {
            _messageSyncError.value = "Sign in online to load messages."
            _tradeSyncError.value = "Sign in online to load trade proposals."
        }
    }

    private fun activeUsername(): String =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .getString("logged_in_user", "").orEmpty()

    private fun listenForAccountRecords(
        path: String,
        uid: String,
        errors: MutableStateFlow<String?>,
        onRecords: suspend (List<DataSnapshot>) -> Unit,
    ) {
        val events = Channel<Pair<String, DataSnapshot>>(Channel.UNLIMITED)
        scope.launch {
            val latest = mutableMapOf<String, DataSnapshot>()
            for ((field, snapshot) in events) {
                latest[field] = snapshot
                if (latest.size < 2) continue
                try {
                    withTimeout(15_000) {
                        onRecords(latest.values.flatMap { it.children }.distinctBy { it.key })
                    }
                    errors.value = null
                } catch (e: TimeoutCancellationException) {
                    errors.value = "Sync timed out. Check your connection and reopen this screen to retry."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors.value = "Could not sync ${path.replace('_', ' ')}: ${e.localizedMessage}"
                }
            }
        }
        for (field in listOf("senderUserId", "receiverUserId")) {
            listen(firebaseDb.child(path).orderByChild(field).equalTo(uid), object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    events.trySend(field to snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    errors.value = "Could not sync ${path.replace('_', ' ')}: ${error.message}"
                }
            })
        }
    }

    // ---------------- Search & Users ----------------
    suspend fun searchUsers(query: String): List<User> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        withTimeout(15_000) {
            val snapshot = firebaseDb.child("trainer_profiles").get().await()
            snapshot.children.mapNotNull { child ->
                val username = child.child("username").getValue(String::class.java)
                val uid = child.key
                if (username == null || uid == null ||
                    !username.contains(query.trim(), ignoreCase = true)) {
                    return@mapNotNull null
                }
                val cached = db.userDao().getUserByUsername(username)
                val user = if (cached?.userId == uid) cached else User(username, uid)
                db.userDao().insertUser(user)
                user
            }
        }
    }

    // ---------------- Friendships ----------------
    suspend fun sendFriendRequest(senderUsername: String, targetUsername: String): Boolean =
        withContext(Dispatchers.IO) {
            withTimeout(15_000) {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                    ?: error("Sign in online before sending a friend request.")
                val users = firebaseDb.child("trainer_profiles").get().await()
                val senderName = firebaseDb.child("users").child(uid).child("username").get().await().getValue(String::class.java)
                    ?: error("Your online account profile could not be found.")
                require(senderName.equals(senderUsername, ignoreCase = true)) {
                    "Your account has changed. Sign in again before sending a request."
                }
                val matches = users.children.filter {
                    it.child("username").getValue(String::class.java)
                        ?.equals(targetUsername.trim(), ignoreCase = true) == true
                }
                require(matches.size == 1) { "Enter one existing trainer's complete username." }
                val target = matches.single()
                val targetName = target.child("username").getValue(String::class.java)!!
                val targetId = target.key ?: error("The trainer account has no ID.")
                require(uid != targetId) { "You cannot send a friend request to yourself." }

                // Consult the server so old local-only requests cannot block a retry.
                val existing = friendshipRecords(senderName, targetName)
                if (existing.any {
                    val status = it.child("status").getValue(String::class.java)
                    status == "ACCEPTED" || status == "PENDING_SENDER_ONE"
                }) return@withTimeout false

                val pairKey = listOf(uid, targetId).sorted().joinToString("_")
                val entity = FriendshipEntity(
                    senderName, targetName, uid, targetId, "PENDING_SENDER_ONE"
                )
                createRecord(firebaseDb.child("friendships").child(pairKey), entity)
            }
        }

    private suspend fun createRecord(reference: DatabaseReference, entity: Any): Boolean =
        transact(reference) { data ->
            if (data.value != null) false
            else { data.value = entity; true }
        }
    // Match both legacy username keys and new UID keys.
    private suspend fun friendshipRecords(userA: String, userB: String): List<DataSnapshot> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: error("Sign in online to manage friend requests.")
        val sent = firebaseDb.child("friendships").orderByChild("userOneId").equalTo(uid).get().await()
        val received = firebaseDb.child("friendships").orderByChild("userTwoId").equalTo(uid).get().await()
        return (sent.children + received.children).distinctBy { it.key }.filter {
            val one = it.child("userOne").getValue(String::class.java)
            val two = it.child("userTwo").getValue(String::class.java)
            (one.equals(userA, true) && two.equals(userB, true)) ||
                (one.equals(userB, true) && two.equals(userA, true))
        }
    }

    suspend fun acceptFriendRequest(currentUsername: String, requesterUsername: String) =
        withContext(Dispatchers.IO) {
            withTimeout(15_000) {
                val records = friendshipRecords(currentUsername, requesterUsername)
                require(records.any {
                    it.child("userTwo").getValue(String::class.java).equals(currentUsername, true) &&
                        it.child("status").getValue(String::class.java) == "PENDING_SENDER_ONE"
                }) { "This incoming request is no longer available." }
                val updates = records.associate { "${it.key}/status" to "ACCEPTED" }
                firebaseDb.child("friendships").updateChildren(updates).await()
            }
        }

    private suspend fun deleteFriendship(userA: String, userB: String) =
        withContext(Dispatchers.IO) {
            withTimeout(15_000) {
                val updates = friendshipRecords(userA, userB).associate { it.key!! to null }
                firebaseDb.child("friendships").updateChildren(updates).await()
            }
        }

    suspend fun declineFriendRequest(currentUsername: String, requesterUsername: String) =
        deleteFriendship(currentUsername, requesterUsername)

    suspend fun cancelFriendRequest(currentUsername: String, targetUsername: String) =
        deleteFriendship(currentUsername, targetUsername)

    suspend fun removeFriend(currentUsername: String, friendUsername: String) =
        deleteFriendship(currentUsername, friendUsername)

    fun observeFriendships(username: String) =
        db.friendshipDao().observeFriendshipsForUser(username)

    suspend fun getFriendships(username: String): List<FriendshipEntity> = withContext(Dispatchers.IO) {
        db.friendshipDao().getFriendshipsForUser(username)
    }

    suspend fun isFriend(userA: String, userB: String): Boolean = withContext(Dispatchers.IO) {
        val friendship = db.friendshipDao().getFriendshipBetween(userA, userB)
        friendship?.status == "ACCEPTED"
    }

    private data class FriendContact(val sender: User, val receiver: User, val friendshipKey: String)

    private suspend fun friendContact(senderUsername: String, receiverUsername: String): FriendContact {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in online first.")
        val ownProfile = firebaseDb.child("users").child(uid).get().await()
        val ownName = ownProfile.child("username").getValue(String::class.java)
            ?: error("Your account profile is unavailable.")
        require(ownName.equals(senderUsername, true)) { "Your account changed. Sign in again." }
        val profiles = firebaseDb.child("trainer_profiles").get().await()
        val matches = profiles.children.filter {
            it.child("username").getValue(String::class.java).equals(receiverUsername, true)
        }
        require(matches.size == 1) { "The friend's trainer profile could not be found." }
        val target = matches.single()
        val receiverId = target.key ?: error("The friend's account ID is unavailable.")
        val receiverName = target.child("username").getValue(String::class.java)!!
        require(uid != receiverId) { "Choose another trainer." }
        val friendship = friendshipRecords(ownName, receiverName).firstOrNull {
            it.child("status").getValue(String::class.java) == "ACCEPTED" &&
                setOf(it.child("userOneId").getValue(String::class.java),
                    it.child("userTwoId").getValue(String::class.java)) == setOf(uid, receiverId)
        } ?: error("An accepted friendship is required to message or trade.")
        return FriendContact(User(ownName, uid), User(receiverName, receiverId), friendship.key!!)
    }

    fun observeMessages(friendUsername: String) = db.messageDao().observeMessages(
        FirebaseAuth.getInstance().currentUser?.uid.orEmpty(), friendUsername
    )

    suspend fun sendMessage(senderUsername: String, receiverUsername: String, messageText: String): Boolean =
        withContext(Dispatchers.IO) {
            withTimeout(15_000) {
                require(messageText.isNotBlank() && messageText.length <= 2000) {
                    "Enter a message of up to 2,000 characters."
                }
                val contact = friendContact(senderUsername, receiverUsername)
                val id = newRecordId()
                val record = mapOf(
                    "id" to id,
                    "senderUsername" to contact.sender.username,
                    "receiverUsername" to contact.receiver.username,
                    "senderUserId" to contact.sender.userId,
                    "receiverUserId" to contact.receiver.userId,
                    "friendshipKey" to contact.friendshipKey,
                    "messageText" to messageText,
                    "timestamp" to ServerValue.TIMESTAMP
                )
                check(createRecord(firebaseDb.child("messages").child(id.toString()), record)) {
                    "The message could not be saved. Please retry."
                }
                true
            }
        }

    fun observeTradeRequests() = db.tradeRequestDao().observeTrades(
        FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    )

    suspend fun sendTradeRequest(
        senderUsername: String,
        receiverUsername: String,
        offeredPokemonId: String,
        optionalDescription: String?
    ): Boolean = withContext(Dispatchers.IO) {
        withTimeout(15_000) {
            val contact = friendContact(senderUsername, receiverUsername)
            val pokemonId = offeredPokemonId.toIntOrNull()
            val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            require(pokemonId != null && pokemonId in CollectionPreferences.caught(prefs, contact.sender.userId)) {
                "Choose a Pokémon from your collection."
            }
            require(optionalDescription.orEmpty().length <= 500) { "Trade notes can contain up to 500 characters." }
            val id = newRecordId()
            reservePokemon(contact.sender.userId, offeredPokemonId, id)
            val record = mapOf(
                "tradeId" to id,
                "senderUsername" to contact.sender.username,
                "receiverUsername" to contact.receiver.username,
                "senderUserId" to contact.sender.userId,
                "receiverUserId" to contact.receiver.userId,
                "friendshipKey" to contact.friendshipKey,
                "offeredPokemonId" to offeredPokemonId,
                "optionalDescription" to optionalDescription.orEmpty(),
                "tradeStatus" to "PENDING",
                "createdAt" to ServerValue.TIMESTAMP
            )
            check(createRecord(firebaseDb.child("trade_requests").child(id.toString()), record)) {
                releasePokemon(contact.sender.userId, offeredPokemonId, id)
                "The proposal could not be saved. Please retry."
            }
            true
        }
    }

    suspend fun counterTradeRequest(tradeId: Long, returnedPokemonId: Int) = withContext(Dispatchers.IO) {
        withTimeout(15_000) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in online first.")
            val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            require(returnedPokemonId in CollectionPreferences.caught(prefs, uid)) {
                "Choose a Pokémon from your collection."
            }
            val reference = firebaseDb.child("trade_requests").child(tradeId.toString())
            val snapshot = reference.get().await()
            require(snapshot.child("receiverUserId").getValue(String::class.java) == uid &&
                snapshot.child("tradeStatus").getValue(String::class.java) == "PENDING") {
                "This incoming proposal is no longer available."
            }
            reservePokemon(uid, returnedPokemonId.toString(), tradeId)
            val committed = transact(reference, snapshot.value) { data ->
                if (data.child("tradeStatus").getValue(String::class.java) != "PENDING") false
                else {
                    data.child("returnedPokemonId").value = returnedPokemonId.toString()
                    data.child("tradeStatus").value = "COUNTERED"
                    true
                }
            }
            if (!committed) {
                releasePokemon(uid, returnedPokemonId.toString(), tradeId)
                error("The proposal changed. Refresh Trading and try again.")
            }
        }
    }

    suspend fun acceptTradeRequest(tradeId: Long) = withContext(Dispatchers.IO) {
        withTimeout(15_000) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in online first.")
            val reference = firebaseDb.child("trade_requests").child(tradeId.toString())
            val snapshot = reference.get().await()
            require(snapshot.child("senderUserId").getValue(String::class.java) == uid) {
                "Only the original sender can confirm the exchange."
            }
            if (snapshot.child("tradeStatus").getValue(String::class.java) == "ACCEPTED") return@withTimeout
            val offered = snapshot.child("offeredPokemonId").getValue(String::class.java)?.toIntOrNull()
            val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            require(offered != null && offered in CollectionPreferences.caught(prefs, uid)) {
                "The offered Pokémon is no longer in your collection. Cancel this proposal."
            }
            check(transact(reference, snapshot.value) { data ->
                if (data.child("tradeStatus").getValue(String::class.java) != "COUNTERED") false
                else {
                    data.child("tradeStatus").value = "ACCEPTED"
                    data.child("completedAt").value = ServerValue.TIMESTAMP
                    true
                }
            }) { "This exchange is no longer awaiting confirmation." }
        }
    }

    suspend fun declineTradeRequest(tradeId: Long) = withContext(Dispatchers.IO) {
        withTimeout(15_000) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in online first.")
            val reference = firebaseDb.child("trade_requests").child(tradeId.toString())
            val snapshot = reference.get().await()
            require(uid in listOf(
                snapshot.child("senderUserId").getValue(String::class.java),
                snapshot.child("receiverUserId").getValue(String::class.java)
            )) { "This proposal belongs to another account." }
            check(transact(reference, snapshot.value) { data ->
                if (data.child("tradeStatus").getValue(String::class.java) !in listOf("PENDING", "COUNTERED")) false
                else {
                    data.child("tradeStatus").value = "DECLINED"
                    true
                }
            }) { "This proposal is already completed." }
        }
    }

    private suspend fun reservePokemon(uid: String, pokemonId: String, tradeId: Long) {
        val lock = firebaseDb.child("trade_locks").child(uid).child(pokemonId)
        val previousId = lock.get().await().getValue(Long::class.java)
        if (previousId != null && previousId != tradeId) {
            val previous = firebaseDb.child("trade_requests").child(previousId.toString()).get().await()
            val referencedPokemon = if (previous.child("senderUserId").getValue(String::class.java) == uid)
                previous.child("offeredPokemonId").getValue(String::class.java)
            else previous.child("returnedPokemonId").getValue(String::class.java)
            if (!previous.exists() || previous.child("tradeStatus").getValue(String::class.java) == "DECLINED" || referencedPokemon != pokemonId) {
                releasePokemon(uid, pokemonId, previousId)
            }
        }
        check(transact(lock) { data ->
            if (data.value != null && data.getValue(Long::class.java) != tradeId) false
            else { data.value = tradeId; true }
        }) { "That Pokémon is reserved for another proposal. Complete or cancel it first." }
    }

    private suspend fun releasePokemon(uid: String, pokemonId: String, tradeId: Long) {
        val lock = firebaseDb.child("trade_locks").child(uid).child(pokemonId)
        if (lock.get().await().getValue(Long::class.java) != tradeId) return
        transact(lock, tradeId) { data ->
            if (data.getValue(Long::class.java) != tradeId) false
            else { data.value = null; true }
        }
    }

    private fun newRecordId(): Long = kotlin.random.Random.nextLong(1, 9_007_199_254_740_991)

    private suspend fun transact(
        reference: DatabaseReference,
        initialValue: Any? = null,
        update: (MutableData) -> Boolean
    ): Boolean = suspendCancellableCoroutine { continuation ->
        reference.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                // Transactions may start with an empty cache even after get(). The server
                // compares versions and retries this handler against its latest record.
                if (data.value == null && initialValue != null) data.value = initialValue
                return if (update(data)) Transaction.success(data) else Transaction.abort()
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error == null) continuation.resumeWith(Result.success(committed))
                else continuation.resumeWith(Result.failure(error.toException()))
            }
        }, false)
    }
}
