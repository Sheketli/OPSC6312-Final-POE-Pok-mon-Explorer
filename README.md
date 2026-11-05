# Pokémon Explorer 🔴⚡

[![Android CI](https://github.com/Sheketli/OPSC6312-Final-POE-Pok-mon-Explorer/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Sheketli/OPSC6312-Final-POE-Pok-mon-Explorer/actions/workflows/android-ci.yml)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.20-blue.svg)](https://kotlinlang.org/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34-orange.svg)](https://developer.android.com/about/versions/14)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

An immersive, game-authentic Android Pokédex companion application. **Pokémon Explorer** combines real-time data from [PokéAPI](https://pokeapi.co), Augmented Reality (AR) camera encounters, peer-to-peer (P2P) trading, live messaging, and offline-first data synchronization into a trading-card-inspired mobile interface.

---

## 👥 Application Ownership

This project was designed, developed, and maintained by:

| Name | Role | Contact |
| :--- | :--- | :--- |
| **Sheketli Mochaki** | Lead Android Developer & Systems Architect | [GitHub](https://github.com/Sheketli) |
| **Seabelo Motloung** | UI/UX Designer & Frontend Engineer | Team Member |
| **Ramahlape Malwa** | Security & Database Specialist | Team Member |
| **Mothoka Malatji** | Backend Integration & Firebase Engineer | Team Member |
| **Engetani Nkuna** | Quality Assurance & Testing Lead | Team Member |

---

## 🎯 Purpose & Scope

The primary objective of **Pokémon Explorer** is to bridge the gap between traditional digital Pokédex tools and interactive mobile game experiences. Standard Pokédex apps offer static reference tables; **Pokémon Explorer** transforms data lookup into a kinetic, gamified experience inspired by the Pokémon trading card game (TCG) and in-game summary screens from *Pokémon Legends: Arceus* and *Pokémon Scarlet & Violet*.

### Key Problems Solved:
1. **Offline Accessibility**: Full functionality even without active internet access. Uses local Room database caching and an offline action queue to preserve collection updates, notes, and searches.
2. **Interactive AR Exploration**: Moves beyond static images by placing 3D-tracked Pokémon in physical space using CameraX and hardware sensor fusion.
3. **Social Trainer Ecosystem**: Features built-in trainer profiles, friend requests, real-time messaging, and atomic P2P Pokémon trading.
4. **Predictive Battle Analytics**: Calculates type-effectiveness multipliers, stat totals, and damage outputs to predict battle outcomes between any two Pokémon.

---

## 🚀 Release Notes — Evolution from Prototype

This production release represents a major evolution from the initial prototype. The original prototype was a web-based proof-of-concept; this version is a native, feature-complete Android application built with modern Android architecture guidelines.

### 🌟 Innovative Features Introduced in Final Version

#### 1. 📷 CameraX Augmented Reality (AR) Encounters
- **Hardware Sensor Fusion**: Uses the device's `Rotation Vector Sensor` (`TYPE_GAME_ROTATION_VECTOR`) with low-pass filter smoothing to position Pokémon in 3D world space.
- **Touch-Drag Fallback**: Pseudo-AR touch dragging for devices without a hardware gyroscope.
- **Crosshair Reticle Lock-On**: 100ms interval hit-testing math detecting when the viewfinder crosshair centers on a Pokémon in 3D space, triggering real-time scan telemetry.
- **Interactive Capture**: Instant catch mechanics adding Pokémon directly to the trainer's local and cloud collections.

#### 2. 🔄 Real-time P2P Trading Portal
- **Atomic Trade Locks**: Prevents double-trading by locking reserved Pokémon under `trade_locks` in Firebase Realtime Database.
- **Negotiation Workflow**: Submit trade proposals, view proposal history, receive counter-offers, and confirm or decline exchanges atomically using Firebase Transactions.

#### 3. 💬 Real-time Messaging & Friends Portal
- **Trainer Search & Connections**: Look up trainers by username and send/accept/decline friend requests.
- **Live Chat**: Real-time messaging backed by Firebase Realtime Database and cached locally in Room (`MessageDao`).
- **Notification Badging**: Instant alert notifications for incoming friend requests, chat messages, and trade offers.

#### 4. ⏰ WorkManager Rare Spawn System
- **Periodic Background Worker**: `RareSpawnWorker` periodically generates rare or legendary Pokémon spawns (e.g. Mewtwo, Dragonite, Charizard).
- **High-Priority Notifications**: Dispatches local notifications with custom deep-link intents leading straight into the AR camera.
- **Countdown Expiry**: Rare spawns feature a 5-minute expiry timer after which the Pokémon flees.

#### 5. ⚡ GraphQL Pokédex Index Download
- **High-Speed GraphQL Query**: Connects to `https://beta.pokeapi.co/graphql/v1beta` to download name and multi-type data for all 1025 Pokémon in a single HTTP request.
- **Instant Search**: Seeds the local Room database (`PokemonIndexDao`) for instant, debounced search-as-you-type.

#### 6. 📡 Offline-First Synchronization Architecture
- **Sync Action Queue**: Offline user actions (catching Pokémon, favoriting, updating settings, creating notes) are logged in Room (`SyncActionDao`).
- **WorkManager Auto-Sync**: Enqueues `SyncWorker` with network constraints (`NetworkType.CONNECTED`) to automatically push queued actions to Firebase Realtime Database and Cloud Firestore when online.

#### 7. 🔒 Security & Authentication Suite
- **BCrypt Password Hashing**: Implements salted key derivation via `PasswordHasher` for secure local storage.
- **Offline Login Fallback**: Verifies credentials against local Room database (`UserDao`) when offline.
- **Google Sign-In**: Native Google OAuth authentication integrated with Firebase Auth.

#### 8. 🌍 Multilingual Localization
- Full app localization support for 5 languages:
  - 🇬🇧 English (`en`)
  - 🇿🇦 Sotho (`st`)
  - 🇿🇦 Tswana (`tn`)
  - 🇿🇦 Xhosa (`xh`)
  - 🇿🇦 Zulu (`zu`)

---

## 🎨 Design Considerations & System Architecture

### 1. UI/UX Design Philosophy
- **Game-UI Aesthetic**: Trading-card proportions, holographic foil sheens on rare/shiny Pokémon, chunky borders, and custom Poké Ball motifs.
- **Dynamic Type Gradients**: Card backgrounds dynamically adapt to the Pokémon's primary type (e.g. Fire = Orange/Red gradient, Water = Blue gradient, Grass = Green gradient).
- **Single-Activity Architecture**: Built around a single activity (`MainActivity`) hosting Fragment screens (`HomeFragment`, `DetailFragment`, `CompareFragment`, `CollectionFragment`, `TypeGuideFragment`, `SearchFragment`, `SettingsFragment`, `AccountManagementFragment`).
- **Custom Navigation Stack**: `AppState` ViewModel maintains per-tab backstacks so navigation state and scroll positions are preserved across bottom-navigation switches.

### 2. Dual-Layer Persistence
- **L1 In-Memory Cache**: `ConcurrentHashMap` inside `PokeApi` for process-lifetime response caching.
- **L2 Persistent Disk Cache**: Room SQLite database (`CachedPokemonDao`, `UserDao`, `FriendshipDao`, `MessageDao`, `TradeRequestDao`, `PokemonNoteDao`, `SyncActionDao`).
- **Encrypted Shared Preferences**: Handles auth tokens and user preferences (`auth_prefs`).

### 3. Concurrency & State Management
- Built using **Kotlin Coroutines** (`viewModelScope`, `Dispatchers.IO`), `StateFlow`, and `LiveData`.
- Unidirectional Data Flow (UDF) guarantees UI components react predictably to state changes.

---

## ⚙️ Utilisation of GitHub & GitHub Actions

### GitHub Version Control & Workflow
- **Branching Strategy**:
  - `main`: Protected production branch containing release-ready code.
  - Feature branches (`feature/ar-camera`, `feature/p2p-trading`, `feature/offline-sync`): Isolated branches for developing specific modules.
- **Pull Request Code Reviews**: All changes undergo automated CI validation and peer review before merging into `main`.

### Automated CI/CD Pipeline (`.github/workflows/android-ci.yml`)
The project utilizes GitHub Actions for automated continuous integration on every `push` to any branch and every `pull_request` targeting `main`.

```yaml
name: Android CI
on:
  push:
    branches: ['**']
  pull_request:
    branches: [main]
  workflow_dispatch:
```

#### Pipeline Steps:
1. **Repository Checkout**: Pulls source code using `actions/checkout@v7`.
2. **Environment Configuration**: Configures JDK 17 via `actions/setup-java@v6` (Temurin) and Gradle 9.7.1 via `gradle/actions/setup-gradle@v6`.
3. **Automated Compilation**: Runs `gradle assembleDebug` to build the debug APK.
4. **Automated Unit Testing**: Runs `gradle testDebugUnitTest` to execute all unit tests.
5. **Automated GitHub Commit Reporting**: A custom `actions/github-script@v9` step parses build logs, counts XML test suite results, checks compiler errors/warnings, calculates the debug APK binary size, and publishes a structured summary comment directly to the commit.
6. **Artifact Storage**: Automatically uploads the generated `app-debug.apk` binary via `actions/upload-artifact@v7` for immediate testing and deployment.

---

## 📱 Application Screenshots & Visual Walkthrough

| App Icon & Launch | Splash Screen | Splash Logo & Tagline |
| :---: | :---: | :---: |
| ![App Icon](screenshots/01_app_icon.png) | ![Splash Logo](screenshots/02_splash.png) | ![Splash Tagline](screenshots/03_splash_tagline.png) |

| Home / Explore Screen | Favourites Grid | Caught Collection Grid |
| :---: | :---: | :---: |
| ![Explore Screen](screenshots/04_explore.png) | ![Favourites Grid](screenshots/05_collection_favourites.png) | ![Caught Collection](screenshots/06_collection_caught.png) |

| AR Camera Viewfinder | Compare Arena | Type Matchup Guide |
| :---: | :---: | :---: |
| ![AR Camera](screenshots/07_ar_camera.png) | ![Compare Arena](screenshots/08_compare.png) | ![Type Guide](screenshots/09_type_guide.png) |

| Debounced Search | Pokémon Details | Friends Portal |
| :---: | :---: | :---: |
| ![Search Screen](screenshots/10_search.png) | ![Detail Screen](screenshots/11_detail.png) | ![Friends Portal](screenshots/12_friends_portal.png) |

| Real-time Live Chat | P2P Trading Portal | Settings & Preferences |
| :---: | :---: | :---: |
| ![Real-time Chat](screenshots/13_chat.png) | ![Trading Portal](screenshots/14_trade_portal.png) | ![Settings Screen](screenshots/15_settings.png) |

| AR Settings & Account | Manage Account | User Login Screen |
| :---: | :---: | :---: |
| ![Settings Scrolled](screenshots/16_settings_account.png) | ![Manage Account](screenshots/17_manage_account.png) | ![Login Screen](screenshots/18_login.png) |

---

## 🛠️ Building & Running the Project

### Prerequisites
- **Android Studio**: Ladybug (2024.2) or newer
- **JDK**: Java Development Kit 17
- **Android SDK**: `compileSdk 34`, `minSdk 24`, `targetSdk 34`

### Build via Command Line
```bash
# Clone the repository
git clone https://github.com/Sheketli/OPSC6312-Final-POE-Pok-mon-Explorer.git
cd OPSC6312-Final-POE-Pok-mon-Explorer

# Compile the Debug APK
gradle assembleDebug

# Run Unit Tests
gradle testDebugUnitTest
```

---

## 📜 Attribution & Legal Disclaimer

- **Data Source**: Pokémon statistics, abilities, moves, and flavor text provided by [PokéAPI](https://pokeapi.co).
- **Artwork & Sprites**: Official Pokémon artwork provided by the [PokeAPI Sprites](https://github.com/PokeAPI/sprites) repository.
- **Trademarks**: Pokémon and Pokémon character names are trademarks of Nintendo, Creatures Inc., and GAME FREAK inc. This software is an educational, non-commercial fan project and is not affiliated with or endorsed by Nintendo or The Pokémon Company.
