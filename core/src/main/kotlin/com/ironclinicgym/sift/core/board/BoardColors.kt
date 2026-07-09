package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.theme.PriorityKey
import com.ironclinicgym.sift.core.theme.Oklab
import com.ironclinicgym.sift.core.theme.SiftColor
import com.ironclinicgym.sift.core.theme.BucketKey
import com.ironclinicgym.sift.core.theme.Theme
import com.ironclinicgym.sift.core.theme.mixOklab
import com.ironclinicgym.sift.core.theme.parseOklch

/**
 * Resolves the colors a board priority or bucket needs, for any theme. Mirrors the derivation
 * math in `derive()` (Phase 1) but parameterized by a color key, so real user priorities and
 * buckets render with the same coherence as the sample board. Pure Kotlin; the UI converts
 * the returned [SiftColor]s to platform colors.
 */

data class PriorityColors(
    val accent: SiftColor,
    val accentText: SiftColor,
    val headerBg: SiftColor,
    val pillBg: SiftColor,
    val pillText: SiftColor,
)

data class BucketColors(
    val color: SiftColor,
    val bg: SiftColor,
    val onColor: SiftColor,
)

fun priorityColors(theme: Theme, key: PriorityKey): PriorityColors {
    val accent = parseOklch(theme.accent.getValue(key)).toOklab()
    val surface = parseOklch(theme.surface).toOklab()
    val aText = accentText(theme, accent)
    return PriorityColors(
        accent = accent.toSrgb(),
        accentText = aText,
        headerBg = mixOklab(accent, theme.headerMix / 100.0, surface).toSrgb(),
        pillBg = if (theme.boldFill) accent.toSrgb() else mixOklab(accent, theme.pillMix / 100.0, surface).toSrgb(),
        pillText = if (theme.boldFill) {
            mixOklab(accent, 0.92, if (theme.isDark) Oklab.BLACK else Oklab.WHITE).toSrgb()
        } else {
            aText
        },
    )
}

/** [key] null returns a neutral coding for an unset bucket. */
fun bucketColors(theme: Theme, key: BucketKey?): BucketColors {
    val surface = parseOklch(theme.surface).toOklab()
    if (key == null) {
        val neutral = parseOklch(theme.textTertiary).toOklab()
        return BucketColors(
            color = parseOklch(theme.textSecondary).toOklab().toSrgb(),
            bg = mixOklab(neutral, theme.bucketBgMix / 100.0, surface).toSrgb(),
            onColor = parseOklch(theme.textSecondary).toOklab().toSrgb(),
        )
    }
    val color = parseOklch(theme.bucket.getValue(key)).toOklab()
    return BucketColors(
        color = color.toSrgb(),
        bg = mixOklab(color, theme.bucketBgMix / 100.0, surface).toSrgb(),
        onColor = if (theme.isDark) mixOklab(color, 0.12, Oklab.WHITE).toSrgb() else color.toSrgb(),
    )
}

fun overdueColors(theme: Theme): Pair<SiftColor, SiftColor> {
    val asap = parseOklch(theme.accent.getValue(PriorityKey.ASAP)).toOklab()
    val surface = parseOklch(theme.surface).toOklab()
    val bg = mixOklab(asap, if (theme.isDark) 0.24 else 0.13, surface).toSrgb()
    val text = if (theme.isDark) mixOklab(asap, 0.20, Oklab.WHITE).toSrgb() else mixOklab(asap, 0.74, Oklab.BLACK).toSrgb()
    return bg to text
}

private fun accentText(theme: Theme, accent: Oklab): SiftColor =
    if (theme.isDark) {
        mixOklab(accent, (100 - theme.accentTextMix) / 100.0, Oklab.WHITE).toSrgb()
    } else {
        mixOklab(accent, theme.accentTextMix / 100.0, Oklab.BLACK).toSrgb()
    }
