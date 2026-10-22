# Implementation Plan: User Registration and Login with Password Hashing

Implement user authentication using Room for local storage and BCrypt for secure password hashing.

## User Review Required

> [!IMPORTANT]
> This implementation uses **local-only** storage (Room). User accounts will not sync across devices. If you need cloud sync, we should consider Firebase Authentication instead.

> [!NOTE]
> For security, passwords will be **hashed** using BCrypt before storage. We use the term "encrypt" loosely here as hashing is the industry standard for passwords (one-way transformation).

## Proposed Changes

### Dependencies & Setup

#### [MODIFY] [libs.versions.toml](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/gradle/libs.versions.toml)
* Add Room and BCrypt versions and libraries.
* Add KSP plugin for Room annotation processing.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/app/build.gradle.kts)
* Apply KSP plugin.
* Add Room and BCrypt dependencies.

---

### Data Layer

#### [NEW] [User.kt](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/app/src/main/java/com/pokemon/explorer/data/db/User.kt)
* Define Room `User` entity with `username` and `passwordHash`.

#### [NEW] [UserDao.kt](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/app/src/main/java/com/pokemon/explorer/data/db/UserDao.kt)
* Interface for database operations: `insertUser`, `getUserByUsername`.

#### [NEW] [AppDatabase.kt](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/app/src/main/java/com/pokemon/explorer/data/db/AppDatabase.kt)
* Room database configuration.

---

### Security & Repository

#### [NEW] [PasswordHasher.kt](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/app/src/main/java/com/pokemon/explorer/security/PasswordHasher.kt)
* Utility for hashing passwords and verifying them against hashes using BCrypt.

#### [NEW] [AuthRepository.kt](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/app/src/main/java/com/pokemon/explorer/data/repository/AuthRepository.kt)
* Logic for registering a new user (check if exists, hash password, save) and logging in (find user, verify hash).

---

### UI & Navigation

#### [MODIFY] [AppState.kt](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/app/src/main/java/com/pokemon/explorer/state/AppState.kt)
* Add `currentUser` state.
* Add `login(username, password)` and `register(username, password)` methods that delegate to `AuthRepository`.
* Add `logout()` method.
* Define `LOGIN` and `REGISTER` screen constants.

#### [NEW] [LoginScreen.kt](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/app/src/main/java/com/pokemon/explorer/ui/screens/LoginScreen.kt)
* UI with username and password fields, and a link to the registration screen.

#### [NEW] [RegisterScreen.kt](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/app/src/main/java/com/pokemon/explorer/ui/screens/RegisterScreen.kt)
* UI for creating a new account.

#### [MODIFY] [AppRoot.kt](file:///C:/Users/Sheketli Mochaki/AndroidStudioProjects/Pok-mon/app/src/main/java/com/pokemon/explorer/ui/AppRoot.kt)
* If no user is logged in, show `LoginScreen` or `RegisterScreen`.

## Verification Plan

### Automated Tests
* Unit tests for `PasswordHasher` to ensure hashing and verification work correctly.
* Unit tests for `AuthRepository` (mocking the DAO) to verify registration and login logic.

### Manual Verification
1. Open the app; it should show the Login screen.
2. Navigate to Register and create a new account.
3. Log in with the new credentials.
4. Verify that the app proceeds to the Explore tab.
5. Log out and try logging in with an incorrect password to verify failure handling.
