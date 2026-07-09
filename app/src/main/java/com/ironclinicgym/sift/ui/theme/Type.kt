package com.ironclinicgym.sift.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Sift typography: Bricolage Grotesque for display / headings / titles / labels
 * (600–700, tight tracking), Hanken Grotesk for body. Priority names render lowercase
 * at the call site; this object only sets families, weights, and tracking.
 */
val SiftTypography = Typography().run {
    val tightTracking = (-0.02).em
    copy(
        displayLarge = displayLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold, letterSpacing = tightTracking),
        displayMedium = displayMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold, letterSpacing = tightTracking),
        displaySmall = displaySmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, letterSpacing = tightTracking),
        headlineLarge = headlineLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, letterSpacing = tightTracking),
        headlineMedium = headlineMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, letterSpacing = tightTracking),
        headlineSmall = headlineSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, letterSpacing = tightTracking),
        titleLarge = titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, letterSpacing = tightTracking),
        titleMedium = titleMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = Hanken, fontWeight = FontWeight.Normal),
        bodyMedium = bodyMedium.copy(fontFamily = Hanken, fontWeight = FontWeight.Normal),
        bodySmall = bodySmall.copy(fontFamily = Hanken, fontWeight = FontWeight.Normal),
        labelLarge = labelLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
    )
}

/** A baseline style for rendering Material Symbols Rounded glyphs by codepoint. */
val MaterialSymbolStyle = TextStyle(fontFamily = MaterialSymbols, fontSize = 24.sp)
