package com.pokemon.explorer.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pokemon.explorer.utils.NotificationHelper
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Background WorkManager worker responsible for generating rare Pokémon spawns and dispatching local notifications.
 *
 * References:
 * - WorkManager Documentation: https://developer.android.com/topic/libraries/architecture/workmanager
 * - Android Notifications Guide: https://developer.android.com/develop/ui/views/notifications/build-notification
 */
class RareSpawnWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "RareSpawnWorker"

        /**
         * Enqueues an immediate rare spawn work request upon application launch.
         */
        fun start(context: Context) {
            Log.i(TAG, "start: Triggering immediate RareSpawnWorker via WorkManager.")
            val workRequest = OneTimeWorkRequestBuilder<RareSpawnWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "RareSpawnWork",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "doWork: Executing RareSpawnWorker job.")
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
        Log.i(TAG, "Spawn selected: #${randomRare.first} (${randomRare.second})")

        // Expiry time: 5 minutes from now
        val expiryTimeMillis = System.currentTimeMillis() + (5 * 60 * 1000)

        // Dispatch local notification
        NotificationHelper.sendRareSpawnNotification(
            context,
            randomRare.first,
            randomRare.second,
            expiryTimeMillis
        )

        // Schedule next rare spawn notification (between 1 and 2 minutes)
        val delayMinutes = Random.nextLong(1, 3)
        Log.d(TAG, "Scheduling next rare spawn notification in $delayMinutes minute(s).")
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
}
