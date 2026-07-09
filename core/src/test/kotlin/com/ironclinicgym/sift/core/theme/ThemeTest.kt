package com.ironclinicgym.sift.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {

    private fun assertChannel(expected: Float, actual: Float, tol: Float = 0.01f) {
        assertTrue("expected ~$expected but was $actual", kotlin.math.abs(expected - actual) <= tol)
    }

    @Test fun `oklch white resolves to sRGB white`() {
        val c = parseOklch("oklch(1 0 0)").toOklab().toSrgb()
        assertChannel(1f, c.r); assertChannel(1f, c.g); assertChannel(1f, c.b)
        assertEquals(1f, c.alpha)
    }

    @Test fun `oklch black resolves to sRGB black`() {
        val c = parseOklch("oklch(0 0 0)").toOklab().toSrgb()
        assertChannel(0f, c.r); assertChannel(0f, c.g); assertChannel(0f, c.b)
    }

    @Test fun `paper surface is near-white and warm`() {
        val s = parseOklch("oklch(0.996 0.004 80)").toOklab().toSrgb()
        assertTrue("surface should be very light", s.r > 0.97f && s.g > 0.97f && s.b > 0.95f)
    }

    @Test fun `asap accent reads as red`() {
        val red = parseOklch("oklch(0.605 0.155 27)").toOklab().toSrgb()
        assertTrue("red channel dominates", red.r > red.g && red.r > red.b)
    }

    @Test fun `oklch alpha is parsed and preserved`() {
        val scrim = parseOklch("oklch(0.29 0.012 60 / 0.42)").toOklab().toSrgb()
        assertChannel(0.42f, scrim.alpha)
    }

    @Test fun `color-mix is a linear lerp in oklab`() {
        val a = Oklab(0.0, 0.0, 0.0)
        val b = Oklab(1.0, 0.0, 0.0)
        // 25% of b, 75% of a -> L = 0.25
        assertEquals(0.25, mixOklab(b, 0.25, a).l, 1e-9)
    }

    @Test fun `derive paper produces six priorities in urgency order`() {
        val d = derive(DEFAULT_THEME)
        assertEquals(6, d.priorities.size)
        assertEquals(
            listOf("asap", "today", "tomorrow", "soon", "later", "one day"),
            d.priorities.map { it.name },
        )
    }

    @Test fun `derive counts include the more overflow`() {
        val d = derive(DEFAULT_THEME)
        // asap: 2 tasks + 0 more; today: 3 + 1
        assertEquals(2, d.priorities.first { it.key == PriorityKey.ASAP }.count)
        assertEquals(4, d.priorities.first { it.key == PriorityKey.TODAY }.count)
        assertEquals(20, d.total)
    }

    @Test fun `boldFill ink fills the pill with the raw accent`() {
        val ink = derive(THEMES.getValue("ink"))
        val asap = ink.priorities.first { it.key == PriorityKey.ASAP }
        assertEquals("pill background equals the accent under boldFill", asap.accent, asap.pillBg)
    }

    @Test fun `paper pill is a wash, not the raw accent`() {
        val paper = derive(DEFAULT_THEME)
        val asap = paper.priorities.first { it.key == PriorityKey.ASAP }
        assertNotEquals(asap.accent, asap.pillBg)
    }

    @Test fun `light themes darken accent text, dark themes lighten it`() {
        val paperText = derive(DEFAULT_THEME).priorities.first { it.key == PriorityKey.ASAP }.accentText
        val slateText = derive(THEMES.getValue("slate")).priorities.first { it.key == PriorityKey.ASAP }.accentText
        val paperAccent = derive(DEFAULT_THEME).priorities.first { it.key == PriorityKey.ASAP }.accent
        val slateAccent = derive(THEMES.getValue("slate")).priorities.first { it.key == PriorityKey.ASAP }.accent
        // Paper darkens toward black -> text darker than accent.
        assertTrue(paperText.r < paperAccent.r)
        // Slate lightens toward white -> text lighter than accent.
        assertTrue(slateText.r > slateAccent.r)
    }

    @Test fun `bucket onColor matches base in light, lifts in dark`() {
        val paper = derive(DEFAULT_THEME).buckets.getValue(BucketKey.WORK)
        assertEquals("light theme onColor is the base color", paper.color, paper.onColor)
        val slate = derive(THEMES.getValue("slate")).buckets.getValue(BucketKey.WORK)
        assertNotEquals("dark theme lifts onColor toward white", slate.color, slate.onColor)
    }

    @Test fun `overdue task suppresses its time chip`() {
        val asap = derive(DEFAULT_THEME).priorities.first { it.key == PriorityKey.ASAP }
        val overdue = asap.tasks.first { it.isOverdue }
        assertFalse(overdue.showTime)
    }

    @Test fun `every theme derives without error`() {
        THEME_ORDER.forEach { key -> derive(THEMES.getValue(key)) }
    }
}
