package com.learningtoolshed.admin

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
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

            /* Update check: runs once on launch, quietly. If a newer
               release exists, a banner appears; if not, nothing. */
            var update by remember { mutableStateOf<UpdateInfo?>(null) }

            /* A downloaded APK waiting to be installed. The receiver
               can't launch the installer itself on Android 10+ (it
               isn't an Activity), so it parks the file here and this
               poller picks it up on the next frame. */
            var pendingInstall by remember { mutableStateOf(false) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                launch {
                    val info = withContext(Dispatchers.IO) {
                        UpdateChecker.check(BuildConfig.VERSION_CODE)
                    }
                    if (info != null) update = info
                }
                /* Watch for the download to land. Cheap: one volatile
                   read per second, and it stops as soon as it fires. */
                while (true) {
                    val f = UpdateDownloader.takeReady()
                    if (f != null) {
                        pendingInstall = true
                        break
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }

            androidx.compose.runtime.LaunchedEffect(pendingInstall) {
                if (pendingInstall) {
                    UpdateDownloader.takeReady()?.let { file ->
                        UpdateInstaller.install(this@MainActivity, file)
                    }
                    pendingInstall = false
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
                    onRefresh = vm::refresh,
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
                    /* The update card is part of the layout, above the
                       tabs, so it can't cover them. */
                    banner = update?.let { info ->
                        {
                            UpdateBanner(
                                versionName = info.versionName,
                                onDismiss = { update = null },
                                onDownload = {
                                    UpdateDownloader.download(this@MainActivity, info.downloadUrl)
                                    update = null
                                },
                                modifier = Modifier.padding(
                                    start = 14.dp, end = 14.dp, top = 8.dp
                                )
                            )
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
}