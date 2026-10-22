package com.pokemon.explorer

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
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
import com.pokemon.explorer.ui.fragments.SplashFragment
import com.pokemon.explorer.ui.fragments.TypeGuideFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val appState: AppState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupBackHandler()

        appState.currentFrame.observe(this) { frame ->
            if (!appState.isSplashFinished) {
                binding.bottomNavigation.visibility = View.GONE
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
        binding.bottomNavigation.visibility = if (showNav) View.VISIBLE else View.GONE

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
