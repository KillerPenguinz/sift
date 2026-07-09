package com.ironclinicgym.sift.core.theme

/**
 * Sift design tokens + sample data — the native port of `sift-tokens.js`.
 *
 * A "theme" is just a set of token values; swapping the [Theme] object re-skins the whole
 * UI. Where this file and the markdown brief disagree, the values transcribed here from
 * `sift-tokens.js` win — they are copied verbatim (oklch strings) for 1:1 fidelity.
 */

// ---- Buckets --------------------------------------------------------------
// Bucket = identity. Always rendered as color + icon together, never color alone.

/** Stable bucket keys. User-extensible later; these two ship by default. */
enum class BucketKey { WORK, PERSONAL }

data class Bucket(val key: BucketKey, val label: String, val icon: String, val hue: Int)

/** Insertion order matches `BUCKETS` in sift-tokens.js. */
val BUCKETS: Map<BucketKey, Bucket> = linkedMapOf(
    BucketKey.WORK to Bucket(BucketKey.WORK, "Work", "work", 255),
    BucketKey.PERSONAL to Bucket(BucketKey.PERSONAL, "Personal", "person", 8),
)

// ---- Priorities --------------------------------------------------------------
// Priority order = urgency. Accent hue runs a temperature: warm (asap) -> cool (one day).

enum class PriorityKey { ASAP, TODAY, TOMORROW, SOON, LATER, ONEDAY }

data class PriorityMeta(val key: PriorityKey, val name: String, val icon: String)

/** Order is the urgency scale — never reorder. Mirrors `PRIORITY_META`. */
val PRIORITY_META: List<PriorityMeta> = listOf(
    PriorityMeta(PriorityKey.ASAP, "asap", "bolt"),
    PriorityMeta(PriorityKey.TODAY, "today", "wb_sunny"),
    PriorityMeta(PriorityKey.TOMORROW, "tomorrow", "wb_twilight"),
    PriorityMeta(PriorityKey.SOON, "soon", "eco"),
    PriorityMeta(PriorityKey.LATER, "later", "event"),
    PriorityMeta(PriorityKey.ONEDAY, "one day", "bedtime"),
)

// ---- Sample data ----------------------------------------------------------
// Shared across surfaces so screens feel like one product. Mirrors BOARD / TODAY_FULL.

/** A raw sample task as authored in sift-tokens.js (pre-derivation). */
data class SampleTask(
    val title: String,
    val bucket: BucketKey,
    val time: String? = null,
    val overdue: Boolean = false,
    val note: String? = null,
    val done: Boolean = false,
)

/** A priority's sample content: its tasks plus a "+N more" overflow count. */
data class SamplePriority(val more: Int, val tasks: List<SampleTask>)

val BOARD: Map<PriorityKey, SamplePriority> = linkedMapOf(
    PriorityKey.ASAP to SamplePriority(
        more = 0,
        tasks = listOf(
            SampleTask("Reply to the lease renewal email", BucketKey.PERSONAL),
            SampleTask("Send the Q3 deck to Priya", BucketKey.WORK, overdue = true),
        ),
    ),
    PriorityKey.TODAY to SamplePriority(
        more = 1,
        tasks = listOf(
            SampleTask("Standup at 10:00", BucketKey.WORK, time = "10:00"),
            SampleTask("Pick up the prescription", BucketKey.PERSONAL),
            SampleTask("Book the dentist", BucketKey.PERSONAL),
        ),
    ),
    PriorityKey.TOMORROW to SamplePriority(
        more = 0,
        tasks = listOf(
            SampleTask("Draft the onboarding copy", BucketKey.WORK),
            SampleTask("Water the plants", BucketKey.PERSONAL),
        ),
    ),
    PriorityKey.SOON to SamplePriority(
        more = 2,
        tasks = listOf(
            SampleTask("Plan the weekend trip", BucketKey.PERSONAL),
            SampleTask("Review the open pull requests", BucketKey.WORK),
            SampleTask("Renew the gym membership", BucketKey.PERSONAL),
        ),
    ),
    PriorityKey.LATER to SamplePriority(
        more = 0,
        tasks = listOf(
            SampleTask("Read the design systems book", BucketKey.WORK),
            SampleTask("Refactor the export flow", BucketKey.WORK),
        ),
    ),
    PriorityKey.ONEDAY to SamplePriority(
        more = 3,
        tasks = listOf(
            SampleTask("Learn to make fresh pasta", BucketKey.PERSONAL),
            SampleTask("Sketch the app redesign", BucketKey.WORK),
        ),
    ),
)

/** Fuller task list used by the focused single-priority view (surface C). */
val TODAY_FULL: List<SampleTask> = listOf(
    SampleTask("Standup at 10:00", BucketKey.WORK, time = "10:00", note = "Share the export blocker"),
    SampleTask("Send the Q3 deck to Priya", BucketKey.WORK, overdue = true, note = "Was due yesterday"),
    SampleTask("Pick up the prescription", BucketKey.PERSONAL, time = "14:30"),
    SampleTask("Book the dentist", BucketKey.PERSONAL),
    SampleTask("Call the landlord back", BucketKey.PERSONAL),
    SampleTask("Review the open pull requests", BucketKey.WORK, note = "3 waiting"),
    SampleTask("Submit the expense report", BucketKey.WORK, done = true),
    SampleTask("Reschedule the gym session", BucketKey.PERSONAL, done = true),
)

