package com.pokemon.explorer.state

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
import kotlinx.coroutines.launch

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
 * Holds navigation stacks, collection and display preferences.
 */
class AppState(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
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

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "caught") {
            val savedCaught = sharedPreferences.getStringSet("caught", null)
            _caught.value = if (savedCaught == null) {
                setOf(25, 1, 6, 7, 4, 5, 152, 249, 250, 384, 143, 59, 130, 131, 9, 3, 65, 68, 76, 94, 149)
            } else {
                savedCaught.mapNotNull { it.toIntOrNull() }.toSet()
            }
        } else if (key == "favourites") {
            val savedFavourites = sharedPreferences.getStringSet("favourites", null)
            _favourites.value = if (savedFavourites == null) {
                setOf(25, 1, 6)
            } else {
                savedFavourites.mapNotNull { it.toIntOrNull() }.toSet()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        com.pokemon.explorer.data.PokeApi.init(application)
        
        val savedUsername = prefs.getString("logged_in_user", null)
        if (savedUsername != null) {
            viewModelScope.launch {
                val database = AppDatabase.getDatabase(application)
                val user = database.userDao().getUserByUsername(savedUsername)
                _currentUser.value = user
                _isAuthChecked.value = true
            }
        } else {
            _isAuthChecked.value = true
        }

        // Load settings
        val savedThemeName = prefs.getString("theme", ThemeSetting.System.name)
        val savedTheme = ThemeSetting.entries.find { it.name == savedThemeName } ?: ThemeSetting.System
        _theme.value = savedTheme
        applyTheme(savedTheme)
        _searchHistoryEnabled.value = prefs.getBoolean("search_history_enabled", true)
        _gyroscopeEnabled.value = prefs.getBoolean("gyroscope_enabled", true)
        _screenDraggingEnabled.value = prefs.getBoolean("screen_dragging_enabled", true)
        _resumeLastViewed.value = prefs.getBoolean("resume_last_viewed", true)
        _notificationsEnabled.value = prefs.getBoolean("notifications_enabled", false)
        _recentSearches.value = prefs.getString("recent_searches_csv", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

        // Load collection
        val savedFavourites = prefs.getStringSet("favourites", null)
        _favourites.value = if (savedFavourites == null) {
            setOf(25, 1, 6)
        } else {
            savedFavourites.mapNotNull { it.toIntOrNull() }.toSet()
        }

        val savedCaught = prefs.getStringSet("caught", null)
        _caught.value = if (savedCaught == null) {
            setOf(25, 1, 6, 7, 4, 5, 152, 249, 250, 384, 143, 59, 130, 131, 9, 3, 65, 68, 76, 94, 149)
        } else {
            savedCaught.mapNotNull { it.toIntOrNull() }.toSet()
        }

        checkAndDownloadIndex(application)
    }

    private fun checkAndDownloadIndex(application: Application) {
        viewModelScope.launch {
            val database = AppDatabase.getDatabase(application)
            val dao = database.pokemonIndexDao()
            
            if (dao.getCount() < 1025) {
                try {
                    val list = com.pokemon.explorer.data.PokeApi.downloadPokedexIndex()
                    val indexList = list.map {
                        PokemonIndex(it.id, it.name, it.types.joinToString(","))
                    }
                    dao.insertAll(indexList)
                } catch (e: Exception) {
                    e.printStackTrace() // offline or failed, handled gracefully
                }
            }
        }
    }

    fun selectTab(tab: TabId) {
        _activeTab.value = tab
        _currentFrame.value = stacks.getValue(tab).last()
    }

    /** Pushes a new screen onto the active tab's stack. */
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

    /** Pops the active tab's stack. Returns true if popped, false if at root. */
    fun goBack(): Boolean {
        val tab = _activeTab.value ?: TabId.Explore
        val stack = stacks.getValue(tab)
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        _currentFrame.value = stack.last()
        return true
    }

    fun toggleFavourite(id: Int) {
        val current = _favourites.value ?: emptySet()
        val updated = current.toggle(id)
        _favourites.value = updated
        prefs.edit().putStringSet("favourites", updated.map { it.toString() }.toSet()).apply()
        syncToFirebase("favourites", updated.toList())
    }

    fun toggleCaught(id: Int) {
        val current = _caught.value ?: emptySet()
        val updated = current.toggle(id)
        _caught.value = updated
        prefs.edit().putStringSet("caught", updated.map { it.toString() }.toSet()).apply()
        syncToFirebase("caught", updated.toList())
    }

    fun addCaught(id: Int) {
        val current = _caught.value ?: emptySet()
        if (!current.contains(id)) {
            val updated = current + id
            _caught.value = updated
            prefs.edit().putStringSet("caught", updated.map { it.toString() }.toSet()).apply()
            syncToFirebase("caught", updated.toList())
        }
    }

    fun resetCollection() {
        _favourites.value = emptySet()
        _caught.value = emptySet()
        prefs.edit().remove("favourites").remove("caught").apply()
        syncToFirebase("favourites", emptyList<Int>())
        syncToFirebase("caught", emptyList<Int>())
    }

    fun applyTheme(value: ThemeSetting) {
        val mode = when (value) {
            ThemeSetting.Light -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeSetting.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeSetting.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun selectTheme(value: ThemeSetting) {
        _theme.value = value
        prefs.edit().putString("theme", value.name).apply()
        applyTheme(value)
        syncToFirebase("theme", value.name)
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
        val normalized = query.trim().lowercase()
        val currentList = _recentSearches.value ?: emptyList()
        val newList = (listOf(normalized) + currentList.filter { it != normalized }).take(10)
        _recentSearches.value = newList
        prefs.edit().putString("recent_searches_csv", newList.joinToString(",")).apply()
        syncToFirebase("recent_searches", newList)
    }

    fun clearSearchHistory() {
        _recentSearches.value = emptyList()
        prefs.edit().remove("recent_searches_csv").apply()
        syncToFirebase("recent_searches", emptyList<String>())
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            when (val result = authRepository.login(email, password)) {
                is AuthResult.Success -> {
                    _currentUser.value = result.user
                    prefs.edit().putString("logged_in_user", result.user.username).apply()
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
                    _currentUser.value = result.user
                    prefs.edit().putString("logged_in_user", result.user.username).apply()
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
                    _currentUser.value = result.user
                    prefs.edit().putString("logged_in_user", result.user.username).apply()
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
                    _currentUser.value = result.user
                    prefs.edit().putString("logged_in_user", result.user.username).apply()
                    _authSuccess.value = "Username updated successfully"
                }
                is AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        prefs.edit().remove("logged_in_user").apply()
        _activeTab.value = TabId.Explore
        stacks[TabId.Explore] = mutableListOf(NavFrame(Screen.HOME))
        stacks[TabId.Collection] = mutableListOf(NavFrame(Screen.COLLECTION))
        stacks[TabId.Compare] = mutableListOf(NavFrame(Screen.COMPARE))
        stacks[TabId.Guide] = mutableListOf(NavFrame(Screen.TYPE_GUIDE))
        _currentFrame.value = stacks.getValue(TabId.Explore).last()
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
            syncToFirebase("notes", "") // trigger SyncWorker
        }
    }

    suspend fun getPokemonNote(pokemonId: Int): PokemonNote? {
        val user = _currentUser.value ?: return null
        val database = AppDatabase.getDatabase(getApplication())
        return database.pokemonNoteDao().getNote(user.username, pokemonId)
    }

    private fun syncToFirebase(key: String, value: Any) {
        viewModelScope.launch {
            val database = AppDatabase.getDatabase(getApplication())
            database.syncActionDao().insertAction(SyncAction(type = key, payload = ""))
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
                
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
                
            WorkManager.getInstance(getApplication()).enqueue(syncRequest)
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
