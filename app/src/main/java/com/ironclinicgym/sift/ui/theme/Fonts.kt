@file:OptIn(ExperimentalTextApi::class)

package com.ironclinicgym.sift.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ironclinicgym.sift.R

/**
 * Sift's three families, bundled as local variable fonts in res/font:
 *
 *  - [Bricolage] — display, headings, labels, priority names, the wordmark.
 *  - [Hanken]    — body / UI text: task titles, metadata, descriptions.
 *  - [MaterialSymbols] — Material Symbols Rounded icon font (FILL 1 for filled glyphs).
 *
 * These ship in the app rather than via the Google Fonts downloadable provider, which did not
 * reliably serve them on every device (text fell back to the system sans, icons to tofu).
 * Weights are selected from the single variable font via the wght axis.
 */
private fun weightAxis(value: Int) = FontVariation.Settings(FontVariation.weight(value))

/** Display / headings / labels. Used at 500–700 with tight tracking. */
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque, weight = FontWeight.Medium, variationSettings = weightAxis(500)),
    Font(R.font.bricolage_grotesque, weight = FontWeight.SemiBold, variationSettings = weightAxis(600)),
    Font(R.font.bricolage_grotesque, weight = FontWeight.Bold, variationSettings = weightAxis(700)),
)

/** Body / UI text. */
val Hanken = FontFamily(
    Font(R.font.hanken_grotesk, weight = FontWeight.Normal, variationSettings = weightAxis(400)),
    Font(R.font.hanken_grotesk, weight = FontWeight.Medium, variationSettings = weightAxis(500)),
    Font(R.font.hanken_grotesk, weight = FontWeight.SemiBold, variationSettings = weightAxis(600)),
)

/** Material Symbols Rounded icon font, filled (FILL 1). */
val MaterialSymbols = FontFamily(
    Font(
        resId = R.font.material_symbols_rounded,
        variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", 1f)),
    ),
)

/** Material Symbols Rounded icon font, outline (FILL 0). */
val MaterialSymbolsOutline = FontFamily(
    Font(
        resId = R.font.material_symbols_rounded,
        variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", 0f)),
    ),
)
