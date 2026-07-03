package com.triledge.dailyjournal.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Curated brand palette. 12 hand-picked seeds that look reasonable in both
 * light and dark modes. The full ColorScheme is derived from each seed at
 * runtime — we don't ship 12 separate schemes.
 */
data class BrandColor(
    val id: String,
    val displayName: String,
    val seed: Color
)

object BrandPalette {
    val All: List<BrandColor> = listOf(
        BrandColor("luxe", "Luxe", Color(0xFF087E8B)),
        BrandColor("sapphire", "Sapphire", Color(0xFF2D5BFF)),
        BrandColor("crimson", "Crimson", Color(0xFFDC2626)),
        BrandColor("emerald", "Emerald", Color(0xFF059669)),
        BrandColor("amber", "Amber", Color(0xFFD97706)),
        BrandColor("violet", "Violet", Color(0xFF7C3AED)),
        BrandColor("rose", "Rose", Color(0xFFE11D48)),
        BrandColor("teal", "Teal", Color(0xFF0D9488)),
        BrandColor("indigo", "Indigo", Color(0xFF4338CA)),
        BrandColor("lime", "Lime", Color(0xFF65A30D)),
        BrandColor("slate", "Slate", Color(0xFF475569)),
        BrandColor("coral", "Coral", Color(0xFFF43F5E)),
        BrandColor("forest", "Forest", Color(0xFF166534))
    )

    /** Default Triledge brand seed. */
    val Default: BrandColor = All.first()

    fun byId(id: String): BrandColor? = All.firstOrNull { it.id == id }
}