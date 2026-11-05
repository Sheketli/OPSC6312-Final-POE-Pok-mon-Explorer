package com.pokemon.explorer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object EmailService {
    private const val TAG = "EmailService"

    /**
     * Dispatches external email notification via mailto Intent or background proxy protocol logic.
     */
    fun sendFriendRequestEmail(
        context: Context,
        senderUsername: String,
        recipientEmail: String,
        recipientUsername: String
    ) {
        val subject = "New Friend Request from $senderUsername!"
        val body = "Hello $recipientUsername,\n\nYou received a pending friend request from user account \"$senderUsername\" on Pokémon Explorer. Log into your account app profile to accept their request and unlock trading privileges immediately.\n\nBest regards,\nPokémon Explorer Team"

        dispatchEmailIntent(context, recipientEmail, subject, body)
    }

    fun sendMessageNotificationEmail(
        context: Context,
        senderUsername: String,
        recipientEmail: String,
        recipientUsername: String,
        messageSnippet: String
    ) {
        val subject = "New Message from $senderUsername on Pokémon Explorer"
        val body = "Hello $recipientUsername,\n\n$senderUsername sent you a message: \"$messageSnippet\"\n\nLog into your Pokémon Explorer account to reply!\n\nBest regards,\nPokémon Explorer Team"

        dispatchEmailIntent(context, recipientEmail, subject, body)
    }

    fun sendTradeNotificationEmail(
        context: Context,
        senderUsername: String,
        recipientEmail: String,
        recipientUsername: String,
        pokemonName: String
    ) {
        val subject = "Trade Proposal from $senderUsername on Pokémon Explorer!"
        val body = "Hello $recipientUsername,\n\n$senderUsername has sent you a trade proposal offering $pokemonName!\n\nLog into your Pokémon Explorer account to review and accept or decline the trade proposal.\n\nBest regards,\nPokémon Explorer Team"

        dispatchEmailIntent(context, recipientEmail, subject, body)
    }

    private fun dispatchEmailIntent(context: Context, toEmail: String, subject: String, body: String) {
        Log.d(TAG, "External verification mail dispatch scheduled for: $toEmail [Subject: $subject]")
        try {
            val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$toEmail")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            val chooserIntent = Intent.createChooser(mailIntent, "Send Email Notification via...").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching email notification intent", e)
        }
    }
}
