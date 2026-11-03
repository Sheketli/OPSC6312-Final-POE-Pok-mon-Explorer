package com.pokemon.explorer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokemon.explorer.data.db.AppDatabase
import com.pokemon.explorer.data.db.User
import com.pokemon.explorer.data.repository.AuthRepository
import com.pokemon.explorer.data.repository.AuthResult
import com.pokemon.explorer.security.PasswordHasher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineLoginSystemTest {

    private lateinit var db: AppDatabase
    private lateinit var authRepository: AuthRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Use an in-memory database for testing so it doesn't affect the real app
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        authRepository = AuthRepository(db.userDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testOfflineLoginBypass() = runTest {
        // 1. Setup an existing user in the local database (simulating a previously logged in user)
        val email = "offline_test@example.com"
        val password = "MySecretPassword123"
        val username = "OfflineTester"

        val user = User(
            username = username,
            email = email,
            passwordHash = PasswordHasher.hashPassword(password)
        )
        db.userDao().insertUser(user)

        // 2. Disable network to simulate offline environment (or rely on Firebase rejecting fake credentials immediately offline)
        // Since we can't easily turn off Wi-Fi in the middle of a test reliably without shell permissions,
        // we can rely on the fact that this fake email/password is NOT registered in Firebase.
        // Therefore, Firebase auth will fail (throwing an exception).
        // The AuthRepository should catch this exception, fall back to RoomDB, find our user, and succeed!

        // 3. Attempt Login
        val result = authRepository.login(email, password)

        // 4. Verify that the login succeeded via the Offline Bypass
        assertTrue(
            "Login should succeed via offline bypass, but got: $result",
            result is AuthResult.Success
        )

        if (result is AuthResult.Success) {
            assertTrue("Username should match", result.user.username == username)
            assertTrue("Email should match", result.user.email == email)
        }
    }
}
