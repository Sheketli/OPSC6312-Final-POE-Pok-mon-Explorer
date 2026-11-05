package com.pokemon.explorer.security

import android.util.Log
import org.mindrot.jbcrypt.BCrypt

/**
 * Utility object for securely hashing and verifying passwords using the BCrypt key derivation algorithm.
 *
 * Implements salted password hashing according to OWASP cryptographic storage standards to safeguard local Room DB entries.
 *
 * References:
 * - OWASP Password Storage Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
 * - JBCrypt Library Documentation: https://www.mindrot.org/projects/jBCrypt/
 */
object PasswordHasher {

    private const val TAG = "PasswordHasher"

    /**
     * Hashes a plain-text password using a freshly generated salt.
     */
    fun hashPassword(password: String): String {
        Log.d(TAG, "hashPassword: Generating salted BCrypt hash.")
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    /**
     * Verifies a plain-text password against a candidate BCrypt hash.
     */
    fun verifyPassword(password: String, hash: String): Boolean {
        return try {
            val isMatch = BCrypt.checkpw(password, hash)
            Log.d(TAG, "verifyPassword result: $isMatch")
            isMatch
        } catch (e: Exception) {
            Log.e(TAG, "verifyPassword exception: ${e.message}", e)
            false
        }
    }
}
