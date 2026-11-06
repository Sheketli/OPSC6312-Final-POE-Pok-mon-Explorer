package com.pokemon.explorer.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.pokemon.explorer.data.db.User
import com.pokemon.explorer.data.db.UserDao
import com.pokemon.explorer.security.PasswordHasher
import com.pokemon.explorer.utils.AuthValidator
import com.pokemon.explorer.utils.ValidationResult
import kotlinx.coroutines.tasks.await

/** Represents authentication operation results. */
sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Repository handling user authentication, profile synchronization, and offline fallback login.
 *
 * Integrates:
 * - Firebase Authentication for cloud identity management: https://firebase.google.com/docs/auth
 * - Firebase Realtime Database for profile synchronization: https://firebase.google.com/docs/database
 * - Cloud Firestore for cloud backup: https://firebase.google.com/docs/firestore
 * - Local Room DB ([UserDao]) with BCrypt hashing ([PasswordHasher]) for offline login fallback: https://developer.android.com/training/data-storage/room
 */
class AuthRepository(private val userDao: UserDao) {

    companion object {
        private const val TAG = "AuthRepository"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private suspend fun publishTrainerProfile(uid: String, username: String) {
        Log.d(TAG, "publishTrainerProfile: uid=$uid, username=$username")
        db.child("trainer_profiles").child(uid).setValue(
            mapOf("userId" to uid, "username" to username)
        ).await()
    }

    /**
     * Registers a new user account across Firebase Auth, Realtime DB, Firestore, and local Room DB.
     */
    suspend fun register(username: String, email: String, password: String): AuthResult {
        Log.i(TAG, "register: Attempting user registration for username=$username, email=$email")
        val validation = AuthValidator.validateRegistration(username, email, password, password)
        if (validation is ValidationResult.Error) {
            Log.w(TAG, "register: Validation failed - ${validation.message}")
            return AuthResult.Error(validation.message)
        }

        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            
            if (firebaseUser != null) {
                Log.i(TAG, "Firebase account created successfully with UID=${firebaseUser.uid}")
                val userMap = mapOf(
                    "userId" to firebaseUser.uid,
                    "username" to username,
                    "email" to email
                )
                db.child("users").child(firebaseUser.uid).setValue(userMap).await()
                publishTrainerProfile(firebaseUser.uid, username)
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid).set(userMap).await()
                
                val user = User(
                    username = username,
                    userId = firebaseUser.uid,
                    email = email,
                    passwordHash = PasswordHasher.hashPassword(password)
                )
                try {
                    userDao.insertUser(user)
                    Log.d(TAG, "Inserted registered user into local Room database.")
                } catch (e: Exception) {
                    Log.w(TAG, "Local DB insertion error ignored: ${e.message}")
                }
                AuthResult.Success(user)
            } else {
                Log.e(TAG, "Registration failed: Firebase user object is null.")
                AuthResult.Error("Registration failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed with exception: ${e.message}", e)
            AuthResult.Error(e.localizedMessage ?: "Registration failed")
        }
    }

    /**
     * Authenticates existing user with online Firebase Auth, falling back to local Room verification if offline.
     */
    suspend fun login(email: String, password: String): AuthResult {
        Log.i(TAG, "login: Attempting sign-in for email=$email")
        val validation = AuthValidator.validateLogin(email, password)
        if (validation is ValidationResult.Error) {
            Log.w(TAG, "login: Validation failed - ${validation.message}")
            return AuthResult.Error(validation.message)
        }
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            
            if (firebaseUser != null) {
                Log.i(TAG, "Online Firebase Authentication successful for UID=${firebaseUser.uid}")
                val snapshot = db.child("users").child(firebaseUser.uid).get().await()
                val username = snapshot.child("username").getValue(String::class.java) ?: "User"
                publishTrainerProfile(firebaseUser.uid, username)
                
                val user = User(
                    username = username,
                    userId = firebaseUser.uid,
                    email = email,
                    passwordHash = PasswordHasher.hashPassword(password)
                )
                try {
                    userDao.insertUser(user)
                    Log.d(TAG, "Updated/inserted user into local Room database.")
                } catch (insertError: Exception) {
                    Log.w(TAG, "Local DB insert warning: ${insertError.message}")
                }
                AuthResult.Success(user)
            } else {
                Log.w(TAG, "Firebase login failed: null user object.")
                AuthResult.Error("Login failed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online login failed (${e.message}). Attempting offline Room database login fallback.")
            try {
                val localUser = userDao.getUserByEmail(email)
                if (localUser != null && PasswordHasher.verifyPassword(password, localUser.passwordHash)) {
                    Log.i(TAG, "Offline login successful for local user: ${localUser.username}")
                    return AuthResult.Success(localUser)
                } else {
                    Log.w(TAG, "Offline login failed: invalid local credentials or missing user record.")
                }
            } catch (offlineError: Exception) {
                Log.e(TAG, "Offline login fallback error: ${offlineError.message}", offlineError)
            }
            AuthResult.Error("Network error. Offline login failed: ${e.localizedMessage}")
        }
    }

