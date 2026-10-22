package com.pokemon.explorer.security

import org.mindrot.jbcrypt.BCrypt

object PasswordHasher {
    /** Hashes a password using BCrypt. */
    fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    /** Verifies a password against a hash. */
    fun verifyPassword(password: String, hash: String): Boolean {
        return try {
            BCrypt.checkpw(password, hash)
        } catch (e: Exception) {
            false
        }
    }
}
