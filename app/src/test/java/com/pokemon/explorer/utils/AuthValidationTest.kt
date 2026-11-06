package com.pokemon.explorer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Unit Tests for Create Registration Validation and Log In Validation.
 */
class AuthValidationTest {

    // ==========================================
    // CREATE REGISTRATION VALIDATION TESTS
    // ==========================================

    @Test
    fun `registration succeeds with valid credentials`() {
        val result = AuthValidator.validateRegistration(
            username = "PikachuMaster",
            email = "pikachu@example.com",
            password = "SecurePassword123",
            confirmPassword = "SecurePassword123",
        )
        assertTrue("Registration should succeed for valid inputs", result.isValid)
        assertTrue(result is ValidationResult.Success)
        assertNull(result.errorMessage)
    }

    @Test
    fun `registration fails when username is empty`() {
        val result = AuthValidator.validateRegistration(
            username = " ",
            email = "trainer@example.com",
            password = "Password123",
            confirmPassword = "Password123",
        )
        assertFalse("Registration should fail for empty username", result.isValid)
        assertTrue(result is ValidationResult.Error)
        assertEquals("Username cannot be empty", result.errorMessage)
    }

    @Test
    fun `registration fails when username is whitespace only`() {
        val result = AuthValidator.validateRegistration(
            username = " ",
            email = "trainer@example.com",
            password = "Password123",
            confirmPassword = "Password123",
        )
        assertFalse("Registration should fail for whitespace username", result.isValid)
        assertEquals("Username cannot be empty", result.errorMessage)
    }

    @Test
    fun `registration fails when username is shorter than 3 characters`() {
        val result = AuthValidator.validateRegistration(
            username = "ab",
            email = "trainer@example.com",
            password = "Password123",
            confirmPassword = "Password123",
        )
        assertFalse("Registration should fail for username under 3 chars", result.isValid)
        assertEquals("Username must be at least 3 characters", result.errorMessage)
    }

    @Test
    fun `registration fails when email is empty`() {
        val result = AuthValidator.validateRegistration(
            username = "TrainerAsh",
            email = "",
            password = "Password123",
            confirmPassword = "Password123",
        )
        assertFalse("Registration should fail for empty email", result.isValid)
        assertEquals("Email cannot be empty", result.errorMessage)
    }

    @Test
    fun `registration fails when email format is invalid`() {
        val invalidEmails = listOf("invalidemail", "user@", "@example.com", "user@com", "user.example.com")
        for (email in invalidEmails) {
            val result = AuthValidator.validateRegistration(
                username = "TrainerAsh",
                email = email,
                password = "Password123",
                confirmPassword = "Password123",
            )
            assertFalse("Registration should fail for invalid email format: $email", result.isValid)
            assertEquals("Invalid email address format", result.errorMessage)
        }
    }

    @Test
    fun `registration fails when password is empty`() {
        val result = AuthValidator.validateRegistration(
            username = "TrainerAsh",
            email = "ash@example.com",
            password = "",
            confirmPassword = "",
        )
        assertFalse("Registration should fail for empty password", result.isValid)
        assertEquals("Password cannot be empty", result.errorMessage)
    }

    @Test
    fun `registration fails when password is less than 6 characters`() {
        val result = AuthValidator.validateRegistration(
            username = "TrainerAsh",
            email = "ash@example.com",
            password = "12345",
            confirmPassword = "12345",
        )
        assertFalse("Registration should fail for short password", result.isValid)
        assertEquals("Password must be at least 6 characters", result.errorMessage)
    }

    @Test
    fun `registration fails when confirm password does not match password`() {
        val result = AuthValidator.validateRegistration(
            username = "TrainerAsh",
            email = "ash@example.com",
            password = "Password123",
            confirmPassword = "Password456",
        )
        assertFalse("Registration should fail when passwords do not match", result.isValid)
        assertEquals("Passwords do not match", result.errorMessage)
    }


    // ==========================================
    // LOG IN VALIDATION TESTS
    // ==========================================

    @Test
    fun `login succeeds with valid email and password`() {
        val result = AuthValidator.validateLogin(
            email = "trainer@example.com",
            password = "Password123",
        )
        assertTrue("Login should succeed for valid inputs", result.isValid)
        assertTrue(result is ValidationResult.Success)
        assertNull(result.errorMessage)
    }

    @Test
    fun `login succeeds with uppercase valid email`() {
        val result = AuthValidator.validateLogin(
            email = "TRAINER@EXAMPLE.COM",
            password = "Password123",
        )
        assertTrue("Login should succeed for valid uppercase email", result.isValid)
    }

    @Test
    fun `login fails when email is empty`() {
        val result = AuthValidator.validateLogin(
            email = "",
            password = "Password123",
        )
        assertFalse("Login should fail for empty email", result.isValid)
        assertEquals("Email cannot be empty", result.errorMessage)
    }

    @Test
    fun `login fails when email is whitespace only`() {
        val result = AuthValidator.validateLogin(
            email = "   ",
            password = "Password123",
        )
        assertFalse("Login should fail for whitespace email", result.isValid)
        assertEquals("Email cannot be empty", result.errorMessage)
    }

    @Test
    fun `login fails when email format is invalid`() {
        val invalidEmails = listOf("notAnEmail", "trainer@domain", "plainaddress", "@domain.com")
        for (email in invalidEmails) {
            val result = AuthValidator.validateLogin(
                email = email,
                password = "Password123",
            )
            assertFalse("Login should fail for invalid email format: $email", result.isValid)
            assertEquals("Invalid email address format", result.errorMessage)
        }
    }

    @Test
    fun `login fails when password is empty`() {
        val result = AuthValidator.validateLogin(
            email = "trainer@example.com",
            password = "",
        )
        assertFalse("Login should fail for empty password", result.isValid)
        assertEquals("Password cannot be empty", result.errorMessage)
    }

    // ==========================================
    // INDIVIDUAL FIELD VALIDATION TESTS
    // ==========================================

    @Test
    fun `validateUsername returns success for valid username`() {
        val result = AuthValidator.validateUsername("Red")
        assertTrue(result.isValid)
    }

    @Test
    fun `validateUsername returns error for blank or short username`() {
        assertEquals("Username cannot be empty", AuthValidator.validateUsername("").errorMessage)
        assertEquals("Username must be at least 3 characters", AuthValidator.validateUsername("ab").errorMessage)
    }

    @Test
    fun `validateEmail returns success for valid emails`() {
        assertTrue(AuthValidator.validateEmail("user@test.org").isValid)
        assertTrue(AuthValidator.validateEmail("user.name+tag@sub.example.co.uk").isValid)
    }

    @Test
    fun `validatePassword returns error for short or empty password`() {
        assertEquals("Password cannot be empty", AuthValidator.validatePassword("").errorMessage)
        assertEquals("Password must be at least 6 characters", AuthValidator.validatePassword("123").errorMessage)
    }
}
