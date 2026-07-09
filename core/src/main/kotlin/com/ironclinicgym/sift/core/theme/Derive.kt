package com.ironclinicgym.sift.core.theme

/**
 * The derivation layer — the native port of `derive(theme)` in sift-tokens.js.
 *
 * Components never reference a raw accent color. They read values **derived** from
 * `theme + base color`, so one set of bases yields coherent output across all five
 * themes. [derive] resolves every oklch base and `color-mix(in oklab, …)` into a
 * concrete [SiftColor] up front; the UI layer just paints what it's handed.
 */

/** Neutral surface + text tokens, resolved to colors. */
data class DerivedNeutrals(
    val bg: SiftColor,
    val surface: SiftColor,
    val surfaceRaised: SiftColor,
    val border: SiftColor,
    val borderStrong: SiftColor,
    val textPrimary: SiftColor,
    val textSecondary: SiftColor,
    val textTertiary: SiftColor,
    val scrim: SiftColor,
)

/** A bucket resolved for painting: identity color + its chip background + on-color. */
data class DerivedBucket(
    val key: BucketKey,
    val label: String,
    val icon: String,
    val hue: Int,
    val color: SiftColor,
    /** Bucket chip background — `mix(color, bucketBgMix%, surface)`. */
    val bg: SiftColor,
    /** Text/icon color on a bucket chip — lightened toward white in dark themes. */
    val onColor: SiftColor,
)

/** A task resolved for a board row. Field-for-field with the JS `tasks.map(...)` output. */
data class DerivedTask(
    val title: String,
    val bucketLabel: String,
    val bucketIcon: String,
    /** Bucket on-color (icon/text). */
    val bucketColor: SiftColor,
    val bucketBg: SiftColor,
    val hasTime: Boolean,
    val timeText: String,
    val isOverdue: Boolean,
    /** Show the time chip only when there is a time and the task isn't overdue. */
    val showTime: Boolean,
    /** Pre-composed "Bucket  ·  time" metadata line for the expanded row. */
    val subMeta: String,
)

/** A priority resolved for the board: urgency accent + its washes, pill, and tasks. */
data class DerivedPriority(
    val key: PriorityKey,
    val name: String,
    val icon: String,
    val accent: SiftColor,
    /** Readable text/icon color sitting on [headerBg]. */
    val accentText: SiftColor,
    /** Faint accent wash behind the priority header. */
    val headerBg: SiftColor,
    /** Count-pill background (solid accent under `boldFill`, else a wash). */
    val pillBg: SiftColor,
    /** Count-pill text. */
    val pillText: SiftColor,
    val count: Int,
    val hasMore: Boolean,
    val moreLabel: String,
    val tasks: List<DerivedTask>,
)

/** Everything a surface needs for one theme. The output of [derive]. */
data class DerivedTheme(
    val theme: Theme,
    val neutrals: DerivedNeutrals,
    val buckets: Map<BucketKey, DerivedBucket>,
    val priorities: List<DerivedPriority>,
    val total: Int,
    val overdueBg: SiftColor,
    val overdueText: SiftColor,
    val successAccent: SiftColor,
)

/** Resolve a plain oklch token string straight to a [SiftColor]. */
private fun String.toColor(): SiftColor = parseOklch(this).toOklab().toSrgb()

/**
 * Compute every derived value for [theme] over the sample [BOARD].
 *
 * This is intentionally a pure function of [theme] (+ the static sample data) so it can be
 * memoized cheaply by the UI layer and called identically on any platform.
 */
