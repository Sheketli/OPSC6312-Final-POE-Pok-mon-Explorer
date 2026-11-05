package com.pokemon.explorer.state

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.pokemon.explorer.data.db.AppDatabase
import com.pokemon.explorer.data.db.PokemonIndex
import com.pokemon.explorer.data.db.PokemonNote
import com.pokemon.explorer.data.db.SyncAction
import com.pokemon.explorer.data.db.User
import com.pokemon.explorer.data.repository.AuthRepository
import com.pokemon.explorer.data.repository.AuthResult
import com.pokemon.explorer.data.sync.SyncWorker
import com.pokemon.explorer.data.CollectionPreferences
import com.pokemon.explorer.data.ThemePreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** The four bottom-navigation destinations. */
enum class TabId(val label: String, val initialScreen: String) {
    Explore("Explore", Screen.HOME),
    Collection("Collection", Screen.COLLECTION),
    Compare("Compare", Screen.COMPARE),
    Guide("Guide", Screen.TYPE_GUIDE),
}

/** Screen keys used by [AppState.navigate]. */
object Screen {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DETAIL = "detail"
    const val COMPARE = "compare"
    const val COLLECTION = "collection"
    const val TYPE_GUIDE = "typeGuide"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"
    const val LOGIN = "login"
    const val REGISTER = "register"

    /** Screens that hide the bottom navigation bar. */
    val HIDDEN_NAV = setOf(SETTINGS, ACCOUNT, DETAIL, SEARCH, LOGIN, REGISTER)
}

/** One entry on a tab's back stack. */
data class NavFrame(
    val screen: String,
    val id: Int? = null,
    val idA: Int? = null,
    val filterType: String? = null,
)

