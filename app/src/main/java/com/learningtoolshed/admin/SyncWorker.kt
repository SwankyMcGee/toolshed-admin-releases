package com.learningtoolshed.admin

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* ============================================================
   Background sync.

   WorkManager runs this every 15 minutes (Android's floor for
   periodic work) and whenever the phone wakes up (a one-shot
   request from SyncScheduler). It is headless: no UI, no
   ViewModel, just the client and a notification.

   The diff is against a small stored set of "already notified"
   row IDs, not against the ViewModel's in-memory list. That way a
   sync that happens while the app is closed still knows what is
   new.

   Only unread ideas that arrived after the last successful sync
   produce a notification — the first sync never notifies, or the
   entire backlog would fire at once.
   ============================================================ */

class SyncWorker(
    app: Context,
    params: WorkerParameters
) : CoroutineWorker(app, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val client = AdminClient(applicationContext)
        if (!client.isConfigured()) return@withContext Result.failure()

        try {
            val ideas = client.ideas()

            /* Notified IDs: rows we have already pinged about. The
               first sync seeds this set without notifying. */
            val notified = NotifiedIds.read(applicationContext)
            val firstSync = notified.isEmpty() && client.lastSyncAt == 0L

            val fresh = if (firstSync) {
                emptyList()
            } else {
                ideas.filter { !it.read && !notified.contains(it.id) }
            }

            if (fresh.isNotEmpty()) {
                notify(applicationContext, fresh)
                NotifiedIds.addAll(applicationContext, fresh.map { it.id })
            }

            /* Remember which ideas exist at all, so a later sync only
               notifies on genuinely new rows, not on ones that were
               merely unread last time. */
            NotifiedIds.addAll(applicationContext, ideas.map { it.id })
            client.lastSyncAt = System.currentTimeMillis()
            Result.success()
        } catch (err: Exception) {
            /* Retry with backoff — the sheet or the network will
               probably be there next time. */
            if (err is AdminException && err.message == "forbidden") {
                Result.failure()   // wrong key; retrying won't help
            } else {
                Result.retry()
            }
        }
    }

    private fun notify(app: Context, ideas: List<Idea>) {
        /* Android 13+ needs the runtime grant before we can post. If
           the user declined, skip quietly — the inbox is still there. */
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                app, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = app.getSystemService(NotificationManager::class.java)
        val first = ideas.first()
        val title = if (ideas.size == 1) {
            when {
                first.isFeature && first.tool.isNotBlank() -> "New idea for ${first.tool}"
                else -> "A new idea arrived"
            }
        } else {
            "${ideas.size} new ideas"
        }
        val text = if (ideas.size == 1) {
            first.idea.take(120)
        } else {
            ideas.take(4).joinToString("\n") { "• " + it.idea.take(60) }
        }

        val intent = android.app.PendingIntent.getActivity(
            app, 0,
            android.content.Intent(app, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(app, ToolshedApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 4201

        /* Unique name for the periodic job. */
        const val PERIODIC_NAME = "toolshed_sync_periodic"

        /* How often the periodic job runs. Android floors this at 15
           minutes and may batch it with other apps' jobs. */
        const val PERIODIC_MINUTES = 15L
    }
}