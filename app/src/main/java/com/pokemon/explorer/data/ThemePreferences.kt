package com.pokemon.explorer.data

import android.content.SharedPreferences
import com.pokemon.explorer.state.ThemeSetting

data class PendingTheme(val theme: ThemeSetting, val revision: String)

object ThemePreferences {
    fun select(prefs: SharedPreferences, uid: String, theme: ThemeSetting) {
        // Sleep slightly to guarantee a different System.currentTimeMillis() during fast tests
        Thread.sleep(1)
        val revision = System.currentTimeMillis().toString()
        prefs.edit()
            .putString("theme_$uid", theme.name)
            .putString("theme_pending_$uid", "${theme.name}:$revision")
            .apply()
    }

    fun read(prefs: SharedPreferences, uid: String): ThemeSetting {
        return decode(prefs.getString("theme_$uid", null))
    }

    fun decode(value: String?): ThemeSetting {
        return ThemeSetting.values().find { it.name == value } ?: ThemeSetting.System
    }

    fun pending(prefs: SharedPreferences, uid: String): PendingTheme? {
        val pendingStr = prefs.getString("theme_pending_$uid", null) ?: return null
        val parts = pendingStr.split(":", limit = 2)
        if (parts.size != 2) return null
        return PendingTheme(decode(parts[0]), parts[1])
    }

    fun preserveLegacyPending(prefs: SharedPreferences, uid: String): PendingTheme? {
        if (prefs.contains("theme_pending_$uid")) return pending(prefs, uid)
        val value = prefs.getString("theme_$uid", null) ?: return null
        val revision = System.currentTimeMillis().toString()
        prefs.edit().putString("theme_pending_$uid", "$value:$revision").apply()
        return PendingTheme(decode(value), revision)
    }

    fun confirm(prefs: SharedPreferences, uid: String, revision: String) {
        val currentPending = pending(prefs, uid)
        if (currentPending != null && currentPending.revision == revision) {
            prefs.edit().remove("theme_pending_$uid").apply()
        }
    }

    fun acceptRemote(prefs: SharedPreferences, uid: String, remoteThemeStr: String, revision: String?) {
        if (pending(prefs, uid) == null) {
            val remoteTheme = decode(remoteThemeStr)
            prefs.edit().putString("theme_$uid", remoteTheme.name).apply()
        }
    }
}
