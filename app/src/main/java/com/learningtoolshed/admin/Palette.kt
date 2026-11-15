package com.learningtoolshed.admin

import androidx.compose.ui.graphics.Color

/* ============================================================
   The Toolshed palettes — kept identical to THEMES in the site's
   site.js and to html[data-theme="…"] in site.css.

   chips = [page background, surface, text, accent] exactly as the
   site's five-chip rows list them (the app doesn't need the second
   accent). If a palette changes on the site, change it here too —
   the app should never look like a different building.
   ============================================================ */

data class Palette(
    val id: String,
    val name: String,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val dim: Color,
    val border: Color,
    val accent: Color
)

val PALETTES: List<Palette> = listOf(
    Palette("calm", "Calm (soft blue & green)",
        Color(0xFFF2F6F8), Color(0xFFFFFFFF), Color(0xFFE9EFF3),
        Color(0xFF33414D), Color(0xFF6B7C8A), Color(0xFFD5DFE6), Color(0xFF4D84A6)),
    Palette("lavender", "Lavender (soft purple)",
        Color(0xFFF5F3FB), Color(0xFFFFFFFF), Color(0xFFECE8F7),
        Color(0xFF3D3A52), Color(0xFF75708F), Color(0xFFDDD7EF), Color(0xFF7A6FC0)),
    Palette("mint", "Mint (fresh green)",
        Color(0xFFEEF8F3), Color(0xFFFFFFFF), Color(0xFFE0F0E8),
        Color(0xFF2F4640), Color(0xFF648076), Color(0xFFCFE6DC), Color(0xFF35837E)),
    Palette("peach", "Peach (warm apricot)",
        Color(0xFFFDF6EF), Color(0xFFFFFDFB), Color(0xFFF9ECE0),
        Color(0xFF4D3F34), Color(0xFF8A7566), Color(0xFFF0E0D0), Color(0xFFC26A3C)),
    Palette("rose", "Rose (soft pink)",
        Color(0xFFFDF4F6), Color(0xFFFFFFFF), Color(0xFFF9E7EC),
        Color(0xFF4F3540), Color(0xFF8C6B78), Color(0xFFF2DAE1), Color(0xFFC25F7E)),
    Palette("ocean", "Ocean (aqua blue)",
        Color(0xFFEEF8FA), Color(0xFFFFFFFF), Color(0xFFDFF0F4),
        Color(0xFF2C4750), Color(0xFF5F7F89), Color(0xFFCFE6EB), Color(0xFF2F87A3)),
    Palette("low", "Low contrast (very gentle)",
        Color(0xFFEEF1F3), Color(0xFFF6F8F9), Color(0xFFEAEDEF),
        Color(0xFF5B6570), Color(0xFF838D97), Color(0xFFE2E6E9), Color(0xFF94B4C3)),
    Palette("high", "High contrast",
        Color(0xFF000000), Color(0xFF0B0D10), Color(0xFF16191E),
        Color(0xFFFFFFFF), Color(0xFFD0D6DC), Color(0xFFFFFFFF), Color(0xFF56E0FF)),
    Palette("dark", "Dark (slate)",
        Color(0xFF151A20), Color(0xFF1E242B), Color(0xFF252C34),
        Color(0xFFE7EDF2), Color(0xFF9AA7B3), Color(0xFF323B45), Color(0xFF6FA9C7)),
    Palette("midnight", "Midnight (deep blue)",
        Color(0xFF0A0F1E), Color(0xFF121A30), Color(0xFF1A2440),
        Color(0xFFE3E9F7), Color(0xFF9AA6C4), Color(0xFF2C3A5E), Color(0xFF6FA8FF)),
    Palette("warm", "Warm dark (sepia)",
        Color(0xFF211A14), Color(0xFF2B221A), Color(0xFF352A20),
        Color(0xFFF0E6DA), Color(0xFFB3A291), Color(0xFF4A3C2E), Color(0xFFD9A05F))
)

fun paletteById(id: String?): Palette =
    PALETTES.firstOrNull { it.id == id } ?: PALETTES.first()