package com.pokemon.explorer

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokemon.explorer.databinding.ActivityChatViewBinding
import com.pokemon.explorer.databinding.ActivityFriendsBinding
import com.pokemon.explorer.databinding.ActivityTradeSubmissionBinding
import com.pokemon.explorer.databinding.ItemChatMessageBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialAppearanceTest {
    private fun themedContext(nightMode: Int): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        return ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_PokemonExplorer)
    }

    @Test
    fun socialLayoutsInflateWithRefreshAndReadableColoursInBothThemes() {
        assertNotEquals(
            ContextCompat.getColor(themedContext(Configuration.UI_MODE_NIGHT_NO), R.color.app_background),
            ContextCompat.getColor(themedContext(Configuration.UI_MODE_NIGHT_YES), R.color.app_background)
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (mode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val context = themedContext(mode)
                val inflater = LayoutInflater.from(context)
                val friends = ActivityFriendsBinding.inflate(inflater)
                val chat = ActivityChatViewBinding.inflate(inflater)
                val trade = ActivityTradeSubmissionBinding.inflate(inflater)
                val message = ItemChatMessageBinding.inflate(inflater)
                assertNotNull(friends.swipeRefresh)
                assertNotNull(chat.swipeRefresh)
                assertNotNull(trade.swipeRefresh)
                val background = ContextCompat.getColor(context, R.color.app_background)
                assertEquals(background, (friends.root.background as ColorDrawable).color)
                assertEquals(background, (chat.root.background as ColorDrawable).color)
                assertEquals(ContextCompat.getColor(context, R.color.text_high), message.tvMessageContent.currentTextColor)
                assertNotEquals(background, message.tvMessageContent.currentTextColor)
            }
        }
    }

    @Test
    fun collapsedNotificationTitlesIdentifyTheSender() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Misty sent you a friend request", context.getString(R.string.friend_request_notification, "Misty"))
        assertEquals("Ash sent a trade proposal", context.getString(R.string.trade_request_notification, "Ash"))
        assertEquals("Misty sent a return offer", context.getString(R.string.trade_return_notification, "Misty"))
    }
}
