package com.learningtoolshed.admin

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/* ============================================================
   The logo — the site's logo.

   site.css swaps toolshed-logo.png for toolshed-logo-light.png on the
   dark themes (html[data-theme="dark"], "midnight", "warm", "high").
   The app picks between the same two files the same way, so switching
   palette never leaves the brand upside down.

   `loading = true` turns the mark into the refresh indicator: it turns
   on the spot and breathes. The refresh button swaps its icon for this
   while a sync is running, and the "Looking…" state uses a large one,
   so it's the brand that says "working" rather than a generic spinner.
   ============================================================ */

/* Both variants are trimmed to 308×202. */
internal const val BRAND_ASPECT = 308f / 202f

/** The logo that suits the current palette. */
@Composable
internal fun brandLogoRes(): Int =
    if (isDark(MaterialTheme.toolshed.bg)) R.drawable.ic_logo_inverse else R.drawable.ic_logo

@Composable
internal fun BrandMark(
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    contentDescription: String? = "The Learning Toolshed"
) {
    val painter = painterResource(brandLogoRes())

    if (!loading) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "brand")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing)
        )
    )
    val breathe by transition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer {
            rotationZ = turn
            scaleX = breathe
            scaleY = breathe
        },
        contentScale = ContentScale.Fit
    )
}

/* The logo sized by height, keeping its own shape.

   An Image with only a height would fill whatever width it was given
   and centre the mark inside it, which in the header meant the brand
   drifting to the middle of the bar. Aspect ratio first, then height:
   the width follows, so it sits where the text used to. */
@Composable
internal fun BrandMarkH(
    height: Dp,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) = BrandMark(
    modifier = modifier.height(height).aspectRatio(BRAND_ASPECT),
    loading = loading
)

/** The mark at the size the header's refresh button needs. */
@Composable
internal fun BrandSpinner(modifier: Modifier = Modifier.size(24.dp)) =
    BrandMark(modifier = modifier, loading = true, contentDescription = "Refreshing")