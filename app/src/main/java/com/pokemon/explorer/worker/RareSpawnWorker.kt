package com.pokemon.explorer.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pokemon.explorer.utils.NotificationHelper
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class RareSpawnWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // High rarity / interesting Pokemon IDs
        val rarePokemonList = listOf(
            Pair(150, "Mewtwo"),
            Pair(149, "Dragonite"),
            Pair(6, "Charizard"),
            Pair(143, "Snorlax"),
            Pair(131, "Lapras"),
            Pair(144, "Articuno"),
            Pair(145, "Zapdos"),
            Pair(146, "Moltres"),
            Pair(130, "Gyarados"),
            Pair(94, "Gengar"),
            Pair(142, "Aerodactyl"),
            Pair(151, "Mew")
        )

        val randomRare = rarePokemonList.random()

        // Expiry time: 5 minutes from now
        val expiryTimeMillis = System.currentTimeMillis() + (5 * 60 * 1000)

        // Dispatch the local notification
        NotificationHelper.sendRareSpawnNotification(
            context,
            randomRare.first,
            randomRare.second,
            expiryTimeMillis
        )

        // Schedule the next rare spawn notification (much faster now, every 1 to 2 minutes)
        val delayMinutes = Random.nextLong(1, 3)
        val nextWork = OneTimeWorkRequestBuilder<RareSpawnWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "RareSpawnWork",
            ExistingWorkPolicy.REPLACE,
            nextWork
        )

        return Result.success()
    }

    companion object {
        fun start(context: Context) {
            // FIRE IMMEDIATELY upon app launch (No initial delay)
            val workRequest = OneTimeWorkRequestBuilder<RareSpawnWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "RareSpawnWork",
                ExistingWorkPolicy.REPLACE, // Force replace to ensure it fires right now
                workRequest
            )
        }
    }
}
