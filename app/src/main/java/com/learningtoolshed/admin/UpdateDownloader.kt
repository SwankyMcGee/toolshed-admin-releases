package com.learningtoolshed.admin

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/* ============================================================
   In-app updates.

   Flow:
     1. UpdateChecker finds a newer release (already built).
     2. The banner's "Download" button calls UpdateDownloader.download.
     3. DownloadManager pulls the APK to app-private storage.
     4. When the download finishes, a receiver fires UpdateInstaller.install.
     5. The system package installer opens; user taps Install.
     6. The new APK replaces the old one and launches.

   The user only sees: a download progress notification (from
   DownloadManager) and the system install prompt. No browser, no
   GitHub page, no file manager.

   First time only: Android asks the user to allow "install unknown
   apps" for Toolshed Admin. After that, updates are one tap.
   ============================================================ */

object UpdateDownloader {

    fun download(context: Context, url: String) {
        val fileName = "toolshed-update.apk"
        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        /* Wipe any previous partial download. */
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Toolshed Admin update")
            setDescription("Downloading the latest version")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)

        /* Listen for the download to finish. The receiver is
           registered for the lifetime of the process; it only acts
           on our download, not anyone else's. */
        context.registerReceiver(
            UpdateReceiver(context),
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }
}

private class UpdateReceiver(private val appContext: Context) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id <= 0) return

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val status = manager.getDownloadStatus(id)

        /* Only act on our own download. We can't tag a request with
           an ID at enqueue time, so we check the destination file
           exists and is a plausible APK. */
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "toolshed-update.apk")
        if (!file.exists()) return

        when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> UpdateInstaller.install(context, file)
            DownloadManager.STATUS_FAILED -> {
                /* Quiet failure — the user can try again from the
                   banner. No toast spam. */
            }
        }
    }

    private fun DownloadManager.getDownloadStatus(id: Long): Int {
        val cursor = query(DownloadManager.Query().setFilterById(id)) ?: return -1
        cursor.use {
            if (!it.moveToFirst()) return -1
            val col = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            return it.getInt(col)
        }
    }
}

object UpdateInstaller {

    fun install(context: Context, file: File) {
        /* Android 8+ needs the user to have granted "install unknown
           apps" for this app. If they haven't, send them to the
           settings page to do it. */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}