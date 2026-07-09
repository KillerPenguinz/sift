package com.ironclinicgym.sift.ui.board

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.ui.theme.MaterialSymbols
import com.ironclinicgym.sift.ui.theme.MaterialSymbolsOutline

/**
 * Renders a Material Symbols Rounded glyph by name, using explicit codepoints (the downloadable
 * font does not resolve ligatures, which showed the literal names). Names come from the
 * theme/mapping and the icon picker, never hardcoded at a UI call site. Unknown names fall back
 * to a label glyph rather than breaking layout.
 */
private val CODEPOINTS: Map<String, Char> = mapOf(
    // Priority urgency icons
    "bolt" to Char(0xea0b), "wb_sunny" to Char(0xe430), "wb_twilight" to Char(0xe1c6),
    "eco" to Char(0xea35), "event" to Char(0xe878), "bedtime" to Char(0xf159),
    // Bucket / picker icons
    "person" to Char(0xf0d3), "work" to Char(0xe943), "favorite" to Char(0xe87e), "auto_awesome" to Char(0xe65f),
    "label" to Char(0xe893), "home" to Char(0xe9b2), "school" to Char(0xe80c), "fitness_center" to Char(0xeb43),
    "star" to Char(0xf09a), "code" to Char(0xe86f), "shopping_cart" to Char(0xe8cc), "pets" to Char(0xe91d),
    "flight" to Char(0xe539), "restaurant" to Char(0xe56c), "savings" to Char(0xe2eb), "health_and_safety" to Char(0xe1d5),
    "sports_esports" to Char(0xea28), "menu_book" to Char(0xea19), "coffee" to Char(0xefef), "spa" to Char(0xeb4c),
    "rocket_launch" to Char(0xeb9b), "brush" to Char(0xe3ae), "music_note" to Char(0xe405), "directions_run" to Char(0xe566),
    "local_cafe" to Char(0xeb44),
    // UI controls
    "unfold_less" to Char(0xe5d6), "unfold_more" to Char(0xe5d7), "view_agenda" to Char(0xe8e9), "grid_view" to Char(0xe9b0),
    "refresh" to Char(0xe5d5), "settings" to Char(0xe8b8), "arrow_back" to Char(0xe5c4), "add" to Char(0xe145),
    "close" to Char(0xe5cd), "check_circle" to Char(0xf0be), "schedule" to Char(0xefd6), "palette" to Char(0xe40a),
    "edit" to Char(0xf097), "visibility" to Char(0xe8f4), "visibility_off" to Char(0xe8f5), "delete" to Char(0xe92e),
    "expand_more" to Char(0xe5cf), "expand_less" to Char(0xe5ce), "drag_handle" to Char(0xe25d),
    // Settings + navigation
    "chevron_right" to Char(0xe5cc), "tune" to Char(0xe429), "link" to Char(0xe250),
    "storage" to Char(0xe1db), "swap_horiz" to Char(0xe8d4), "science" to Char(0xea4b),
    "add_task" to Char(0xf23a), "widgets" to Char(0xe1bd), "smart_toy" to Char(0xf06c),
    "info" to Char(0xe88e), "chat_bubble" to Char(0xe0cb),
    // Phase 3.5 additions
    "push_pin" to Char(0xf10d), "open_in_new" to Char(0xe89e), "notifications" to Char(0xe7f4),
    "lightbulb" to Char(0xe0f0), "dashboard" to Char(0xe871),
    // Redesign additions
    "keep" to Char(0xf4e2), "north_east" to Char(0xf1e1), "mic" to Char(0xe029),
    "cloud_off" to Char(0xe167), "calendar_month" to Char(0xebcc), "repeat" to Char(0xe040),
    "notes" to Char(0xe26c), "remove" to Char(0xe15b),
)

/** Icon names offered when the user customizes a bucket. */
val BUCKET_ICON_CHOICES: List<String> = listOf(
    "person", "work", "favorite", "auto_awesome", "home", "school", "fitness_center", "health_and_safety",
    "star", "code", "shopping_cart", "pets", "flight", "restaurant", "savings", "sports_esports",
    "menu_book", "coffee", "spa", "rocket_launch", "brush", "music_note", "directions_run", "label",
)

@Composable
fun MaterialSymbol(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    filled: Boolean = true,
) {
    val glyph = (CODEPOINTS[name] ?: CODEPOINTS.getValue("label")).toString()
    Text(
        text = glyph,
        color = color,
        modifier = modifier,
        style = TextStyle(
            fontFamily = if (filled) MaterialSymbols else MaterialSymbolsOutline,
            fontSize = size,
        ),
    )
}
