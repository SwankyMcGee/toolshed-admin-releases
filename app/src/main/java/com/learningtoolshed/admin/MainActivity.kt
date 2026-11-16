package com.learningtoolshed.admin

import android.Manifest
import android.app.DownloadManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /* Android 13+ needs a runtime grant for the one notification that
       matters: "an idea just landed". If refused, the app still works —
       the inbox is the point, the ping is a convenience. */
    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        /* Start the periodic background sync (idempotent) and request
           a one-shot sync now, so the inbox is fresh the moment the
           app opens. */
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.requestNow(this)

        val vm = InboxViewModel(application)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()

            /* The palette is app-local, so it survives rotation but is
               read straight from the ViewModel to keep one source. */
            var palette by remember { mutableStateOf(vm.paletteId) }

            /* Update check: on launch, and again whenever the refresh
               button is pressed. A release published while the app is
               open should be findable without force-quitting it. */
            var update by remember { mutableStateOf<UpdateInfo?>(null) }
            var lastUpdateCheck by remember { mutableStateOf(0L) }
            val scope = rememberCoroutineScope()

            suspend fun fetchUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
                UpdateChecker.check(BuildConfig.VERSION_CODE)
            }

            /* Refresh: the inbox, and a look for a newer build. Throttled,
               because the GitHub releases API is unauthenticated and
               allows 60 requests an hour per address — a run of
               refreshes would otherwise spend them all. */
            fun refresh() {
                vm.refresh()
                scope.launch {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUpdateCheck < UPDATE_CHECK_GAP_MS) return@launch
                    lastUpdateCheck = now
                    val info = fetchUpdate()
                    if (info != null) update = info
                }
            }

            /* A download is in flight. The banner's button clears itself
               when this is set, and a small card says what's happening. */
            var downloading by remember { mutableStateOf(false) }
            var downloadFailed by remember { mutableStateOf<String?>(null) }

            /* Android 10+ won't let a background app launch the
               installer, so installing waits until we're in front.
               Nothing is lost by waiting: the download carries on, and
               the status is still there when we resume. */
            var resumed by remember { mutableStateOf(false) }
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> resumed = true
                        Lifecycle.Event.ON_PAUSE,
                        Lifecycle.Event.ON_STOP -> resumed = false
                        else -> Unit
                    }
                }
                this@MainActivity.lifecycle.addObserver(observer)
                onDispose { this@MainActivity.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(Unit) {
                /* Stamp before the call, so a refresh that lands while
                   this one is in flight doesn't start a second. */
                lastUpdateCheck = SystemClock.elapsedRealtime()
                val info = fetchUpdate()

                /* A download that finished while the app wasn't running
                   leaves an APK on disk. If an update is still on offer,
                   install it now rather than making the user find it; if
                   not, the file is a leftover from an install that
                   already happened and can go. */
                val apk = UpdateDownloader.apkFile(this@MainActivity)
                if (apk.exists() && apk.length() > 0L) {
                    if (info != null) {
                        UpdateInstaller.install(this@MainActivity, apk)
                    } else {
                        apk.delete()
                    }
                }

                if (info != null) update = info
            }

            /* Watch the download. Polling rather than waiting for a
               broadcast: the broadcast needs an exported receiver on
               Android 13+, which is exactly the sort of thing that
               silently stops arriving. */
            LaunchedEffect(downloading, resumed) {
                if (!downloading) return@LaunchedEffect
                /* No point asking while we're in the background — but no
                   harm either, since we check again on resume. */
                while (downloading && resumed) {
                    /* One of DownloadManager.STATUS_*: 1 pending, 2
                       running, 8 successful, 16 failed. */
                    when (UpdateDownloader.statusOf(this@MainActivity)) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            /* The APK is on disk and we're in the
                               foreground: open the installer now. */
                            UpdateInstaller.install(
                                this@MainActivity,
                                UpdateDownloader.apkFile(this@MainActivity)
                            )
                            UpdateDownloader.clear()
                        }

                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            downloadFailed =
                                UpdateDownloader.failureReason(this@MainActivity)
                        }

                        else -> delay(1000)
                    }
                }
            }

            ToolshedTheme(paletteId = palette) {
                ToolshedAppRoot(
                    state = state,
                    paletteId = palette,
                    onPaletteChange = { vm.setPalette(it); palette = it },
                    onQueryChange = vm::setQuery,
                    onToggleUnread = vm::toggleUnread,
                    onToggleType = vm::toggleType,
                    onClearFilters = vm::clearFilters,
                    onRefresh = { refresh() },
                    onMarkRead = vm::markRead,
                    onMarkAllRead = vm::markAllRead,
                    onSetStatus = vm::setStatus,
                    onSaveNote = vm::saveNote,
                    onLoadReplies = vm::loadReplies,
                    onSendReply = { id, body -> vm.addReply(id, body) },
                    onShareReply = { body -> shareReply(body) },
                    onSaveConnection = vm::saveConnection,
                    onDismissMessage = vm::dismissMessage,
                    onLoadUsage = vm::loadUsage,
                    onLoadManifest = vm::loadManifest,
                    /* The update cards are part of the layout, above the
                       tabs, so they can't cover them. */
                    banner = {
                        Column(
                            modifier = Modifier.padding(
                                start = 14.dp, end = 14.dp, top = 8.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (downloading) {
                                DownloadProgressCard(
                                    onCancel = {
                                        downloading = false
                                        UpdateDownloader.clear()
                                    }
                                )
                            } else {
                                update?.let { info ->
                                    UpdateBanner(
                                        versionName = info.versionName,
                                        onDismiss = { update = null },
                                        onDownload = {
                                            downloadFailed = null
                                            downloading = UpdateDownloader.download(
                                                this@MainActivity, info.downloadUrl
                                            ) > 0L
                                            if (!downloading) {
                                                downloadFailed = "The download didn't start."
                                            }
                                        }
                                    )
                                }
                            }

                            /* A failed download says why, next to the
                               card that will retry it. */
                            downloadFailed?.let { reason ->
                                UpdateFailedCard(
                                    reason = reason,
                                    onDismiss = { downloadFailed = null }
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        /* Coming back to the app: sync if it's been a little while.
           The periodic job covers the long gaps; this covers "I was
           in another app for 30 seconds and something may have
           arrived." */
        SyncScheduler.requestNow(this)
    }

    /** Open the admin's mail app with a pre-filled reply. The reply
        is saved in the thread first (by the ViewModel); this just
        hands the text to the mail client. */
    private fun shareReply(body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, "Re: The Learning Toolshed — your idea")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(send, "Send reply"))
    }

    private companion object {
        /* The GitHub releases API allows 60 unauthenticated requests an
           hour per address. One check per launch is nothing; one per
           refresh could be. */
        const val UPDATE_CHECK_GAP_MS = 60_000L
    }
}