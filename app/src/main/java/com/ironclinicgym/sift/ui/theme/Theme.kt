package com.ironclinicgym.sift.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ironclinicgym.sift.core.theme.DEFAULT_FAMILY
import com.ironclinicgym.sift.core.theme.DerivedTheme
import com.ironclinicgym.sift.core.theme.deriveTheme

val LocalSiftTheme = staticCompositionLocalOf<DerivedTheme> {
    error("No SiftTheme provided — wrap your content in SiftTheme { … }")
}

@Composable
fun SiftTheme(
    familyKey: String = DEFAULT_FAMILY.key,
    themeMode: String = "auto",
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val tokens = remember(familyKey, isDark) { deriveTheme(familyKey, isDark) }
    val colorScheme = remember(tokens) { tokens.toMaterialColorScheme() }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    CompositionLocalProvider(LocalSiftTheme provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SiftTypography,
            content = content,
        )
    }
}

object SiftTheme {
    val tokens: DerivedTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalSiftTheme.current
}

private fun DerivedTheme.toMaterialColorScheme() = with(neutrals) {
    val base = if (theme.isDark) darkColorScheme() else lightColorScheme()
    base.copy(
        background = bg.toColor(),
        onBackground = textPrimary.toColor(),
        surface = surface.toColor(),
        onSurface = textPrimary.toColor(),
        surfaceVariant = surfaceRaised.toColor(),
        onSurfaceVariant = textSecondary.toColor(),
        surfaceContainer = surface.toColor(),
        surfaceContainerHigh = surfaceRaised.toColor(),
        surfaceContainerHighest = surfaceRaised.toColor(),
        outline = border.toColor(),
        outlineVariant = border.toColor(),
        primary = textPrimary.toColor(),
        onPrimary = bg.toColor(),
        scrim = scrim.toColor(),
        error = priorities.first().accent.toColor(),
    )
}
