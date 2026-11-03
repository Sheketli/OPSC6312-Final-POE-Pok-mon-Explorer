package com.pokemon.explorer.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.pokemon.explorer.R
import com.pokemon.explorer.data.PokeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class BulkDownloadService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private val CHANNEL_ID = "bulk_download_channel"
    private val NOTIFICATION_ID = 1001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(0, 1025))

        scope.launch {
            val total = 1025
            var progress = 0
            val batchSize = 10
            val imageLoader = applicationContext.imageLoader

            (1..total).chunked(batchSize).forEach { chunk ->
                chunk.map { id ->
                    async {
                        try {
                            PokeApi.detail(id)
                            
                            // Download and cache the main artwork image
                            val url = PokeApi.artworkUrl(id)
                            val request = ImageRequest.Builder(applicationContext)
                                .data(url)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build()
                            imageLoader.execute(request)
                        } catch (e: Exception) {
                            // ignore individual failures, just keep going
                        }
                    }
                }.awaitAll()

                progress += chunk.size
                updateNotification(progress, total)
            }

            // Finished
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val finishedNotification = NotificationCompat.Builder(this@BulkDownloadService, CHANNEL_ID)
                .setContentTitle("Download Complete")
                .setContentText("Successfully downloaded offline database.")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Placeholder
                .build()
            
            notificationManager.notify(NOTIFICATION_ID + 1, finishedNotification)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Database Download",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for full offline database downloads"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int, total: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading Full Database")
            .setContentText("Downloaded $progress / $total Pokémon")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Placeholder
            .setProgress(total, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(progress: Int, total: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(progress, total))
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