enum class ThemeSetting(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * Holds navigation stacks, collection and display preferences scoped per unique user account.
 */
class AppState(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val collectionPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
        val key = activeUserKey
        if (key != null && changed == "caught_$key") {
            _caught.postValue(CollectionPreferences.caught(prefs, key))
        } else if (key != null && changed == "theme_$key") {
            _theme.postValue(ThemePreferences.read(prefs, key))
        }
    }
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private val authRepository: AuthRepository by lazy {
        val database = AppDatabase.getDatabase(application)
        AuthRepository(database.userDao())
    }

    private val _currentUser = MutableLiveData<User?>(null)
    val currentUser: LiveData<User?> = _currentUser

    private val _authError = MutableLiveData<String?>(null)
    val authError: LiveData<String?> = _authError
    
    private val _authSuccess = MutableLiveData<String?>(null)
    val authSuccess: LiveData<String?> = _authSuccess

    private val _isAuthChecked = MutableLiveData(false)
    val isAuthChecked: LiveData<Boolean> = _isAuthChecked

    private val _activeTab = MutableLiveData(TabId.Explore)
    val activeTab: LiveData<TabId> = _activeTab

    private val _favourites = MutableLiveData<Set<Int>>(emptySet())
    val favourites: LiveData<Set<Int>> = _favourites

    private val _caught = MutableLiveData<Set<Int>>(emptySet())
    val caught: LiveData<Set<Int>> = _caught

    private val _theme = MutableLiveData(ThemeSetting.System)
    val theme: LiveData<ThemeSetting> = _theme

    private val _searchHistoryEnabled = MutableLiveData(true)
    val searchHistoryEnabled: LiveData<Boolean> = _searchHistoryEnabled
    
    private val _gyroscopeEnabled = MutableLiveData(true)
    val gyroscopeEnabled: LiveData<Boolean> = _gyroscopeEnabled
    
    private val _screenDraggingEnabled = MutableLiveData(true)
    val screenDraggingEnabled: LiveData<Boolean> = _screenDraggingEnabled

    private val _recentSearches = MutableLiveData<List<String>>(emptyList())
    val recentSearches: LiveData<List<String>> = _recentSearches

    private val _resumeLastViewed = MutableLiveData(true)
    val resumeLastViewed: LiveData<Boolean> = _resumeLastViewed

    private val _notificationsEnabled = MutableLiveData(false)
    val notificationsEnabled: LiveData<Boolean> = _notificationsEnabled

    private val stacks = mutableMapOf(
        TabId.Explore to mutableListOf(NavFrame(Screen.HOME)),
        TabId.Collection to mutableListOf(NavFrame(Screen.COLLECTION)),
        TabId.Compare to mutableListOf(NavFrame(Screen.COMPARE)),
        TabId.Guide to mutableListOf(NavFrame(Screen.TYPE_GUIDE)),
    )

    private val _currentFrame = MutableLiveData(stacks.getValue(TabId.Explore).last())
    val currentFrame: LiveData<NavFrame> = _currentFrame

    val canGoBack: Boolean
        get() = (stacks[_activeTab.value ?: TabId.Explore]?.size ?: 0) > 1

    var isSplashFinished: Boolean = false

    val activeUserKey: String?
        get() = _currentUser.value?.let { user -> user.userId.ifBlank { user.username } }

    init {
        prefs.registerOnSharedPreferenceChangeListener(collectionPreferenceListener)
        com.pokemon.explorer.data.PokeApi.init(application)
        
        val savedUsername = prefs.getString("logged_in_user", null)
        val savedUserId = prefs.getString("logged_in_user_id", null)
        if (savedUsername != null) {
            viewModelScope.launch {
                val database = AppDatabase.getDatabase(application)
                var user = database.userDao().getUserByUsername(savedUsername)
                if (user == null && savedUserId != null) {
                    user = database.userDao().getUserByUserId(savedUserId)
                }
                if (user == null) {
                    val uid = auth.currentUser?.uid ?: savedUserId ?: savedUsername
                    user = User(username = savedUsername, userId = uid, email = auth.currentUser?.email ?: "", passwordHash = "")
                }
                setCurrentUser(user)
                _isAuthChecked.value = true
            }
        } else {
            _isAuthChecked.value = true
        }

        checkAndDownloadIndex(application)
    }

    private fun setCurrentUser(user: User?) {
        _currentUser.value = user
        if (user != null) {
            val uid = user.userId.ifBlank { auth.currentUser?.uid ?: user.username }
            prefs.edit()
                .putString("logged_in_user", user.username)
                .putString("logged_in_user_id", uid)
                .apply()
            loadUserScopedData(user)
        } else {
            _favourites.value = emptySet()
            _caught.value = emptySet()
            _recentSearches.value = emptyList()
        }
    }

    private fun loadUserScopedData(user: User) {
        val userKey = user.userId.ifBlank { user.username }

        // Load caught
        val savedCaught = prefs.getStringSet("caught_$userKey", null)
        if (savedCaught != null) {
            _caught.value = savedCaught.mapNotNull { it.toIntOrNull() }.toSet()
        } else {
            _caught.value = setOf(25, 1, 6, 7, 4)
        }

        // Load favourites
        val savedFavourites = prefs.getStringSet("favourites_$userKey", null)
        if (savedFavourites != null) {
            _favourites.value = savedFavourites.mapNotNull { it.toIntOrNull() }.toSet()
        } else {
            _favourites.value = setOf(25, 1, 6)
        }

        // Load theme
        val savedThemeName = prefs.getString("theme_$userKey", ThemeSetting.System.name)
        val savedTheme = ThemeSetting.entries.find { it.name == savedThemeName } ?: ThemeSetting.System
        _theme.value = savedTheme

        // Load recent searches
        _recentSearches.value = prefs.getString("recent_searches_csv_$userKey", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

        // Sync from Firebase if online
        viewModelScope.launch {
            try {
                var remoteCaughtList = emptyList<Int>()
                var remoteRemovedCaught = emptySet<Int>()
                var remoteAppliedTrades = emptySet<String>()
                var remoteFavsList = emptyList<Int>()
                var remoteSearchesList = emptyList<String>()

                val snapshot = db.child("users").child(userKey).get().await()
                if (snapshot.exists()) {
                    remoteCaughtList = snapshot.child("caught").children.mapNotNull { it.getValue(Long::class.java)?.toInt() }
                    remoteRemovedCaught = snapshot.child("caught_removed").children.mapNotNull { it.getValue(String::class.java)?.toIntOrNull() }.toSet()
                    remoteAppliedTrades = snapshot.child("applied_trades").children.mapNotNull { it.getValue(String::class.java) }.toSet()
                    remoteFavsList = snapshot.child("favourites").children.mapNotNull { it.getValue(Long::class.java)?.toInt() }
                    remoteSearchesList = snapshot.child("recent_searches").children.mapNotNull { it.getValue(String::class.java) }

                    snapshot.child("search_history_enabled").getValue(Boolean::class.java)?.let {
                        _searchHistoryEnabled.value = it
                        prefs.edit().putBoolean("search_history_enabled", it).apply()
                    }
                    snapshot.child("gyroscope_enabled").getValue(Boolean::class.java)?.let {
                        _gyroscopeEnabled.value = it
                        prefs.edit().putBoolean("gyroscope_enabled", it).apply()
                    }
                    snapshot.child("screen_dragging_enabled").getValue(Boolean::class.java)?.let {
                        _screenDraggingEnabled.value = it
                        prefs.edit().putBoolean("screen_dragging_enabled", it).apply()
                    }
                    snapshot.child("resume_last_viewed").getValue(Boolean::class.java)?.let {
                        _resumeLastViewed.value = it
                        prefs.edit().putBoolean("resume_last_viewed", it).apply()
                    }
                    snapshot.child("notifications_enabled").getValue(Boolean::class.java)?.let {
                        _notificationsEnabled.value = it
                        prefs.edit().putBoolean("notifications_enabled", it).apply()
                    }

                    // Restore Pokémon Notes
                    val notesSnap = snapshot.child("notes")
                    if (notesSnap.exists()) {
                        val roomDb = AppDatabase.getDatabase(getApplication())
                        for (child in notesSnap.children) {
                            val pokeId = child.key?.toIntOrNull()
                            val nickname = child.child("nickname").getValue(String::class.java) ?: ""
                            val note = child.child("note").getValue(String::class.java) ?: ""
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                            if (pokeId != null) {
                                roomDb.pokemonNoteDao().insertNote(
                                    PokemonNote(
                                        username = user.username,
                                        userId = userKey,
                                        pokemonId = pokeId,
                                        nickname = nickname,
                                        note = note,
                                        timestamp = timestamp
                                    )
                                )
                            }
                        }
                    }
                }

                // Fallback to Firestore if RTDB lists are empty
                if (remoteCaughtList.isEmpty() || remoteFavsList.isEmpty()) {
                    try {
                        val firestoreDoc = FirebaseFirestore.getInstance().collection("users").document(userKey).get().await()
                        if (firestoreDoc.exists()) {
                            if (remoteCaughtList.isEmpty()) {
                                remoteCaughtList = (firestoreDoc.get("caught") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() ?: (it as? String)?.toIntOrNull() } ?: emptyList()
                            }
                            if (remoteFavsList.isEmpty()) {
                                remoteFavsList = (firestoreDoc.get("favourites") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() ?: (it as? String)?.toIntOrNull() } ?: emptyList()
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (remoteCaughtList.isNotEmpty()) {
                    val merged = CollectionPreferences.mergeRemote(
                        prefs, userKey, remoteCaughtList.toSet(), remoteRemovedCaught, remoteAppliedTrades
                    )
                    if (activeUserKey == userKey) _caught.value = merged
                }

                if (remoteFavsList.isNotEmpty()) {
                    val merged = (_favourites.value ?: emptySet()) + remoteFavsList
                    _favourites.value = merged
                    prefs.edit().putStringSet("favourites_$userKey", merged.map { it.toString() }.toSet()).apply()
                }

                if (remoteSearchesList.isNotEmpty()) {
                    _recentSearches.value = remoteSearchesList
                    prefs.edit().putString("recent_searches_csv_$userKey", remoteSearchesList.joinToString(",")).apply()
                }
            } catch (_: Exception) {
                // Ignore offline sync error
            }
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    private fun checkAndDownloadIndex(application: Application) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val database = AppDatabase.getDatabase(application)
            val dao = database.pokemonIndexDao()

            // Check if existing items in DB have missing types and populate them from fallback map
            if (dao.getEmptyTypesCount() > 0) {
                val emptyItems = dao.getEmptyTypes()
                val updated = emptyItems.mapNotNull { item ->
                    val fallbackTypes = com.pokemon.explorer.data.getFallbackTypes(item.id)
                    if (fallbackTypes.isNotEmpty()) {
                        item.copy(typesCsv = fallbackTypes.joinToString(","))
                    } else null
                }
                if (updated.isNotEmpty()) {
                    dao.insertAll(updated)
                }
            }
            
            if (dao.getCount() < 20) {
                // Instantly seed common Pokémon so search-as-you-type works immediately
                val seedList = listOf(
                    PokemonIndex(1, "bulbasaur", "grass,poison"),
                    PokemonIndex(2, "ivysaur", "grass,poison"),
                    PokemonIndex(3, "venusaur", "grass,poison"),
                    PokemonIndex(4, "charmander", "fire"),
                    PokemonIndex(5, "charmeleon", "fire"),
                    PokemonIndex(6, "charizard", "fire,flying"),
                    PokemonIndex(7, "squirtle", "water"),
                    PokemonIndex(8, "wartortle", "water"),
                    PokemonIndex(9, "blastoise", "water"),
                    PokemonIndex(10, "caterpie", "bug"),
                    PokemonIndex(25, "pikachu", "electric"),
                    PokemonIndex(26, "raichu", "electric"),
                    PokemonIndex(133, "eevee", "normal"),
                    PokemonIndex(136, "flareon", "fire"),
                    PokemonIndex(143, "snorlax", "normal"),
                    PokemonIndex(150, "mewtwo", "psychic"),
                    PokemonIndex(151, "mew", "psychic"),
                    PokemonIndex(94, "gengar", "ghost,poison"),
                    PokemonIndex(448, "lucario", "fighting,steel"),
                    PokemonIndex(445, "garchomp", "dragon,ground"),
                    PokemonIndex(149, "dragonite", "dragon,flying")
                )
                dao.insertAll(seedList)
            }

            if (dao.getCount() < 1025 || dao.getEmptyTypesCount() > 100) {
                try {
                    val list = com.pokemon.explorer.data.PokeApi.downloadPokedexIndex()
                    if (list.isNotEmpty()) {
                        val indexList = list.map {
                            val types = if (it.types.isEmpty()) com.pokemon.explorer.data.getFallbackTypes(it.id) else it.types
                            PokemonIndex(it.id, it.name, types.joinToString(","))
                        }
                        dao.insertAll(indexList)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun selectTab(tab: TabId) {
        _activeTab.value = tab
        _currentFrame.value = stacks.getValue(tab).last()
    }

    fun navigate(
        screen: String,
        id: Int? = null,
        idA: Int? = null,
        filterType: String? = null,
    ) {
        val frame = NavFrame(screen = screen, id = id, idA = idA, filterType = filterType)
        val tab = _activeTab.value ?: TabId.Explore
        stacks.getValue(tab).add(frame)
        _currentFrame.value = frame
    }

    fun goBack(): Boolean {
        val tab = _activeTab.value ?: TabId.Explore
        val stack = stacks.getValue(tab)
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        _currentFrame.value = stack.last()
        return true
    }

    fun toggleFavourite(id: Int) {
        val key = activeUserKey ?: return
        val current = _favourites.value ?: emptySet()
        val updated = current.toggle(id)
        _favourites.value = updated
        prefs.edit().putStringSet("favourites_$key", updated.map { it.toString() }.toSet()).apply()
        syncToFirebase("favourites", updated.toList())
    }

    fun toggleCaught(id: Int) {
        val key = activeUserKey ?: return
        if (id in CollectionPreferences.reserved(prefs, key)) {
            Toast.makeText(getApplication(), "Complete or cancel the trade reserving this Pokémon first.", Toast.LENGTH_LONG).show()
            return
        }
        val current = CollectionPreferences.caught(prefs, key)
        val updated = current.toggle(id)
        _caught.value = updated
        CollectionPreferences.update(prefs, key, updated)
        syncToFirebase("caught", updated.toList())
    }

    fun addCaught(id: Int) {
        val key = activeUserKey ?: return
        val current = CollectionPreferences.caught(prefs, key)
        if (!current.contains(id)) {
            val updated = current + id
            _caught.value = updated
            CollectionPreferences.update(prefs, key, updated)
            syncToFirebase("caught", updated.toList())
        }
    }

    fun resetCollection() {
        val key = activeUserKey ?: return
        if (CollectionPreferences.reserved(prefs, key).isNotEmpty()) {
            Toast.makeText(getApplication(), "Complete or cancel pending trades before resetting your collection.", Toast.LENGTH_LONG).show()
            return
        }
        _favourites.value = emptySet()
        _caught.value = emptySet()
        prefs.edit().remove("favourites_$key").apply()
        CollectionPreferences.update(prefs, key, emptySet())
        syncToFirebase("favourites", emptyList<Int>())
        syncToFirebase("caught", emptyList<Int>())
    }

    fun selectTheme(value: ThemeSetting) {
        _theme.value = value
        getApplication<com.pokemon.explorer.PokemonApplication>().themeController.select(activeUserKey, value)
    }

    fun updateSearchHistoryEnabled(enabled: Boolean) {
        _searchHistoryEnabled.value = enabled
        prefs.edit().putBoolean("search_history_enabled", enabled).apply()
        syncToFirebase("search_history_enabled", enabled)
    }
    
    fun updateGyroscopeEnabled(enabled: Boolean) {
        _gyroscopeEnabled.value = enabled
        prefs.edit().putBoolean("gyroscope_enabled", enabled).apply()
        syncToFirebase("gyroscope_enabled", enabled)
    }

    fun updateScreenDraggingEnabled(enabled: Boolean) {
        _screenDraggingEnabled.value = enabled
        prefs.edit().putBoolean("screen_dragging_enabled", enabled).apply()
        syncToFirebase("screen_dragging_enabled", enabled)
    }

    fun updateResumeLastViewed(enabled: Boolean) {
        _resumeLastViewed.value = enabled
        prefs.edit().putBoolean("resume_last_viewed", enabled).apply()
        syncToFirebase("resume_last_viewed", enabled)
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        syncToFirebase("notifications_enabled", enabled)
    }

    fun addSearchToHistory(query: String) {
        if (_searchHistoryEnabled.value != true || query.isBlank()) return
        val key = activeUserKey ?: return
        val normalized = query.trim().lowercase()
        val currentList = _recentSearches.value ?: emptyList()
        val newList = (listOf(normalized) + currentList.filter { it != normalized }).take(10)
        _recentSearches.value = newList
        prefs.edit().putString("recent_searches_csv_$key", newList.joinToString(",")).apply()
        syncToFirebase("recent_searches", newList)
    }

    fun clearSearchHistory() {
        val key = activeUserKey ?: return
        _recentSearches.value = emptyList()
        prefs.edit().remove("recent_searches_csv_$key").apply()
        syncToFirebase("recent_searches", emptyList<String>())
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            when (val result = authRepository.login(email, password)) {
                is AuthResult.Success -> {
                    setCurrentUser(result.user)
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            when (val result = authRepository.register(username, email, password)) {
                is AuthResult.Success -> {
                    setCurrentUser(result.user)
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun setAuthError(error: String?) {
        _authError.value = error
    }

    fun googleSignIn(idToken: String) {
        viewModelScope.launch {
            _authError.value = null
            when (val result = authRepository.googleSignIn(idToken)) {
                is AuthResult.Success -> {
                    setCurrentUser(result.user)
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }
    
    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            _authError.value = null
            _authSuccess.value = null
            when (val result = authRepository.updatePassword(newPassword)) {
                is AuthResult.Success -> {
                    _authSuccess.value = "Password updated successfully"
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }
    
    fun updateUsername(newUsername: String) {
        viewModelScope.launch {
            _authError.value = null
            _authSuccess.value = null
            when (val result = authRepository.updateUsername(newUsername)) {
                is AuthResult.Success -> {
                    val updatedUser = result.user
                    setCurrentUser(updatedUser)
                    _authSuccess.value = "Username updated successfully"
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun deleteAccount() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            _authError.value = null
            _authSuccess.value = null
            when (val result = authRepository.deleteAccount()) {
                is AuthResult.Success -> {
                    val database = AppDatabase.getDatabase(getApplication())
                    database.pokemonNoteDao().deleteAllNotesForUser(user.username, user.userId)

                    val key = user.userId.ifBlank { user.username }
                    prefs.edit()
                        .remove("caught_$key")
                        .remove("caught_removed_$key")
                        .remove("applied_trades_$key")
                        .remove("trade_reserved_$key")
                        .remove("favourites_$key")
                        .remove("theme_$key")
                        .remove("theme_pending_$key")
                        .remove("recent_searches_csv_$key")
                        .remove("logged_in_user")
                        .remove("logged_in_user_id")
                        .apply()

                    logout()
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun logout() {
        prefs.edit().remove("logged_in_user").remove("logged_in_user_id").apply()
        setCurrentUser(null)
        _activeTab.value = TabId.Explore
        stacks[TabId.Explore] = mutableListOf(NavFrame(Screen.HOME))
        stacks[TabId.Collection] = mutableListOf(NavFrame(Screen.COLLECTION))
        stacks[TabId.Compare] = mutableListOf(NavFrame(Screen.COMPARE))
        stacks[TabId.Guide] = mutableListOf(NavFrame(Screen.TYPE_GUIDE))
        _currentFrame.value = stacks.getValue(TabId.Explore).last()
    }

    fun catchPokemon(id: Int) {
        val user = _currentUser.value ?: return
        val key = user.userId.ifBlank { user.username }
        val currentCaught = CollectionPreferences.caught(prefs, key)
        val newCaught = currentCaught + id
        
        _caught.value = newCaught
        CollectionPreferences.update(prefs, key, newCaught)
        
        val currentFavourites = _favourites.value ?: emptySet()
        if (!currentFavourites.contains(id)) {
            val newFavourites = currentFavourites + id
            _favourites.value = newFavourites
            prefs.edit().putStringSet("favourites_$key", newFavourites.map { it.toString() }.toSet()).apply()
        }
        syncToFirebase("caught", newCaught.toList())
    }

    private fun Set<Int>.toggle(id: Int): Set<Int> =
        if (contains(id)) this - id else this + id

    fun savePokemonNote(pokemonId: Int, nickname: String, note: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val database = AppDatabase.getDatabase(getApplication())
            if (nickname.isBlank() && note.isBlank()) {
                database.pokemonNoteDao().deleteNote(user.username, pokemonId)
            } else {
                database.pokemonNoteDao().insertNote(
                    PokemonNote(
                        username = user.username,
                        pokemonId = pokemonId,
                        nickname = nickname,
                        note = note
                    )
                )
            }
            syncToFirebase("notes", "")
        }
    }

    suspend fun getPokemonNote(pokemonId: Int): PokemonNote? {
        val user = _currentUser.value ?: return null
        val database = AppDatabase.getDatabase(getApplication())
        return database.pokemonNoteDao().getNote(user.username, pokemonId)
    }

    private fun syncToFirebase(key: String, value: Any) {
        val owner = activeUserKey.orEmpty()
        viewModelScope.launch {
            val database = AppDatabase.getDatabase(getApplication())
            database.syncActionDao().insertAction(SyncAction(type = key, payload = if (key == "caught") owner else ""))
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
                
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
                
            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "user-data-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, syncRequest
            )
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(collectionPreferenceListener)
        super.onCleared()
    }
}
