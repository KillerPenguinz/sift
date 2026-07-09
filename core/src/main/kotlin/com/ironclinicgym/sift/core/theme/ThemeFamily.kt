package com.ironclinicgym.sift.core.theme

/**
 * A theme family groups a light variant and a dark variant under one name.
 *
 * Every family must provide at least one mode. When a mode is missing (null), the toggle
 * falls back gracefully: the family's single available mode is used regardless of the
 * system setting. This lets themes ship incrementally (e.g., Slate is dark-only today;
 * when a Slate Light is added, users get it automatically).
 */
data class ThemeFamily(
    val key: String,
    val label: String,
    val lightKey: String?,
    val darkKey: String?,
) {
    fun resolveKey(isDark: Boolean): String = when {
        isDark && darkKey != null -> darkKey
        !isDark && lightKey != null -> lightKey
        darkKey != null -> darkKey
        lightKey != null -> lightKey
        else -> DEFAULT_THEME.key
    }
}

val THEME_FAMILIES: List<ThemeFamily> = listOf(
    ThemeFamily(key = "paper", label = "Paper", lightKey = "paper", darkKey = "paper_dark"),
    ThemeFamily(key = "slate", label = "Slate", lightKey = null, darkKey = "slate"),
    ThemeFamily(key = "ink", label = "Ink", lightKey = null, darkKey = "ink"),
    ThemeFamily(key = "linen", label = "Linen", lightKey = "linen", darkKey = null),
    ThemeFamily(key = "cyber", label = "Cyber", lightKey = null, darkKey = "cyber"),
)

val DEFAULT_FAMILY: ThemeFamily = THEME_FAMILIES.first()