fun derive(theme: Theme): DerivedTheme {
    val surface = theme.surface.toColor() // mix base, reused everywhere
    val surfaceLab = parseOklch(theme.surface).toOklab()

    fun mix(c: String, percent: Int, base: Oklab): SiftColor =
        mixOklab(parseOklch(c).toOklab(), percent / 100.0, base).toSrgb()

    // accentText(c): isDark ? lighten toward white : darken toward black
    fun accentText(accentSpec: String): SiftColor {
        val accent = parseOklch(accentSpec).toOklab()
        return if (theme.isDark) {
            mixOklab(accent, (100 - theme.accentTextMix) / 100.0, Oklab.WHITE).toSrgb()
        } else {
            mixOklab(accent, theme.accentTextMix / 100.0, Oklab.BLACK).toSrgb()
        }
    }

    val neutrals = DerivedNeutrals(
        bg = theme.bg.toColor(),
        surface = surface,
        surfaceRaised = theme.surfaceRaised.toColor(),
        border = theme.border.toColor(),
        borderStrong = theme.borderStrong.toColor(),
        textPrimary = theme.textPrimary.toColor(),
        textSecondary = theme.textSecondary.toColor(),
        textTertiary = theme.textTertiary.toColor(),
        scrim = theme.scrim.toColor(),
    )

    val buckets: Map<BucketKey, DerivedBucket> = BUCKETS.entries.associate { (key, meta) ->
        val colorSpec = theme.bucket.getValue(key)
        val colorLab = parseOklch(colorSpec).toOklab()
        key to DerivedBucket(
            key = key,
            label = meta.label,
            icon = meta.icon,
            hue = meta.hue,
            color = colorLab.toSrgb(),
            bg = mix(colorSpec, theme.bucketBgMix, surfaceLab),
            onColor = if (theme.isDark) mixOklab(colorLab, 0.12, Oklab.WHITE).toSrgb() else colorLab.toSrgb(),
        )
    }

    val priorities = PRIORITY_META.map { meta ->
        val accentSpec = theme.accent.getValue(meta.key)
        val accentLab = parseOklch(accentSpec).toOklab()
        val data = BOARD.getValue(meta.key)
        val aText = accentText(accentSpec)
        DerivedPriority(
            key = meta.key,
            name = meta.name,
            icon = meta.icon,
            accent = accentLab.toSrgb(),
            accentText = aText,
            headerBg = mix(accentSpec, theme.headerMix, surfaceLab),
            pillBg = if (theme.boldFill) accentLab.toSrgb() else mix(accentSpec, theme.pillMix, surfaceLab),
            pillText = if (theme.boldFill) {
                mixOklab(accentLab, 0.92, if (theme.isDark) Oklab.BLACK else Oklab.WHITE).toSrgb()
            } else {
                aText
            },
            count = data.tasks.size + data.more,
            hasMore = data.more > 0,
            moreLabel = "+${data.more} more",
            tasks = data.tasks.map { t ->
                val s = buckets.getValue(t.bucket)
                val hasTime = t.time != null
                val isOverdue = t.overdue
                DerivedTask(
                    title = t.title,
                    bucketLabel = s.label,
                    bucketIcon = s.icon,
                    bucketColor = s.onColor,
                    bucketBg = s.bg,
                    hasTime = hasTime,
                    timeText = t.time ?: "",
                    isOverdue = isOverdue,
                    showTime = hasTime && !isOverdue,
                    subMeta = s.label + if (hasTime) "  ·  ${t.time}" else "",
                )
            },
        )
    }

    val total = PRIORITY_META.sumOf { BOARD.getValue(it.key).let { b -> b.tasks.size + b.more } }
    val asapSpec = theme.accent.getValue(PriorityKey.ASAP)
    val asapLab = parseOklch(asapSpec).toOklab()

    val successAccent = if (theme.isDark) {
        "oklch(0.76 0.16 155)".toColor()
    } else {
        "oklch(0.64 0.16 155)".toColor()
    }

    return DerivedTheme(
        theme = theme,
        neutrals = neutrals,
        buckets = buckets,
        priorities = priorities,
        total = total,
        overdueBg = mix(asapSpec, if (theme.isDark) 24 else 13, surfaceLab),
        overdueText = if (theme.isDark) {
            mixOklab(asapLab, 0.20, Oklab.WHITE).toSrgb()
        } else {
            mixOklab(asapLab, 0.74, Oklab.BLACK).toSrgb()
        },
        successAccent = successAccent,
    )
}

/** Convenience: derive by theme key, defaulting to Paper for an unknown key. */
fun deriveTheme(key: String): DerivedTheme = derive(THEMES[key] ?: DEFAULT_THEME)

/** Derive via a family key + dark/light mode. Falls back gracefully per [ThemeFamily.resolveKey]. */
fun deriveTheme(familyKey: String, isDark: Boolean): DerivedTheme {
    val family = THEME_FAMILIES.firstOrNull { it.key == familyKey } ?: DEFAULT_FAMILY
    return deriveTheme(family.resolveKey(isDark))
}
