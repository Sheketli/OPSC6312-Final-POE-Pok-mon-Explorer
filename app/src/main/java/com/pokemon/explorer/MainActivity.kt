package com.pokemon.explorer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pokemon.explorer.databinding.ActivityMainBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.NavFrame
import com.pokemon.explorer.state.Screen
import com.pokemon.explorer.state.TabId
import com.pokemon.explorer.ui.fragments.AccountManagementFragment
import com.pokemon.explorer.ui.fragments.CollectionFragment
import com.pokemon.explorer.ui.fragments.CompareFragment
import com.pokemon.explorer.ui.fragments.DetailFragment
import com.pokemon.explorer.ui.fragments.HomeFragment
import com.pokemon.explorer.ui.fragments.LoginFragment
import com.pokemon.explorer.ui.fragments.RegisterFragment
import com.pokemon.explorer.ui.fragments.SearchFragment
import com.pokemon.explorer.ui.fragments.SettingsFragment
import com.pokemon.explorer.ui.fragments.SplashFragment
import com.pokemon.explorer.ui.fragments.TypeGuideFragment

/**
 * Main activity for the Pokémon Explorer application.
 *
 * Implements Android's Single Activity Architecture pattern, hosting fragment navigation,
 * bottom navigation bar management, camera AR launching, and handling notification intent routing.
 *
 * References:
 * - Android Guide to App Architecture: https://developer.android.com/topic/architecture
 * - Single Activity Architecture: https://developer.android.com/guide/navigation/navigation-principles
 * - Request Runtime Permissions: https://developer.android.com/training/permissions/requesting
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val appState: AppState by viewModels()

    /**
     * Activity Result Launcher for POST_NOTIFICATIONS runtime permission (API 33+ Tiramisu).
     * @see https://developer.android.com/develop/ui/views/notifications/notification-permission
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(TAG, "Notification permission granted by user.")
            Toast.makeText(this, getString(R.string.ui_notifications_enabled), Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "Notification permission denied by user.")
            Toast.makeText(this, getString(R.string.ui_notifications_disabled), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Prompts the user for notification permissions on API level 33 (Tiramisu) and above.
     * Displays a rationale dialog if the user has previously denied the request.
     */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            Log.d(TAG, "Checking POST_NOTIFICATIONS permission status: $permissionStatus")

            if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission is already GRANTED.")
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Log.i(TAG, "Showing educational rationale dialog for notification permission.")
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.ui_enable_notifications))
                    .setMessage("Pokémon Explorer uses notifications to alert you about rare spawns and game events. Please enable them to stay updated!")
                    .setPositiveButton(getString(R.string.ui_ok)) { _, _ ->
                        Log.d(TAG, "User accepted rationale dialog, launching permission request.")
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setNegativeButton(getString(R.string.ui_no_thanks)) { dialog, _ ->
                        Log.d(TAG, "User dismissed notification permission rationale dialog.")
                        dialog.dismiss()
                    }
                    .show()
            } else {
                Log.d(TAG, "Directly launching POST_NOTIFICATIONS permission prompt.")
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Initializing MainActivity layout and state observers.")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        askNotificationPermission()
        setupBottomNavigation()
        setupBackHandler()
        
        binding.fabCameraLauncher.setOnClickListener {
            Log.d(TAG, "FAB clicked: Launching AR Camera.")
            launchCamera()
        }

        checkNotificationIntent(intent)

        // Observe frame changes to display the active fragment screen
        appState.currentFrame.observe(this) { frame ->
            Log.d(TAG, "Observed currentFrame change: screen=${frame.screen}, id=${frame.id}")
            if (!appState.isSplashFinished) {
                Log.i(TAG, "Splash screen not finished, displaying SplashFragment.")
                binding.bottomNavigation.visibility = View.GONE
                binding.fabCameraLauncher.visibility = View.GONE
                showFragment(SplashFragment(), addToBackStack = false)
                appState.isSplashFinished = true
            } else {
                updateScreen(frame)
            }
        }

        // Observe active tab to synchronize bottom navigation selection
        appState.activeTab.observe(this) { tab ->
            Log.d(TAG, "Observed activeTab change: tab=$tab")
            val itemId = when (tab) {
                TabId.Explore -> R.id.nav_explore
                TabId.Collection -> R.id.nav_collection
                TabId.Compare -> R.id.nav_compare
                TabId.Guide -> R.id.nav_guide
            }
            if (binding.bottomNavigation.selectedItemId != itemId) {
                binding.bottomNavigation.selectedItemId = itemId
            }
        }
    }

    /**
     * Launches the AR Camera activity with optional parameters and shared element transition.
     * @param extras Optional extras bundle (e.g. rare spawn details)
     * @param useSharedTransition Whether to use a pokeball FAB transition animation
     */
    private fun launchCamera(extras: Bundle? = null, useSharedTransition: Boolean = true) {
        Log.i(TAG, "launchCamera: Starting CameraArActivity (useSharedTransition=$useSharedTransition)")
        val intent = Intent(this, CameraArActivity::class.java)
        extras?.let { intent.putExtras(it) }
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        if (useSharedTransition && binding.fabCameraLauncher.isLaidOut) {
            val sharedElementOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                binding.fabCameraLauncher,
                "transition_pokeball_to_camera"
            )
            startActivity(intent, sharedElementOptions.toBundle())
        } else {
            startActivity(intent)
        }
    }

    /**
     * Inspects incoming intent extras for deep-linked notifications (e.g., social alerts or rare spawns).
     */
    private fun checkNotificationIntent(intent: Intent?) {
        if (intent == null) return
        Log.d(TAG, "checkNotificationIntent: Inspecting intent payload.")

        if (intent.hasExtra("TARGET_SCREEN")) {
            val target = intent.getStringExtra("TARGET_SCREEN")
            val extraData = intent.getStringExtra("EXTRA_DATA")
            Log.i(TAG, "Found target notification screen: target=$target, extraData=$extraData")
            intent.removeExtra("TARGET_SCREEN")
            intent.removeExtra("EXTRA_DATA")
            when (target) {
                "friends" -> {
                    startActivity(Intent(this, com.pokemon.explorer.ui.FriendsActivity::class.java))
                }
                "chat" -> {
                    val chatIntent = Intent(this, com.pokemon.explorer.ui.ChatActivity::class.java).apply {
                        extraData?.let { putExtra("TARGET_FRIEND", it) }
                    }
                    startActivity(chatIntent)
                }
                "trade" -> {
                    startActivity(Intent(this, com.pokemon.explorer.ui.TradeSubmissionActivity::class.java))
                }
            }
        }

        if (intent.hasExtra("rare_pokemon_id")) {
            val pokemonId = intent.getIntExtra("rare_pokemon_id", -1).let {
                if (it == -1) intent.getStringExtra("rare_pokemon_id")?.toIntOrNull() ?: -1 else it
            }

            val expiresAt = intent.getLongExtra("expires_at", -1L).let {
                if (it == -1L) intent.getStringExtra("expires_at")?.toLongOrNull() ?: -1L else it
            }
            
            Log.i(TAG, "Notification contained rare Pokémon spawn: pokemonId=$pokemonId, expiresAt=$expiresAt")
            intent.removeExtra("rare_pokemon_id")
            intent.removeExtra("expires_at")
            
            if (pokemonId != -1 && expiresAt != -1L) {
                val extras = Bundle().apply {
                    putInt("rare_pokemon_id", pokemonId)
                    putLong("expires_at", expiresAt)
                }
                launchCamera(extras, useSharedTransition = false)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: Activity received new intent.")
        setIntent(intent)
        checkNotificationIntent(intent)
    }

    /**
     * Configures item selection listeners for the BottomNavigationView.
     */
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            Log.d(TAG, "Bottom navigation item clicked: id=${item.itemId}")
            when (item.itemId) {
                R.id.nav_explore -> appState.selectTab(TabId.Explore)
                R.id.nav_collection -> appState.selectTab(TabId.Collection)
                R.id.nav_compare -> appState.selectTab(TabId.Compare)
                R.id.nav_guide -> appState.selectTab(TabId.Guide)
            }
            true
        }
    }

    /**
     * Handles system back press events using OnBackPressedDispatcher.
     * @see https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
     */
    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back button pressed. Invoking AppState.goBack()")
                if (!appState.goBack()) {
                    Log.i(TAG, "AppState backstack empty. Executing default activity back press.")
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * Updates the main container fragment based on current user session and navigation frame.
     */
    private fun updateScreen(frame: NavFrame) {
        val user = appState.currentUser.value
        val showNav = user != null && !Screen.HIDDEN_NAV.contains(frame.screen)
        Log.d(TAG, "updateScreen: user=${user?.username}, screen=${frame.screen}, showNav=$showNav")

        if (showNav) {
            binding.bottomNavigation.visibility = View.VISIBLE
            binding.fabCameraLauncher.visibility = View.VISIBLE
        } else {
            binding.bottomNavigation.visibility = View.GONE
            binding.fabCameraLauncher.visibility = View.GONE
        }

        val fragment: Fragment = if (user == null) {
            when (frame.screen) {
                Screen.REGISTER -> RegisterFragment()
                else -> LoginFragment()
            }
        } else {
            when (frame.screen) {
                Screen.HOME -> HomeFragment()
                Screen.SEARCH -> SearchFragment()
                Screen.DETAIL -> DetailFragment.newInstance(frame.id ?: 25)
                Screen.COMPARE -> CompareFragment.newInstance(frame.idA, frame.id)
                Screen.COLLECTION -> CollectionFragment()
                Screen.TYPE_GUIDE -> TypeGuideFragment()
                Screen.SETTINGS -> SettingsFragment()
                Screen.ACCOUNT -> AccountManagementFragment()
                else -> HomeFragment()
            }
        }

        showFragment(fragment, addToBackStack = false)
    }

    /**
     * Replaces the fragment inside fragment_container.
     */
    private fun showFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        Log.d(TAG, "showFragment: Replacing current fragment with ${fragment.javaClass.simpleName}")
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }
}
