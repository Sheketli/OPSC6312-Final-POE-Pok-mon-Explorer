package com.pokemon.explorer.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pokemon.explorer.MainActivity
import com.pokemon.explorer.R

/**
 * Singleton helper managing system notification channel creation, permission verification,
 * and posting local notifications for rare spawns and social interactions.
 *
 * References:
 * - Create and Manage Notification Channels: https://developer.android.com/develop/ui/views/notifications/channels
 * - Notification Permissions in Android 13+: https://developer.android.com/develop/ui/views/notifications/notification-permission
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID = "rare_spawns_channel"
    private const val SOCIAL_CHANNEL_ID = "explorer_social_alerts"

    /**
     * Checks whether runtime notification permission is granted or enabled on device.
     */
    fun canSendNotification(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        Log.d(TAG, "canSendNotification check result: $granted")
        return granted
    }

    /**
     * Sends a rare spawn alert notification to the system bar.
     */
    fun sendRareSpawnNotification(
        context: Context,
        pokemonId: Int,
        pokemonName: String,
        expiresAt: Long
    ) {
        Log.i(TAG, "sendRareSpawnNotification: Dispatching alert for $pokemonName (#$pokemonId)")
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
                Log.d(TAG, "Notification successfully posted to NotificationManager.")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while notifying rare spawn: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "Cannot send rare spawn notification: POST_NOTIFICATIONS permission disabled.")
        }
    }

    /**
     * Sends a social alert notification (friend request, chat message, trade proposal).
     */
    fun sendSocialNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int,
        targetScreen: String? = null,
        extraData: String? = null
    ) {
        Log.i(TAG, "sendSocialNotification: title=$title, id=$notificationId, targetScreen=$targetScreen")
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (canSendNotification(context)) {
            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                Log.d(TAG, "Social notification successfully posted.")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while notifying social alert: ${e.message}", e)
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
