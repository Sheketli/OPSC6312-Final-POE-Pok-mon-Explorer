package com.pokemon.explorer

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            
            val pokemonIdStr = remoteMessage.data["rare_pokemon_id"]
            val pokemonName = remoteMessage.data["rare_pokemon_name"]
            val expiresAtStr = remoteMessage.data["expires_at"]
            
            if (pokemonIdStr != null && pokemonName != null && expiresAtStr != null) {
                val pokemonId = pokemonIdStr.toIntOrNull()
                val expiresAt = expiresAtStr.toLongOrNull()
                
                if (pokemonId != null && expiresAt != null) {
                    com.pokemon.explorer.utils.NotificationHelper.sendRareSpawnNotification(
                        this, pokemonId, pokemonName, expiresAt
                    )
                }
            }
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}
