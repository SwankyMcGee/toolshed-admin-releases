package com.learningtoolshed.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ============================================================
   The Toolshed theme.

   Material 3 underneath (so ripples, sheets and dialogs behave),
   but every color comes from a Palette, so the app looks like the
   site: soft backgrounds, white cards, thin borders, one accent.
   Borders are deliberately visible — the site's cards have them,
   and "gentle" is the point, not plain white-on-white.
   ============================================================ */

@Immutable
internal data class ToolshedColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val dim: Color,
    val border: Color,
    val accent: Color,
    val accentText: Color
)

private val LocalToolshedColors = staticCompositionLocalOf {
    val p = PALETTES.first()
    ToolshedColors(p.bg, p.surface, p.surface2, p.text, p.dim, p.border, p.accent, p.surface)
}

internal val MaterialTheme.toolshed: ToolshedColors
    @Composable get() = LocalToolshedColors.current

@Composable
fun ToolshedTheme(paletteId: String, content: @Composable () -> Unit) {
    val palette = paletteById(paletteId)
    val dark = isDark(palette.bg)

    val colors = ToolshedColors(
        bg = palette.bg,
        surface = palette.surface,
        surface2 = palette.surface2,
        text = palette.text,
        dim = palette.dim,
        border = palette.border,
        accent = palette.accent,
        accentText = if (dark) palette.bg else palette.surface
    )

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentText,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.dim,
            outline = colors.border,
            error = Color(0xFFC25F7E)
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentText,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.dim,
            outline = colors.border,
            error = Color(0xFFC25F7E)
        )
    }

    CompositionLocalProvider(
        LocalToolshedColors provides colors
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ToolshedTypography,
            shapes = Shapes(),
            content = {
                Surface(color = colors.bg, content = content)
            }
        )
    }
}

@Composable
private fun Shapes() = androidx.compose.material3.Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

/* ---------- typography ----------
   Sans-serif, exactly like the site's font stack. */
private val ToolshedTypography = androidx.compose.material3.Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 19.sp
    )
)

/* ---------- the site's card ---------- */

/** Mirrors `.card` in site.css: white, 1px border, 12px corners. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val c = MaterialTheme.toolshed
    val shape = RoundedCornerShape(12.dp)
    val base = Modifier
        .then(modifier)
        .fillMaxWidth()
        .clip(shape)
        .background(
            if (primary) c.surface2 else c.surface
        )
        .border(
            width = if (primary) 2.dp else 1.dp,
            color = if (primary) c.accent else c.border,
            shape = shape
        )

    if (onClick != null) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            modifier = base,
            shape = shape,
            color = Color.Transparent
        ) { content() }
    } else {
        Box(modifier = base.padding(16.dp)) { content() }
    }
}

/** `.lbl` — the site's inline mark for exact on-screen wording. */
@Composable
fun Label(text: String) {
    val c = MaterialTheme.toolshed
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
    }
}

/** A pill button, like `.idea-again`. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val c = MaterialTheme.toolshed
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) c.accent else Color.Transparent)
            .border(1.dp, if (filled) c.accent else c.border, shape)
            .clickableNoRipple(onClick)
            /* 48dp is the Material minimum touch target. The text sits
               in the middle, so the pill can be comfortably tapped
               without looking oversized. */
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (filled) c.accentText else c.accent
        )
    }
}

/** Small caps-ish section header, like the site's h2 underline. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.toolshed
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = c.text
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .heightIn(min = 2.dp)
                .background(c.border)
        )
    }
}

private fun isDark(color: Color): Boolean {
    val l = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return l < 0.5f
}