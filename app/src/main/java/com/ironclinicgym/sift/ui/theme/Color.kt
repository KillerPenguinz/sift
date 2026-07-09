package com.ironclinicgym.sift.ui.theme

import androidx.compose.ui.graphics.Color
import com.ironclinicgym.sift.core.theme.SiftColor

/**
 * The one bridge between the platform-neutral core and Compose: a resolved
 * [SiftColor] (straight-alpha sRGB, 0..1) maps directly onto a Compose [Color].
 * Everything color-related is computed in :core; this just re-wraps it.
 */
fun SiftColor.toColor(): Color = Color(red = r, green = g, blue = b, alpha = alpha)
