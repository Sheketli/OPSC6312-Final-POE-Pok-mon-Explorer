package com.pokemon.explorer.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pokemon.explorer.MainActivity
import com.pokemon.explorer.R
import com.pokemon.explorer.utils.NotificationHelper

class PokeNotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val payloadType = remoteMessage.data["type"] // "FRIEND_REQUEST", "CHAT_MESSAGE", "TRADE_REQUEST"
        val sender = remoteMessage.data["sender_username"] ?: "A Trainer"
        val bodyContent = remoteMessage.data["body"] ?: ""

        val alertTitle: String
        val alertBody: String
        val notificationId: Int
        var targetScreen: String? = null
        var extraData: String? = null

        when (payloadType) {
            "FRIEND_REQUEST" -> {
                alertTitle = "New Friend Request!"
                alertBody = "$sender sent you a friend request via their Explorer Account."
                notificationId = 4001
                targetScreen = "friends"
            }
            "CHAT_MESSAGE" -> {
                alertTitle = "Message from $sender"
                alertBody = bodyContent
                notificationId = sender.hashCode()
                targetScreen = "chat"
                extraData = sender
            }
            "TRADE_REQUEST" -> {
                alertTitle = "Trade Proposal!"
                alertBody = "$sender sent you a Pokémon trade proposal."
                notificationId = 5001
                targetScreen = "trade"
            }
            else -> {
                val pokemonIdStr = remoteMessage.data["rare_pokemon_id"]
                val pokemonName = remoteMessage.data["rare_pokemon_name"]
                val expiresAtStr = remoteMessage.data["expires_at"]

                if (pokemonIdStr != null && pokemonName != null && expiresAtStr != null) {
                    val pokemonId = pokemonIdStr.toIntOrNull()
                    val expiresAt = expiresAtStr.toLongOrNull()
                    if (pokemonId != null && expiresAt != null) {
                        NotificationHelper.sendRareSpawnNotification(
                            this, pokemonId, pokemonName, expiresAt
                        )
                    }
                }
                return
            }
        }

        dispatchLocalNotification(alertTitle, alertBody, notificationId, targetScreen, extraData)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    private fun dispatchLocalNotification(
        title: String,
        body: String,
        id: Int,
        targetScreen: String?,
        extraData: String?
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "explorer_social_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Social & Trading Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            targetScreen?.let { putExtra("TARGET_SCREEN", it) }
            extraData?.let { putExtra("EXTRA_DATA", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            id,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_pokeball)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        if (NotificationHelper.canSendNotification(this)) {
            try {
                manager.notify(id, builder.build())
            } catch (_: SecurityException) {}
        }
    }
}
