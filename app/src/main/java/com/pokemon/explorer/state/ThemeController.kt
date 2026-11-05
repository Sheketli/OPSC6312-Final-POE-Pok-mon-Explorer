package com.pokemon.explorer.state

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.pokemon.explorer.data.ThemePreferences

class ThemeController(private val application: Application) {
    fun start() {
        val prefs = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("logged_in_user_id", null) ?: prefs.getString("logged_in_user", null)
        val themeSetting = if (username != null) ThemePreferences.read(prefs, username) else ThemeSetting.System
        applyTheme(themeSetting)
    }

    fun select(activeUserKey: String?, value: ThemeSetting) {
        if (activeUserKey != null) {
            val prefs = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            ThemePreferences.select(prefs, activeUserKey, value)
        }
        applyTheme(value)
    }

    private fun applyTheme(theme: ThemeSetting) {
        val mode = when (theme) {
            ThemeSetting.Light -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeSetting.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeSetting.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
