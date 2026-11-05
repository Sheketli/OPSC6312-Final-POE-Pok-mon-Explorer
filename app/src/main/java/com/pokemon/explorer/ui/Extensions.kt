package com.pokemon.explorer.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.pokemon.explorer.data.repository.SocialRepository
import kotlinx.coroutines.launch

fun AppCompatActivity.configureSocialRefresh(
    swipeRefresh: SwipeRefreshLayout,
    socialRepository: SocialRepository,
    action: (() -> Unit)? = null
) {
    swipeRefresh.setOnRefreshListener {
        lifecycleScope.launch {
            try {
                socialRepository.refresh()
            } catch (e: Exception) {
                // ignore
            } finally {
                swipeRefresh.isRefreshing = false
                action?.invoke()
            }
        }
    }
}
