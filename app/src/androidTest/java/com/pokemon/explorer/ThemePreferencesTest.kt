package com.pokemon.explorer

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokemon.explorer.data.ThemePreferences
import com.pokemon.explorer.state.ThemeSetting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreferencesTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setup() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("theme-sync-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun cleanup() {
        prefs.edit().clear().commit()
    }

    @Test
    fun staleCloudThemeCannotOverrideAnOfflineSelection() {
        ThemePreferences.select(prefs, "ash", ThemeSetting.Dark)
        ThemePreferences.acceptRemote(prefs, "ash", "Light", "older-device-revision")
        assertEquals(ThemeSetting.Dark, ThemePreferences.read(prefs, "ash"))
        assertNotNull(ThemePreferences.pending(prefs, "ash"))
    }

    @Test
    fun olderAcknowledgementCannotClearANewerThemeSelection() {
        ThemePreferences.select(prefs, "ash", ThemeSetting.Dark)
        val oldRevision = ThemePreferences.pending(prefs, "ash")!!.revision
        ThemePreferences.select(prefs, "ash", ThemeSetting.System)
        val latestRevision = ThemePreferences.pending(prefs, "ash")!!.revision
        ThemePreferences.confirm(prefs, "ash", oldRevision)
        assertEquals(latestRevision, ThemePreferences.pending(prefs, "ash")!!.revision)
        assertEquals(ThemeSetting.System, ThemePreferences.read(prefs, "ash"))
        ThemePreferences.confirm(prefs, "ash", latestRevision)
        assertNull(ThemePreferences.pending(prefs, "ash"))
        ThemePreferences.acceptRemote(prefs, "ash", "Light", "new-other-device-revision")
        assertEquals(ThemeSetting.Light, ThemePreferences.read(prefs, "ash"))
    }

    @Test
    fun themesRemainScopedToTheirAccountsAndSystemPersistsAsASetting() {
        ThemePreferences.select(prefs, "ash", ThemeSetting.Dark)
        ThemePreferences.select(prefs, "misty", ThemeSetting.System)
        assertEquals(ThemeSetting.Dark, ThemePreferences.read(prefs, "ash"))
        assertEquals("System", ThemePreferences.pending(prefs, "misty")!!.theme.name)
        assertEquals(ThemeSetting.System, ThemePreferences.read(prefs, "unknown"))
        assertEquals(ThemeSetting.System, ThemePreferences.decode("invalid-legacy-value"))
    }

    @Test
    fun anUnsentThemeFromThePreviousBuildIsPreservedUntilAcknowledged() {
        prefs.edit().putString("theme_ash", "Dark").commit()
        val pending = ThemePreferences.preserveLegacyPending(prefs, "ash")!!
        ThemePreferences.acceptRemote(prefs, "ash", "Light", null)
        assertEquals(ThemeSetting.Dark, ThemePreferences.read(prefs, "ash"))
        ThemePreferences.confirm(prefs, "ash", pending.revision)
        assertNull(ThemePreferences.pending(prefs, "ash"))
    }
}
