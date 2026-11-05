package com.pokemon.explorer.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pokemon.explorer.MainActivity
import com.pokemon.explorer.R

object NotificationHelper {
    private const val CHANNEL_ID = "rare_spawns_channel"
    private const val SOCIAL_CHANNEL_ID = "explorer_social_alerts"

    fun canSendNotification(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun sendRareSpawnNotification(
        context: Context,
        pokemonId: Int,
        pokemonName: String,
        expiresAt: Long
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("rare_pokemon_id", pokemonId)
            putExtra("rare_pokemon_name", pokemonName)
            putExtra("expires_at", expiresAt)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            pokemonId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pokeball)
            .setContentTitle("Rare Pokémon Alert!")
            .setContentText("A wild $pokemonName has appeared! Hurry, it flees soon!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (canSendNotification(context)) {
            try {
                NotificationManagerCompat.from(context).notify(pokemonId, builder.build())
            } catch (_: SecurityException) {}
        }
    }

    fun sendSocialNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int,
        targetScreen: String? = null,
        extraData: String? = null
    ) {
        createSocialNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            targetScreen?.let { putExtra("TARGET_SCREEN", it) }
            extraData?.let { putExtra("EXTRA_DATA", it) }
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, SOCIAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pokeball)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (canSendNotification(context)) {
            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            } catch (_: SecurityException) {}
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Rare Spawns"
            val descriptionText = "Notifications for rare Pokémon appearing nearby"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createSocialNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Social & Trading Alerts"
            val descriptionText = "Notifications for friend requests, messages, and trade proposals"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(SOCIAL_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
