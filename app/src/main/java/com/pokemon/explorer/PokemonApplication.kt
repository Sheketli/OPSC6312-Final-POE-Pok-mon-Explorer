package com.pokemon.explorer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pokemon.explorer.worker.RareSpawnWorker
import com.google.firebase.auth.FirebaseAuth
import com.pokemon.explorer.data.repository.SocialRepository
import com.pokemon.explorer.state.ThemeController

class PokemonApplication : Application(), ImageLoaderFactory {
    val themeController by lazy { ThemeController(this) }
    private var socialAccountId: String? = null
    private var socialSession: SocialRepository? = null

    @Synchronized
    fun socialRepository(): SocialRepository {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (socialSession == null || socialAccountId != uid) {
            socialSession?.close()
            socialAccountId = uid
            socialSession = SocialRepository(this)
        }
        return socialSession!!
    }

    override fun onCreate() {
        super.onCreate()
        themeController.start()
        FirebaseAuth.getInstance().addAuthStateListener {
            socialRepository()
        }
        // Automatically start the background service for rare spawns when the app launches!
        RareSpawnWorker.start(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    // Set a large disk cache size (250MB) to comfortably hold 1000+ artwork images
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            // Respect Cache-Control headers, but fall back to cached images aggressively
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
