package com.pokemon.explorer.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.pokemon.explorer.data.CollectionPreferences
import com.pokemon.explorer.data.ThemePreferences
import com.pokemon.explorer.data.db.AppDatabase
import kotlinx.coroutines.tasks.await

/**
 * WorkManager CoroutineWorker for synchronizing queued local user actions up to Firebase.
 *
 * Background sync guarantees data persistence when connection is restored after offline usage.
 * Processes queued items in Room table [SyncActionDao] and pushes them to Firebase Realtime Database
 * and Cloud Firestore.
 *
 * References:
 * - WorkManager Overview: https://developer.android.com/topic/libraries/architecture/workmanager
 * - CoroutineWorker API: https://developer.android.com/reference/androidx/work/CoroutineWorker
 * - Firebase Realtime DB Data Structures: https://firebase.google.com/docs/database/android/read-and-write
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "SyncWorker starting execution: checking background sync queue.")
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.w(TAG, "SyncWorker cancelled: No authenticated Firebase user currently logged in.")
            return Result.success()
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val syncDao = db.syncActionDao()
        val actions = syncDao.getAllActions()

        if (actions.isEmpty()) {
            Log.d(TAG, "SyncWorker: Sync queue is empty. Nothing to process.")
            return Result.success()
        }

        Log.i(TAG, "SyncWorker found ${actions.size} pending sync action(s) to process for UID=$uid.")

        val rtdb = FirebaseDatabase.getInstance().reference
        val firestore = FirebaseFirestore.getInstance()
        val prefs = applicationContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val userKey = prefs.getString("logged_in_user_id", uid) ?: uid
        if (userKey != uid) {
            Log.w(TAG, "SyncWorker user mismatch (userKey=$userKey, uid=$uid). Retrying later.")
            return Result.retry()
        }

        return try {
            val processedTypes = mutableSetOf<String>()

            for (action in actions) {
                if (action.type in listOf("caught", "theme") && action.payload.isNotBlank() && action.payload != uid) {
                    Log.d(TAG, "Skipping action ${action.type} with owner ${action.payload} for uid $uid")
                    continue
                }
                if (!processedTypes.contains(action.type)) {
                    processedTypes.add(action.type)
                    Log.d(TAG, "Processing sync action type: ${action.type}")
                    when (action.type) {
                        "favourites" -> {
                            val saved = prefs.getStringSet("favourites_$userKey", null) ?: prefs.getStringSet("favourites", emptySet()) ?: emptySet()
                            val list = saved.mapNotNull { it.toIntOrNull() }
                            rtdb.child("users").child(uid).child("favourites").setValue(list).await()
                            try {
                                firestore.collection("users").document(uid).update("favourites", list).await()
                            } catch (_: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("favourites" to list)).await()
                            }
                        }
                        "caught" -> {
                            val snapshot = CollectionPreferences.syncSnapshot(prefs, uid)
                            val list = snapshot.caught.toList()
                            val removed = snapshot.removed.map { it.toString() }
                            val appliedTrades = snapshot.appliedTrades.toList()
                            rtdb.child("users").child(uid).updateChildren(mapOf(
                                "caught" to list,
                                "caught_removed" to removed,
                                "applied_trades" to appliedTrades
                            )).await()
                            try {
                                firestore.collection("users").document(uid).update("caught", list).await()
                            } catch (_: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("caught" to list)).await()
                            }
                        }
                        "theme" -> {
                            val pending = ThemePreferences.pending(prefs, uid)
                                ?: if (action.payload.isBlank()) ThemePreferences.preserveLegacyPending(prefs, uid) else null
                            if (pending == null) {
                                syncDao.deleteAction(action)
                                continue
                            }
                            val theme = pending.theme.name
                            rtdb.child("users").child(uid).updateChildren(mapOf(
                                "theme" to theme,
                                "theme_revision" to pending.revision
                            )).await()
                            ThemePreferences.confirm(prefs, uid, pending.revision)
                            try {
                                firestore.collection("users").document(uid).update("theme", theme).await()
                            } catch (_: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("theme" to theme)).await()
                            }
                        }
                        "search_history_enabled" -> {
                            val enabled = prefs.getBoolean("search_history_enabled", true)
                            rtdb.child("users").child(uid).child("search_history_enabled").setValue(enabled).await()
                            try {
                                firestore.collection("users").document(uid).update("search_history_enabled", enabled).await()
                            } catch (_: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("search_history_enabled" to enabled)).await()
                            }
                        }
                        "resume_last_viewed" -> {
                            val enabled = prefs.getBoolean("resume_last_viewed", true)
                            rtdb.child("users").child(uid).child("resume_last_viewed").setValue(enabled).await()
                            try {
                                firestore.collection("users").document(uid).update("resume_last_viewed", enabled).await()
                            } catch (_: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("resume_last_viewed" to enabled)).await()
                            }
                        }
                        "notifications_enabled" -> {
                            val enabled = prefs.getBoolean("notifications_enabled", false)
                            rtdb.child("users").child(uid).child("notifications_enabled").setValue(enabled).await()
                            try {
                                firestore.collection("users").document(uid).update("notifications_enabled", enabled).await()
                            } catch (_: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("notifications_enabled" to enabled)).await()
                            }
                        }
                        "recent_searches" -> {
                            val list = prefs.getString("recent_searches_csv_$userKey", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                            rtdb.child("users").child(uid).child("recent_searches").setValue(list).await()
                            try {
                                firestore.collection("users").document(uid).update("recent_searches", list).await()
                            } catch (_: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("recent_searches" to list)).await()
                            }
                        }
                        "notes" -> {
                            val username = prefs.getString("logged_in_user", "") ?: ""
                            if (username.isNotBlank()) {
                                val notes = db.pokemonNoteDao().getAllNotesForUser(username)
                                val notesMap = notes.associate { it.pokemonId.toString() to mapOf(
                                    "nickname" to it.nickname,
                                    "note" to it.note,
                                    "timestamp" to it.timestamp
                                ) }
                                
                                rtdb.child("users").child(uid).child("notes").setValue(notesMap).await()
                                try {
                                    firestore.collection("users").document(uid).update("notes", notesMap).await()
                                } catch (_: Exception) {
                                    firestore.collection("users").document(uid).set(mapOf("notes" to notesMap)).await()
                                }
                            }
                        }
                    }
                }
                syncDao.deleteAction(action)
                Log.d(TAG, "Deleted processed action ${action.id} from queue.")
            }

            Log.i(TAG, "SyncWorker completed sync batch successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker batch failed with exception: ${e.message}", e)
            Result.retry()
        }
    }
}
