package com.pokemon.explorer.state

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pokemon.explorer.data.db.AppDatabase
import com.pokemon.explorer.data.db.User
import com.pokemon.explorer.data.repository.AuthRepository
import com.pokemon.explorer.data.repository.AuthResult
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
    const val LOGIN = "login"
    const val REGISTER = "register"

    /** Screens that hide the bottom navigation bar. */
    val HIDDEN_NAV = setOf(SETTINGS, DETAIL, SEARCH, LOGIN, REGISTER)
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

    private val authRepository: AuthRepository by lazy {
        val database = AppDatabase.getDatabase(application)
        AuthRepository(database.userDao())
    }

    private val _currentUser = MutableLiveData<User?>(null)
    val currentUser: LiveData<User?> = _currentUser

    private val _authError = MutableLiveData<String?>(null)
    val authError: LiveData<String?> = _authError

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

    init {
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
    }

    fun toggleCaught(id: Int) {
        val current = _caught.value ?: emptySet()
        val updated = current.toggle(id)
        _caught.value = updated
        prefs.edit().putStringSet("caught", updated.map { it.toString() }.toSet()).apply()
    }

    fun resetCollection() {
        _favourites.value = emptySet()
        _caught.value = emptySet()
        prefs.edit().remove("favourites").remove("caught").apply()
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
    }

    fun updateSearchHistoryEnabled(enabled: Boolean) {
        _searchHistoryEnabled.value = enabled
        prefs.edit().putBoolean("search_history_enabled", enabled).apply()
    }

    fun updateResumeLastViewed(enabled: Boolean) {
        _resumeLastViewed.value = enabled
        prefs.edit().putBoolean("resume_last_viewed", enabled).apply()
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun addSearchToHistory(query: String) {
        if (_searchHistoryEnabled.value != true || query.isBlank()) return
        val normalized = query.trim().lowercase()
        val currentList = _recentSearches.value ?: emptyList()
        val newList = (listOf(normalized) + currentList.filter { it != normalized }).take(10)
        _recentSearches.value = newList
        prefs.edit().putString("recent_searches_csv", newList.joinToString(",")).apply()
    }

    fun clearSearchHistory() {
        _recentSearches.value = emptyList()
        prefs.edit().remove("recent_searches_csv").apply()
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            when (val result = authRepository.login(username, password)) {
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

    fun register(username: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            when (val result = authRepository.register(username, password)) {
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
}
