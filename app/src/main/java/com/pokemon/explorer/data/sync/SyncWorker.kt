package com.pokemon.explorer.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.pokemon.explorer.data.db.AppDatabase
import kotlinx.coroutines.tasks.await

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return Result.success() // No user, nothing to sync to Firebase

        val db = AppDatabase.getDatabase(applicationContext)
        val syncDao = db.syncActionDao()
        val actions = syncDao.getAllActions()

        if (actions.isEmpty()) {
            return Result.success()
        }

        val rtdb = FirebaseDatabase.getInstance().reference
        val firestore = FirebaseFirestore.getInstance()
        val prefs = applicationContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        return try {
            val processedTypes = mutableSetOf<String>()

            // We only need to sync the final state for lists like favourites/caught,
            // or we could execute actions sequentially. But the AppState is already 
            // saving the entire set to SharedPreferences when modified locally.
            // So we can just read the latest state from SharedPreferences and push it.
            
            // To be precise with "offline actions", we'll just re-sync the keys 
            // that were modified offline.
            
            for (action in actions) {
                if (!processedTypes.contains(action.type)) {
                    processedTypes.add(action.type)
                    when (action.type) {
                        "favourites" -> {
                            val saved = prefs.getStringSet("favourites", emptySet()) ?: emptySet()
                            val list = saved.mapNotNull { it.toIntOrNull() }
                            rtdb.child("users").child(uid).child("favourites").setValue(list).await()
                            try {
                                firestore.collection("users").document(uid).update("favourites", list).await()
                            } catch (e: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("favourites" to list)).await()
                            }
                        }
                        "caught" -> {
                            val saved = prefs.getStringSet("caught", emptySet()) ?: emptySet()
                            val list = saved.mapNotNull { it.toIntOrNull() }
                            rtdb.child("users").child(uid).child("caught").setValue(list).await()
                            try {
                                firestore.collection("users").document(uid).update("caught", list).await()
                            } catch (e: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("caught" to list)).await()
                            }
                        }
                        "theme" -> {
                            val theme = prefs.getString("theme", "System") ?: "System"
                            rtdb.child("users").child(uid).child("theme").setValue(theme).await()
                            try {
                                firestore.collection("users").document(uid).update("theme", theme).await()
                            } catch (e: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("theme" to theme)).await()
                            }
                        }
                        "search_history_enabled" -> {
                            val enabled = prefs.getBoolean("search_history_enabled", true)
                            rtdb.child("users").child(uid).child("search_history_enabled").setValue(enabled).await()
                            try {
                                firestore.collection("users").document(uid).update("search_history_enabled", enabled).await()
                            } catch (e: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("search_history_enabled" to enabled)).await()
                            }
                        }
                        "resume_last_viewed" -> {
                            val enabled = prefs.getBoolean("resume_last_viewed", true)
                            rtdb.child("users").child(uid).child("resume_last_viewed").setValue(enabled).await()
                            try {
                                firestore.collection("users").document(uid).update("resume_last_viewed", enabled).await()
                            } catch (e: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("resume_last_viewed" to enabled)).await()
                            }
                        }
                        "notifications_enabled" -> {
                            val enabled = prefs.getBoolean("notifications_enabled", false)
                            rtdb.child("users").child(uid).child("notifications_enabled").setValue(enabled).await()
                            try {
                                firestore.collection("users").document(uid).update("notifications_enabled", enabled).await()
                            } catch (e: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("notifications_enabled" to enabled)).await()
                            }
                        }
                        "recent_searches" -> {
                            val list = prefs.getString("recent_searches_csv", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                            rtdb.child("users").child(uid).child("recent_searches").setValue(list).await()
                            try {
                                firestore.collection("users").document(uid).update("recent_searches", list).await()
                            } catch (e: Exception) {
                                firestore.collection("users").document(uid).set(mapOf("recent_searches" to list)).await()
                            }
                        }
                        "notes" -> {
                            // Sync all offline notes up to Firebase
                            val user = db.userDao().getUserByUsername(prefs.getString("logged_in_user", "") ?: "")
                            if (user != null) {
                                val notes = db.pokemonNoteDao().getAllNotesForUser(user.username)
                                val notesMap = notes.associate { it.pokemonId.toString() to mapOf(
                                    "nickname" to it.nickname,
                                    "note" to it.note,
                                    "timestamp" to it.timestamp
                                ) }
                                
                                rtdb.child("users").child(uid).child("notes").setValue(notesMap).await()
                                try {
                                    firestore.collection("users").document(uid).update("notes", notesMap).await()
                                } catch (e: Exception) {
                                    firestore.collection("users").document(uid).set(mapOf("notes" to notesMap)).await()
                                }
                            }
                        }
                    }
                }
                syncDao.deleteAction(action)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
