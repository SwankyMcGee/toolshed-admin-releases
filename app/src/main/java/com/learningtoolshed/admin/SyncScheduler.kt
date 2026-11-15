package com.learningtoolshed.admin

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/* ============================================================
   Scheduling for the background sync.

   Two triggers:
     1. Periodic — every 15 minutes, on both Wi-Fi and mobile data.
        Android floors this at 15 min and may batch it, so the
        actual interval can be longer in Doze.
     2. One-shot on wake — the app calls this when it launches or
        comes back to the foreground, so a sync happens the moment
        the user looks at the phone. Queued as "keep existing if
        one is already pending", so rapid launches don't stack.

   Both are best-effort: if the app was force-quit, the periodic
   job survives (WorkManager persists it) but the one-shot does not.
   ============================================================ */

object SyncScheduler {

    fun schedulePeriodic(app: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)   // Wi-Fi or mobile
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            SyncWorker.PERIODIC_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,   // don't reset the schedule if already there
            request
        )
    }

    /** A one-shot sync, e.g. when the app comes to the foreground.
        Coalesces: if one is already queued, this is a no-op. */
    fun requestNow(app: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(app).enqueueUniqueWork(
            "toolshed_sync_now",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAll(app: Context) {
        WorkManager.getInstance(app).cancelUniqueWork(SyncWorker.PERIODIC_NAME)
        WorkManager.getInstance(app).cancelUniqueWork("toolshed_sync_now")
    }
}