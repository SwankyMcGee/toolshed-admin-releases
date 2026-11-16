package com.learningtoolshed.admin

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/* ============================================================
   In-app updates.

   Flow:
     1. UpdateChecker finds a newer release.
     2. The banner's "Download" button calls download().
     3. DownloadManager pulls the APK into app-private storage.
     4. While the app is in front, MainActivity polls the download's
        status once a second and calls install() the moment it lands.
     5. Android shows its own install prompt; the user taps Install.

   Why polling and not a broadcast receiver:

   The receiver version had two bugs that made every update need a
   manual tap of the download notification. It read the "download
   finished" broadcast — which is registered RECEIVER_NOT_EXPORTED on
   Android 13+, so it is not certain to be delivered at all — and it
   parked the file in a one-shot holder that the UI read twice, so the
   second read always found nothing and the installer never opened.

   Asking DownloadManager directly for the status of the download we
   started has neither problem: it is one query, it cannot be missed,
   and the answer is still there when the app comes back from the
   background. The cost is a one-second poll while a download is in
   flight, which is nothing.

   Backgrounding: Android 10+ refuses to start an Activity from the
   background, so the install waits until MainActivity is resumed. The
   download keeps going either way, and DownloadManager's own
   notification stays as the manual fallback for anyone who leaves the
   app for good mid-download.

   The one interaction that cannot be removed is the system install
   prompt: Android requires the user to approve an install that didn't
   come from Play. See UpdateInstaller for what that does and does not
   cover.
   ============================================================ */

object UpdateDownloader {

    private const val FILE_NAME = "toolshed-update.apk"

    /** The id DownloadManager gave our download, or -1 when there
        isn't one in flight. */
    @Volatile
    var downloadId: Long = -1L
        private set

    fun apkFile(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FILE_NAME)

    /** Start the download. Returns the DownloadManager id so the
        caller can watch it, or -1 if it couldn't be queued. */
    fun download(context: Context, url: String): Long {
        val destination = apkFile(context)

        /* Wipe any previous partial download. */
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Toolshed Admin update")
            setDescription("Downloading the latest version")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                FILE_NAME
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val manager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        return try {
            val id = manager.enqueue(request)
            downloadId = id
            id
        } catch (err: Exception) {
            downloadId = -1L
            -1L
        }
    }

    /** One of DownloadManager.STATUS_*, or STATUS_FAILED when there is
        no download to ask about. */
    fun statusOf(context: Context): Int {
        val id = downloadId
        if (id <= 0L) return DownloadManager.STATUS_FAILED

        val manager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        return try {
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return DownloadManager.STATUS_FAILED
                val column = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (column < 0) return DownloadManager.STATUS_FAILED
                cursor.getInt(column)
            }
        } catch (err: Exception) {
            DownloadManager.STATUS_FAILED
        }
    }

    /** The reason a failed download failed, in words worth showing. */
    fun failureReason(context: Context): String {
        val id = downloadId
        if (id <= 0L) return "The download didn't start."

        val manager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        return try {
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return "The download was removed."
                val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                val reason = if (reasonCol < 0) 0 else cursor.getInt(reasonCol)
                when (reason) {
                    DownloadManager.ERROR_CANNOT_RESUME ->
                        "The download couldn't resume. Check the connection and try again."
                    DownloadManager.ERROR_DEVICE_NOT_FOUND ->
                        "There's no storage for the download."
                    DownloadManager.ERROR_FILE_ALREADY_EXISTS ->
                        "The download was already on the device."
                    DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                        "There isn't enough space for the update."
                    else -> "The download stopped. Check the connection and try again."
                }
            }
        } catch (err: Exception) {
            "The download stopped. Check the connection and try again."
        }
    }

    fun clear() { downloadId = -1L }
}

object UpdateInstaller {

    /** Hand the APK to Android's installer.
        Call from an Activity that is in the foreground.

        Android requires the user to approve an install that didn't come
        from Play. There is no app-level way around that — it is enforced
        by the package installer, not by anything this app does. What
        this method does do is skip the steps that *were* avoidable: no
        browser, no file manager, no digging the APK out of Downloads. */
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
               have no installer. DownloadManager's notification is
               still there for the user to tap manually. */
        }
    }
}