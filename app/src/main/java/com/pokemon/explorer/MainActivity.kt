package com.pokemon.explorer

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.app.ActivityOptionsCompat
import com.pokemon.explorer.databinding.ActivityMainBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.state.NavFrame
import com.pokemon.explorer.state.Screen
import com.pokemon.explorer.state.TabId
import com.pokemon.explorer.ui.fragments.CollectionFragment
import com.pokemon.explorer.ui.fragments.CompareFragment
import com.pokemon.explorer.ui.fragments.DetailFragment
import com.pokemon.explorer.ui.fragments.HomeFragment
import com.pokemon.explorer.ui.fragments.LoginFragment
import com.pokemon.explorer.ui.fragments.RegisterFragment
import com.pokemon.explorer.ui.fragments.SearchFragment
import com.pokemon.explorer.ui.fragments.SettingsFragment
import com.pokemon.explorer.ui.fragments.AccountManagementFragment
import com.pokemon.explorer.ui.fragments.SplashFragment
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pokemon.explorer.ui.fragments.TypeGuideFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val appState: AppState by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
            Toast.makeText(this, getString(R.string.ui_notifications_enabled), Toast.LENGTH_SHORT).show()
        } else {
            // Inform user that your app will not show notifications.
            Toast.makeText(this, getString(R.string.ui_notifications_disabled), Toast.LENGTH_SHORT).show()
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Display an educational UI explaining to the user the features that will be enabled
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.ui_enable_notifications))
                    .setMessage("Pokémon Explorer uses notifications to alert you about rare spawns and game events. Please enable them to stay updated!")
                    .setPositiveButton(getString(R.string.ui_ok)) { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setNegativeButton(getString(R.string.ui_no_thanks)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        askNotificationPermission()

        setupBottomNavigation()
        setupBackHandler()
        
        binding.fabCameraLauncher.setOnClickListener {
            launchCamera()
        }

        checkNotificationIntent(intent)

        appState.currentFrame.observe(this) { frame ->
            if (!appState.isSplashFinished) {
                binding.bottomNavigation.visibility = View.GONE
                binding.fabCameraLauncher.visibility = View.GONE
                showFragment(SplashFragment(), addToBackStack = false)
                appState.isSplashFinished = true
            } else {
                updateScreen(frame)
            }
        }

        appState.activeTab.observe(this) { tab ->
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

    private fun launchCamera(extras: Bundle? = null, useSharedTransition: Boolean = true) {
        val intent = android.content.Intent(this, CameraArActivity::class.java)
        extras?.let { intent.putExtras(it) }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
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

    private fun checkNotificationIntent(intent: android.content.Intent?) {
        if (intent != null && intent.hasExtra("rare_pokemon_id")) {
            // FCM data payloads are sometimes delivered as Strings, but our NotificationHelper uses typed values.
            val pokemonId = intent.getIntExtra("rare_pokemon_id", -1).let {
                if (it == -1) intent.getStringExtra("rare_pokemon_id")?.toIntOrNull() ?: -1 else it
            }

            val expiresAt = intent.getLongExtra("expires_at", -1L).let {
                if (it == -1L) intent.getStringExtra("expires_at")?.toLongOrNull() ?: -1L else it
            }
            
            // Remove the extras so we don't process this same notification intent again 
            // if the activity is recreated (e.g. on rotation)
            intent.removeExtra("rare_pokemon_id")
            intent.removeExtra("expires_at")
            
            if (pokemonId != -1 && expiresAt != -1L) {
                // If it's a rare spawn notification, launch the camera immediately without shared transition
                val extras = Bundle().apply {
                    putInt("rare_pokemon_id", pokemonId)
                    putLong("expires_at", expiresAt)
                }
                launchCamera(extras, useSharedTransition = false)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkNotificationIntent(intent)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_explore -> appState.selectTab(TabId.Explore)
                R.id.nav_collection -> appState.selectTab(TabId.Collection)
                R.id.nav_compare -> appState.selectTab(TabId.Compare)
                R.id.nav_guide -> appState.selectTab(TabId.Guide)
            }
            true
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!appState.goBack()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun updateScreen(frame: NavFrame) {
        val user = appState.currentUser.value
        val showNav = user != null && !Screen.HIDDEN_NAV.contains(frame.screen)
        
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

    private fun showFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }
}
