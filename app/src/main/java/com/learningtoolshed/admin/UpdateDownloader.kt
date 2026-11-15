package com.learningtoolshed.admin

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/* ============================================================
   In-app updates.

   Flow:
     1. UpdateChecker finds a newer release (already built).
     2. The banner's "Download" button calls UpdateDownloader.download.
     3. DownloadManager pulls the APK to app-private storage.
     4. When the download finishes, UpdateReceiver fires and stores
        the APK path in a tiny holder.
     5. The app's next frame notices the holder is set and calls
        UpdateInstaller.install from an Activity context — a
        background receiver can't launch the installer on newer
        Android (background activity launch limits), and doing it
        from a BroadcastReceiver is exactly what crashes.

   The user only sees: a download progress notification (from
   DownloadManager) and the system install prompt. No browser, no
   GitHub page, no file manager.

   First time only: Android asks the user to allow "install unknown
   apps" for Toolshed Admin. After that, updates are one tap.
   ============================================================ */

object UpdateDownloader {

    /** Set by UpdateReceiver when the APK lands, consumed by the
        next composition. Internal because the receiver (a separate
        class) writes it; the app only reads it. */
    @Volatile
    var readyToInstall: File? = null
        internal set

    fun takeReady(): File? {
        val f = readyToInstall ?: return null
        readyToInstall = null
        return f
    }

    fun download(context: Context, url: String) {
        val fileName = "toolshed-update.apk"

        /* Wipe any previous partial download. */
        val destination = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Toolshed Admin update")
            setDescription("Downloading the latest version")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val manager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        try {
            /* ACTION_DOWNLOAD_COMPLETE is a system broadcast, not an
               app one, so it does NOT need RECEIVER_EXPORTED. But
               Android 14 requires the flag to be explicit either way,
               and omitting it throws. */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    UpdateReceiver(),
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(
                    UpdateReceiver(),
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (err: Exception) {
            /* Already registered (a second tap) — harmless. */
        }

        manager.enqueue(request)
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id <= 0) return

        /* Only act on our own download. */
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "toolshed-update.apk"
        )
        if (!file.exists()) return

        val manager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val cursor = manager.query(DownloadManager.Query().setFilterById(id))
        cursor?.use {
            if (!it.moveToFirst()) return
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = it.getInt(statusCol)
            /* STATUS_SUCCESSFUL is 8. */
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                UpdateDownloader.readyToInstall = file
            }
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
                val intent =
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
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
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        try {
            context.startActivity(intent)
        } catch (err: Exception) {
            /* No activity to handle it — some tablets in kiosk mode
               have no installer. The download notification is still
               there for the user to tap manually. */
        }
    }
}