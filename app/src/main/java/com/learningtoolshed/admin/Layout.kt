package com.learningtoolshed.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/* ============================================================
   Just enough responsive layout.

   The app was written for a phone in portrait, and it still is the
   primary target. Three things follow from that and are worth stating
   once, here, rather than in three screens:

   - Content has a maximum width. On a tablet in landscape a card
     stretched edge to edge is a 1200dp line of 13sp text, which is
     tiring to read and looks unfinished. Lists cap at 720dp and
     centre.

   - The one screen that gains from width uses it: Overview is an
     adaptive grid, one column on a phone and two or three on a
     tablet, with the intro and footnotes spanning the full width.

   - The breakpoint is the Material window-size-class one (600dp),
     computed from the configuration rather than a library, so there's
     no dependency to keep up to date.

   Deliberately not done: a navigation rail, or a two-pane
   list-detail layout. Both are real tablet patterns, and both are a
   lot of structure for an app one person uses on a phone.
   ============================================================ */

/** The widest a screen's content should get before it centres. */
private val CONTENT_MAX_WIDTH = 720.dp

/** True on a tablet or a phone in landscape. */
@Composable
fun isWide(): Boolean =
    LocalConfiguration.current.screenWidthDp >= 600

/**
 * Centres its content and caps its width. Use it around a scrolling
 * column: `WideContainer { LazyColumn(...) }`.
 */
@Composable
fun WideContainer(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH)) {
            content()
        }
    }
}