// ---- Themes ---------------------------------------------------------------
// Each theme lists every token explicitly. accent.* = per-priority urgency colors.
// bucket.* = per-bucket colors. headerMix / pillMix / bucketBgMix / accentTextMix tune how
// much accent tints a surface. isDark flips accent-text computation (lighten vs darken).

/**
 * A complete token set. Colors are stored as oklch strings copied verbatim from
 * sift-tokens.js; [derive] parses + composites them into resolved [SiftColor]s.
 */
data class Theme(
    val key: String,
    val label: String,
    val sub: String,
    val kind: String,
    val isDark: Boolean,
    val bg: String,
    val surface: String,
    val surfaceRaised: String,
    val border: String,
    val borderStrong: String,
    val textPrimary: String,
    val textSecondary: String,
    val textTertiary: String,
    val headerMix: Int,
    val pillMix: Int,
    val bucketBgMix: Int,
    val accentTextMix: Int,
    val scrim: String,
    val boldFill: Boolean = false,
    val accent: Map<PriorityKey, String>,
    val bucket: Map<BucketKey, String>,
)

val THEMES: Map<String, Theme> = linkedMapOf(
    "paper_dark" to Theme(
        key = "paper_dark", label = "Paper Dark", sub = "warm dark · the default after dark", kind = "dark", isDark = true,
        bg = "oklch(0.22 0.008 74)", surface = "oklch(0.27 0.01 74)", surfaceRaised = "oklch(0.32 0.012 74)",
        border = "oklch(0.38 0.012 74)", borderStrong = "oklch(0.48 0.016 74)",
        textPrimary = "oklch(0.95 0.006 74)", textSecondary = "oklch(0.74 0.01 74)", textTertiary = "oklch(0.6 0.01 74)",
        headerMix = 16, pillMix = 26, bucketBgMix = 24, accentTextMix = 28,
        scrim = "oklch(0.14 0.008 74 / 0.58)",
        accent = mapOf(
            PriorityKey.ASAP to "oklch(0.7 0.16 27)", PriorityKey.TODAY to "oklch(0.78 0.14 65)",
            PriorityKey.TOMORROW to "oklch(0.82 0.12 95)", PriorityKey.SOON to "oklch(0.74 0.12 152)",
            PriorityKey.LATER to "oklch(0.7 0.1 232)", PriorityKey.ONEDAY to "oklch(0.68 0.12 292)",
        ),
        bucket = mapOf(
            BucketKey.WORK to "oklch(0.68 0.13 255)", BucketKey.PERSONAL to "oklch(0.7 0.16 8)",
        ),
    ),
    "paper" to Theme(
        key = "paper", label = "Paper", sub = "default · calm warm light", kind = "light", isDark = false,
        bg = "oklch(0.967 0.006 74)", surface = "oklch(0.996 0.004 80)", surfaceRaised = "oklch(1 0 0)",
        border = "oklch(0.905 0.007 74)", borderStrong = "oklch(0.85 0.009 74)",
        textPrimary = "oklch(0.29 0.012 60)", textSecondary = "oklch(0.52 0.012 60)", textTertiary = "oklch(0.66 0.01 60)",
        headerMix = 8, pillMix = 16, bucketBgMix = 15, accentTextMix = 72,
        scrim = "oklch(0.29 0.012 60 / 0.42)",
        accent = mapOf(
            PriorityKey.ASAP to "oklch(0.605 0.155 27)", PriorityKey.TODAY to "oklch(0.7 0.14 65)",
            PriorityKey.TOMORROW to "oklch(0.72 0.12 95)", PriorityKey.SOON to "oklch(0.64 0.11 152)",
            PriorityKey.LATER to "oklch(0.6 0.09 232)", PriorityKey.ONEDAY to "oklch(0.57 0.1 292)",
        ),
        bucket = mapOf(
            BucketKey.WORK to "oklch(0.55 0.13 255)", BucketKey.PERSONAL to "oklch(0.62 0.15 8)",
        ),
    ),
    "slate" to Theme(
        key = "slate", label = "Slate", sub = "calm dark · cool neutral", kind = "dark", isDark = true,
        bg = "oklch(0.215 0.012 256)", surface = "oklch(0.262 0.014 256)", surfaceRaised = "oklch(0.305 0.016 256)",
        border = "oklch(0.36 0.016 256)", borderStrong = "oklch(0.45 0.02 256)",
        textPrimary = "oklch(0.96 0.004 256)", textSecondary = "oklch(0.75 0.012 256)", textTertiary = "oklch(0.6 0.014 256)",
        headerMix = 18, pillMix = 28, bucketBgMix = 26, accentTextMix = 30,
        scrim = "oklch(0.12 0.01 256 / 0.6)",
        accent = mapOf(
            PriorityKey.ASAP to "oklch(0.7 0.16 25)", PriorityKey.TODAY to "oklch(0.8 0.14 70)",
            PriorityKey.TOMORROW to "oklch(0.84 0.13 95)", PriorityKey.SOON to "oklch(0.76 0.13 158)",
            PriorityKey.LATER to "oklch(0.72 0.11 238)", PriorityKey.ONEDAY to "oklch(0.72 0.13 298)",
        ),
        bucket = mapOf(
            BucketKey.WORK to "oklch(0.7 0.13 252)", BucketKey.PERSONAL to "oklch(0.72 0.16 12)",
        ),
    ),
    "ink" to Theme(
        key = "ink", label = "Ink", sub = "bold high contrast · punchy", kind = "bold", isDark = true,
        bg = "oklch(0.16 0.014 285)", surface = "oklch(0.205 0.018 285)", surfaceRaised = "oklch(0.25 0.02 285)",
        border = "oklch(0.33 0.024 285)", borderStrong = "oklch(0.46 0.03 285)",
        textPrimary = "oklch(0.98 0.004 285)", textSecondary = "oklch(0.78 0.014 285)", textTertiary = "oklch(0.62 0.018 285)",
        headerMix = 30, pillMix = 100, bucketBgMix = 34, accentTextMix = 22, boldFill = true,
        scrim = "oklch(0.08 0.012 285 / 0.66)",
        accent = mapOf(
            PriorityKey.ASAP to "oklch(0.68 0.23 25)", PriorityKey.TODAY to "oklch(0.8 0.19 65)",
            PriorityKey.TOMORROW to "oklch(0.86 0.18 100)", PriorityKey.SOON to "oklch(0.78 0.2 150)",
            PriorityKey.LATER to "oklch(0.68 0.2 250)", PriorityKey.ONEDAY to "oklch(0.66 0.24 305)",
        ),
        bucket = mapOf(
            BucketKey.WORK to "oklch(0.68 0.19 255)", BucketKey.PERSONAL to "oklch(0.7 0.22 12)",
        ),
    ),
    "linen" to Theme(
        key = "linen", label = "Linen", sub = "warm light · cozy", kind = "warm", isDark = false,
        bg = "oklch(0.945 0.02 70)", surface = "oklch(0.985 0.013 72)", surfaceRaised = "oklch(1 0.006 72)",
        border = "oklch(0.88 0.018 66)", borderStrong = "oklch(0.81 0.024 62)",
        textPrimary = "oklch(0.32 0.026 48)", textSecondary = "oklch(0.5 0.028 50)", textTertiary = "oklch(0.64 0.026 54)",
        headerMix = 11, pillMix = 20, bucketBgMix = 18, accentTextMix = 66,
        scrim = "oklch(0.32 0.026 48 / 0.4)",
        accent = mapOf(
            PriorityKey.ASAP to "oklch(0.58 0.16 30)", PriorityKey.TODAY to "oklch(0.66 0.14 58)",
            PriorityKey.TOMORROW to "oklch(0.68 0.12 85)", PriorityKey.SOON to "oklch(0.6 0.11 140)",
            PriorityKey.LATER to "oklch(0.58 0.1 222)", PriorityKey.ONEDAY to "oklch(0.56 0.12 318)",
        ),
        bucket = mapOf(
            BucketKey.WORK to "oklch(0.53 0.13 250)", BucketKey.PERSONAL to "oklch(0.58 0.16 18)",
        ),
    ),
    "cyber" to Theme(
        key = "cyber", label = "Cyber", sub = "cool dark · electric", kind = "cool", isDark = true,
        bg = "oklch(0.19 0.03 220)", surface = "oklch(0.235 0.035 220)", surfaceRaised = "oklch(0.28 0.04 218)",
        border = "oklch(0.36 0.045 216)", borderStrong = "oklch(0.5 0.07 200)",
        textPrimary = "oklch(0.95 0.02 200)", textSecondary = "oklch(0.74 0.04 205)", textTertiary = "oklch(0.6 0.05 210)",
        headerMix = 22, pillMix = 34, bucketBgMix = 30, accentTextMix = 26,
        scrim = "oklch(0.1 0.03 220 / 0.64)",
        accent = mapOf(
            PriorityKey.ASAP to "oklch(0.72 0.2 8)", PriorityKey.TODAY to "oklch(0.82 0.16 75)",
            PriorityKey.TOMORROW to "oklch(0.88 0.18 175)", PriorityKey.SOON to "oklch(0.82 0.2 165)",
            PriorityKey.LATER to "oklch(0.75 0.16 232)", PriorityKey.ONEDAY to "oklch(0.74 0.18 290)",
        ),
        bucket = mapOf(
            BucketKey.WORK to "oklch(0.78 0.15 230)", BucketKey.PERSONAL to "oklch(0.74 0.19 10)",
        ),
    ),
)

/** Theme picker order. Mirrors `THEME_ORDER`. */
val THEME_ORDER: List<String> = listOf("paper", "slate", "ink", "linen", "cyber")

/** The default theme — Paper. */
val DEFAULT_THEME: Theme = THEMES.getValue("paper")
