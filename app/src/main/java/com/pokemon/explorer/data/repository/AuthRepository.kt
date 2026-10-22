package com.pokemon.explorer.data.repository

import com.pokemon.explorer.data.db.User
import com.pokemon.explorer.data.db.UserDao
import com.pokemon.explorer.security.PasswordHasher

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthRepository(private val userDao: UserDao) {
    suspend fun register(username: String, password: String): AuthResult {
        if (username.isBlank() || password.isBlank()) {
            return AuthResult.Error("Username and password cannot be empty")
        }

        val existingUser = userDao.getUserByUsername(username)
        if (existingUser != null) {
            return AuthResult.Error("User already exists")
        }

        val passwordHash = PasswordHasher.hashPassword(password)
        val user = User(username = username, passwordHash = passwordHash)
        return try {
            userDao.insertUser(user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error("Registration failed: ${e.localizedMessage}")
        }
    }

    suspend fun login(username: String, password: String): AuthResult {
        val user = userDao.getUserByUsername(username)
        return if (user != null && PasswordHasher.verifyPassword(password, user.passwordHash)) {
            AuthResult.Success(user)
        } else {
            AuthResult.Error("Invalid username or password")
        }
    }
}