    /**
     * Authenticates user via Google Sign-In ID Token.
     */
    suspend fun googleSignIn(idToken: String): AuthResult {
        Log.i(TAG, "googleSignIn: Attempting Google credential authentication.")
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user

            if (firebaseUser != null) {
                Log.i(TAG, "Google Sign-In successful for UID=${firebaseUser.uid}")
                val username = firebaseUser.displayName ?: "User"
                val email = firebaseUser.email ?: ""

                val userMap = mapOf(
                    "userId" to firebaseUser.uid,
                    "username" to username,
                    "email" to email
                )
                db.child("users").child(firebaseUser.uid).setValue(userMap).await()
                publishTrainerProfile(firebaseUser.uid, username)
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid).set(userMap).await()

                val user = User(username = username, userId = firebaseUser.uid, email = email, passwordHash = "")
                try {
                    userDao.insertUser(user)
                } catch (e: Exception) {
                    Log.w(TAG, "Local DB insertion error ignored: ${e.message}")
                }
                AuthResult.Success(user)
            } else {
                Log.e(TAG, "Google Sign-In failed: firebaseUser is null.")
                AuthResult.Error("Google Sign-In failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            AuthResult.Error(e.localizedMessage ?: "Google Sign-In failed")
        }
    }

    /**
     * Updates password in Firebase Authentication and local Room database.
     */
    suspend fun updatePassword(newPassword: String): AuthResult {
        Log.i(TAG, "updatePassword: Updating user password.")
        return try {
            val firebaseUser = auth.currentUser ?: return AuthResult.Error("No user logged in")
            
            firebaseUser.updatePassword(newPassword).await()
            Log.d(TAG, "Firebase password updated successfully.")

            val snapshot = db.child("users").child(firebaseUser.uid).get().await()
            val dbUsername = snapshot.child("username").getValue(String::class.java)
            
            if (dbUsername != null) {
                userDao.updatePassword(dbUsername, PasswordHasher.hashPassword(newPassword))
                Log.d(TAG, "Local Room password hash updated for $dbUsername.")
            }
            
            AuthResult.Success(User(username = dbUsername ?: "User", email = firebaseUser.email ?: "", passwordHash = ""))
        } catch (e: Exception) {
            Log.e(TAG, "Password update failed: ${e.message}", e)
            AuthResult.Error(e.localizedMessage ?: "Failed to update password")
        }
    }

    /**
     * Updates profile username across Realtime DB, Firestore, Auth Profile, and Room.
     */
    suspend fun updateUsername(newUsername: String): AuthResult {
        Log.i(TAG, "updateUsername: Requesting username update to $newUsername")
        if (newUsername.isBlank()) return AuthResult.Error("Username cannot be empty")
        
        return try {
            val firebaseUser = auth.currentUser ?: return AuthResult.Error("No user logged in")
            
            val snapshot = db.child("users").child(firebaseUser.uid).get().await()
            val oldUsername = snapshot.child("username").getValue(String::class.java) ?: return AuthResult.Error("Could not find current username")
            
            if (userDao.getUserByUsername(newUsername) != null && oldUsername != newUsername) {
                Log.w(TAG, "Username update rejected: $newUsername already exists in local DB.")
                return AuthResult.Error("Username already exists locally")
            }
            
            db.updateChildren(mapOf(
                "users/${firebaseUser.uid}/username" to newUsername,
                "trainer_profiles/${firebaseUser.uid}/username" to newUsername,
                "trainer_profiles/${firebaseUser.uid}/userId" to firebaseUser.uid
            )).await()
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid).update("username", newUsername).await()
            
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(newUsername)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()
            
            userDao.updateUsername(oldUsername, newUsername)
            Log.i(TAG, "Successfully updated username from $oldUsername to $newUsername")

            AuthResult.Success(User(username = newUsername, email = firebaseUser.email ?: "", passwordHash = ""))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update username: ${e.message}", e)
            AuthResult.Error(e.localizedMessage ?: "Failed to update username")
        }
    }

    /**
     * Permanently deletes user account from Firebase Auth, Realtime DB, Firestore, and Room.
     */
    suspend fun deleteAccount(): AuthResult {
        Log.w(TAG, "deleteAccount: Initiating complete account deletion procedure.")
        return try {
            val firebaseUser = auth.currentUser ?: return AuthResult.Error("No user logged in")
            val uid = firebaseUser.uid

            val snapshot = db.child("users").child(uid).get().runCatching { await() }.getOrNull()
            val dbUsername = snapshot?.child("username")?.getValue(String::class.java) ?: firebaseUser.displayName ?: ""

            db.child("trainer_profiles").child(uid).removeValue().await()
            try { db.child("users").child(uid).removeValue().await() } catch (_: Exception) {}
            try { com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).delete().await() } catch (_: Exception) {}

            userDao.deleteUser(dbUsername, uid)
            firebaseUser.delete().await()

            Log.i(TAG, "Account deletion completed successfully for UID=$uid")
            AuthResult.Success(User(username = dbUsername, userId = uid, email = "", passwordHash = ""))
        } catch (e: Exception) {
            Log.e(TAG, "Account deletion failed: ${e.message}", e)
            AuthResult.Error(e.localizedMessage ?: "Failed to delete account")
        }
    }
}
