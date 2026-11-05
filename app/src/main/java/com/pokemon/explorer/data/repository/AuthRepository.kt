package com.pokemon.explorer.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.pokemon.explorer.data.db.User
import com.pokemon.explorer.data.db.UserDao
import com.pokemon.explorer.security.PasswordHasher
import kotlinx.coroutines.tasks.await

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthRepository(private val userDao: UserDao) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    suspend fun register(username: String, email: String, password: String): AuthResult {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            return AuthResult.Error("Username, email and password cannot be empty")
        }

        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            
            if (firebaseUser != null) {
                // Save user info to Realtime Database and Firestore
                val userMap = mapOf(
                    "userId" to firebaseUser.uid,
                    "username" to username,
                    "email" to email
                )
                db.child("users").child(firebaseUser.uid).setValue(userMap).await()
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid).set(userMap).await()
                
                val user = User(
                    username = username,
                    userId = firebaseUser.uid,
                    email = email,
                    passwordHash = PasswordHasher.hashPassword(password)
                )
                try {
                    userDao.insertUser(user)
                } catch (e: Exception) {
                    // Ignore local DB insertion errors
                }
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Registration failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Registration failed")
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            
            if (firebaseUser != null) {
                // Fetch username from Realtime Database
                val snapshot = db.child("users").child(firebaseUser.uid).get().await()
                val username = snapshot.child("username").getValue(String::class.java) ?: "User"
                
                val user = User(
                    username = username,
                    userId = firebaseUser.uid,
                    email = email,
                    passwordHash = PasswordHasher.hashPassword(password)
                )
                try {
                    userDao.insertUser(user)
                } catch (insertError: Exception) {
                    // Ignore
                }
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Login failed")
            }
        } catch (e: Exception) {
            // Attempt offline login bypass
            try {
                val localUser = userDao.getUserByEmail(email)
                if (localUser != null && PasswordHasher.verifyPassword(password, localUser.passwordHash)) {
                    return AuthResult.Success(localUser)
                }
            } catch (offlineError: Exception) {
                // Ignore offline error and just return original error
            }
            AuthResult.Error("Network error. Offline login failed: ${e.localizedMessage}")
        }
    }

    suspend fun googleSignIn(idToken: String): AuthResult {
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user

            if (firebaseUser != null) {
                val username = firebaseUser.displayName ?: "User"
                val email = firebaseUser.email ?: ""

                // Save or update user info to Realtime Database and Firestore
                val userMap = mapOf(
                    "userId" to firebaseUser.uid,
                    "username" to username,
                    "email" to email
                )
                db.child("users").child(firebaseUser.uid).setValue(userMap).await()
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid).set(userMap).await()

                val user = User(username = username, userId = firebaseUser.uid, email = email, passwordHash = "")
                try {
                    userDao.insertUser(user)
                } catch (e: Exception) {
                    // Ignore local DB insertion errors if they already exist
                }
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Google Sign-In failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Google Sign-In failed")
        }
    }

    suspend fun updatePassword(newPassword: String): AuthResult {
        return try {
            val firebaseUser = auth.currentUser ?: return AuthResult.Error("No user logged in")
            
            // 1. Update Firebase Auth Password
            firebaseUser.updatePassword(newPassword).await()
            
            // 2. Update Local Room DB
            val username = firebaseUser.displayName ?: "" // Or get from Realtime DB if needed
            // To properly update Room, we might need the actual username, 
            // since we don't have it directly from firebaseUser unless it's in displayName.
            // Let's fetch it from RTDB to be safe:
            val snapshot = db.child("users").child(firebaseUser.uid).get().await()
            val dbUsername = snapshot.child("username").getValue(String::class.java)
            
            if (dbUsername != null) {
                userDao.updatePassword(dbUsername, PasswordHasher.hashPassword(newPassword))
            }
            
            AuthResult.Success(User(username = dbUsername ?: "User", email = firebaseUser.email ?: "", passwordHash = ""))
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Failed to update password")
        }
    }

    suspend fun updateUsername(newUsername: String): AuthResult {
        if (newUsername.isBlank()) return AuthResult.Error("Username cannot be empty")
        
        return try {
            val firebaseUser = auth.currentUser ?: return AuthResult.Error("No user logged in")
            
            // 1. Get current username to update local DB
            val snapshot = db.child("users").child(firebaseUser.uid).get().await()
            val oldUsername = snapshot.child("username").getValue(String::class.java) ?: return AuthResult.Error("Could not find current username")
            
            if (userDao.getUserByUsername(newUsername) != null && oldUsername != newUsername) {
                return AuthResult.Error("Username already exists locally")
            }
            
            // 2. Update Firebase RTDB & Firestore
            db.child("users").child(firebaseUser.uid).child("username").setValue(newUsername).await()
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid).update("username", newUsername).await()
            
            // 3. Update Firebase Auth Profile
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(newUsername)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()
            
            // 4. Update Local Room DB
            userDao.updateUsername(oldUsername, newUsername)
            
            AuthResult.Success(User(username = newUsername, email = firebaseUser.email ?: "", passwordHash = ""))
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Failed to update username")
        }
    }
}
