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
            pokemonId, // unique request code per pokemon
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

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(pokemonId, builder.build())
            }
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
}
