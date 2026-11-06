package com.pokemon.explorer.utils

/**
 * Encapsulates the outcome of a field or form validation operation.
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()

    val isValid: Boolean get() = this is Success
    val errorMessage: String? get() = (this as? Error)?.message
}

/**
 * Utility object for performing Create Registration and Log In validation logic.
 */
object AuthValidator {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /**
     * Validates username input.
     * Rule: Must not be empty or blank, must be at least 3 characters after trimming.
     */
    fun validateUsername(username: String): ValidationResult {
        if (username.isBlank()) {
            return ValidationResult.Error("Username cannot be empty")
        }
        if (username.trim().length < 3) {
            return ValidationResult.Error("Username must be at least 3 characters")
        }
        return ValidationResult.Success
    }

    /**
     * Validates email address input.
     * Rule: Must not be empty or blank, must match standard email address format.
     */
    fun validateEmail(email: String): ValidationResult {
        if (email.isBlank()) {
            return ValidationResult.Error("Email cannot be empty")
        }
        if (!EMAIL_REGEX.matches(email.trim())) {
            return ValidationResult.Error("Invalid email address format")
        }
        return ValidationResult.Success
    }

    /**
     * Validates password input.
     * Rule: Must not be empty, must be at least 6 characters.
     */
    fun validatePassword(password: String): ValidationResult {
        if (password.isEmpty()) {
            return ValidationResult.Error("Password cannot be empty")
        }
        if (password.length < 6) {
            return ValidationResult.Error("Password must be at least 6 characters")
        }
        return ValidationResult.Success
    }

    /**
     * Validates Create Registration input fields.
     */
    fun validateRegistration(
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): ValidationResult {
        val usernameResult = validateUsername(username)
        if (!usernameResult.isValid) return usernameResult

        val emailResult = validateEmail(email)
        if (!emailResult.isValid) return emailResult

        val passwordResult = validatePassword(password)
        if (!passwordResult.isValid) return passwordResult

        if (password != confirmPassword) {
            return ValidationResult.Error("Passwords do not match")
        }

        return ValidationResult.Success
    }

    /**
     * Validates Log In input fields.
     */
    fun validateLogin(
        email: String,
        password: String,
    ): ValidationResult {
        val emailResult = validateEmail(email)
        if (!emailResult.isValid) return emailResult

        if (password.isEmpty()) {
            return ValidationResult.Error("Password cannot be empty")
        }

        return ValidationResult.Success
    }
}
