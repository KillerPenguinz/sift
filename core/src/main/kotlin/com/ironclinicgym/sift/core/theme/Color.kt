package com.ironclinicgym.sift.core.theme

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Color foundation for Sift.
 *
 * The web source of truth (`sift-tokens.js`) authors every color in **oklch** and
 * composites tints with **`color-mix(in oklab, …)`**, letting the browser resolve them.
 * Native has no `color-mix`, so we resolve it ourselves: parse oklch, work in the Oklab
 * (`a`/`b`) space for mixing, then convert to sRGB. This is the faithful native port of
 * the CSS pipeline — same math the browser runs, done eagerly in [derive].
 *
 * Pure Kotlin, no platform types. The Compose layer maps [SiftColor] → `androidx…Color`.
 */

/** A color in cylindrical Oklab: lightness, chroma, hue (degrees), alpha (0..1). */
data class Oklch(val l: Double, val c: Double, val h: Double, val alpha: Double = 1.0) {
    fun toOklab(): Oklab {
        val hr = Math.toRadians(h)
        return Oklab(l, c * cos(hr), c * sin(hr), alpha)
    }
}

/**
 * A color in rectangular Oklab. This is the space we interpolate in — matching
 * CSS `color-mix(in oklab, …)`, which lerps the L/a/b/alpha components linearly.
 */
data class Oklab(val l: Double, val a: Double, val b: Double, val alpha: Double = 1.0) {

    /** Convert to gamma-encoded sRGB, clamped to the display gamut. */
    fun toSrgb(): SiftColor {
        // Oklab -> LMS (Björn Ottosson's matrices)
        val l_ = l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = l - 0.0894841775 * a - 1.2914855480 * b

        val lc = l_ * l_ * l_
        val mc = m_ * m_ * m_
        val sc = s_ * s_ * s_

        // LMS -> linear sRGB
        val rLin = 4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc
        val gLin = -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc
        val bLin = -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc

        return SiftColor(
            r = gammaEncode(rLin),
            g = gammaEncode(gLin),
            b = gammaEncode(bLin),
            alpha = alpha.coerceIn(0.0, 1.0).toFloat(),
        )
    }

    private fun gammaEncode(linear: Double): Float {
        val x = linear.coerceIn(0.0, 1.0)
        val encoded = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1.0 / 2.4) - 0.055
        return encoded.coerceIn(0.0, 1.0).toFloat()
    }

    companion object {
        /** sRGB white / black in Oklab — the mix anchors used by [derive]. */
        val WHITE = Oklab(1.0, 0.0, 0.0, 1.0)
        val BLACK = Oklab(0.0, 0.0, 0.0, 1.0)
    }
}

/**
 * A resolved, platform-neutral color: straight-alpha sRGB components in 0..1.
 * The single hand-off type between core and any UI layer.
 */
data class SiftColor(val r: Float, val g: Float, val b: Float, val alpha: Float = 1f) {
    /** 0xAARRGGBB, handy for tests/logging. */
    fun toArgb(): Int {
        fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (ch(alpha) shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}

/**
 * `color-mix(in oklab, [c] [p], [base])`.
 *
 * Mirrors the `mix(c, p, base)` helper in `sift-tokens.js`: [p] is the weight of [c]
 * (0..1), and [base] takes the remainder. Interpolation is linear in Oklab L/a/b/alpha.
 */
fun mixOklab(c: Oklab, p: Double, base: Oklab): Oklab {
    val w = p.coerceIn(0.0, 1.0)
    val iw = 1.0 - w
    return Oklab(
        l = c.l * w + base.l * iw,
        a = c.a * w + base.a * iw,
        b = c.b * w + base.b * iw,
        alpha = c.alpha * w + base.alpha * iw,
    )
}

/**
 * Parse a CSS oklch literal: `oklch(L C H)` or `oklch(L C H / A)`.
 * Components are unitless numbers (alpha may be a 0..1 number). This lets the [THEMES]
 * table hold the exact strings from `sift-tokens.js` so it diffs 1:1 against the bucket.
 */
fun parseOklch(spec: String): Oklch {
    val inner = spec.trim()
        .removePrefix("oklch(")
        .removeSuffix(")")
        .trim()
    val (coords, alphaPart) = if ('/' in inner) {
        val parts = inner.split('/')
        parts[0].trim() to parts[1].trim()
    } else {
        inner to null
    }
    val nums = coords.split(Regex("\\s+")).filter { it.isNotEmpty() }
    require(nums.size == 3) { "Malformed oklch literal: $spec" }
    return Oklch(
        l = nums[0].toDouble(),
        c = nums[1].toDouble(),
        h = nums[2].toDouble(),
        alpha = alphaPart?.toDouble() ?: 1.0,
    )
}
